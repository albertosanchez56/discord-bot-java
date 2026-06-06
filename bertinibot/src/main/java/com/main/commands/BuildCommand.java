package com.main.commands;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.core.PrefixCommand;
import com.main.core.SlashCommand;
import com.main.service.MetasrcService;
import com.main.util.AsyncHttp;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * /build &lt;champion&gt; [mode] - fetches a LoL build from METAsrc and posts
 * runes + items as composed PNGs.
 *
 * V2 improvements over V1: parallel icon downloads (virtual threads) via the
 * shared {@link AsyncHttp} client, PNGs written to temp files (no cwd
 * pollution) and embeds composed in memory.
 */
public final class BuildCommand implements SlashCommand, PrefixCommand {

    private static final Logger log = LoggerFactory.getLogger(BuildCommand.class);
    private final MetasrcService metasrc = new MetasrcService();

    @Override
    public SlashCommandData data() {
        return Commands.slash("build", "Muestra runas y objetos de un campeon usando METAsrc")
                .addOption(OptionType.STRING, "champion", "Clave del campeon (ej. zed)", true)
                .addOption(OptionType.STRING, "mode", "Modo (classic, aram, urf)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String champ = event.getOption("champion").getAsString().trim().toLowerCase();
        String mode = event.getOption("mode") != null
                ? event.getOption("mode").getAsString().trim().toLowerCase()
                : "classic";

        event.deferReply().queue();
        try {
            BuildResult res = renderBuild(champ, mode);
            if (res.uploads.isEmpty()) {
                event.getHook().sendMessage("No pude encontrar runas ni objetos para ese campeon/modo.").queue();
                return;
            }
            event.getHook().sendFiles(res.uploads).addEmbeds(res.embeds).queue(
                    ok -> cleanup(res.files()),
                    err -> cleanup(res.files()));
        } catch (Exception e) {
            log.warn("/build failed for champ={} mode={}: {}", champ, mode, e.getMessage());
            event.getHook().sendMessage("Error al obtener la build: " + e.getMessage()).queue();
        }
    }

    @Override
    public String name() { return "build"; }
    @Override
    public String usage() { return "!build <champion> [mode]"; }
    @Override
    public String description() { return "Runas y objetos de un campeon de LoL (METAsrc)."; }

    @Override
    public void execute(MessageReceivedEvent event, String args) {
        if (args.isBlank()) {
            event.getChannel().sendMessage("Uso: `!build <champion> [classic|aram|urf]`.").queue();
            return;
        }
        String[] parts = args.trim().split("\\s+", 2);
        String champ = parts[0].toLowerCase();
        String mode = parts.length > 1 ? parts[1].toLowerCase() : "classic";

        MessageChannel ch = event.getChannel();
        ch.sendMessage("Buscando build de **" + champ + "** (" + mode + ")...").queue(notice ->
                CompletableFuture.runAsync(() -> {
                    try {
                        BuildResult res = renderBuild(champ, mode);
                        if (res.uploads.isEmpty()) {
                            notice.editMessage("No pude encontrar runas ni objetos para ese campeon/modo.").queue();
                            return;
                        }
                        notice.delete().queue();
                        ch.sendFiles(res.uploads).addEmbeds(res.embeds).queue(
                                ok -> cleanup(res.files()),
                                err -> cleanup(res.files()));
                    } catch (Exception e) {
                        log.warn("!build failed for champ={} mode={}: {}", champ, mode, e.getMessage());
                        notice.editMessage("Error al obtener la build: " + e.getMessage()).queue();
                    }
                }));
    }

    private BuildResult renderBuild(String champ, String mode) throws Exception {
        MetasrcService.Build build = metasrc.fetchBuild(champ, mode);

        File runesFile = null;
        if (!build.runeUrls().isEmpty()) {
            runesFile = createGridImage(build.runeUrls(), "runes", 56, 6, 5);
        }
        File itemsFile = null;
        if (!build.itemUrls().isEmpty()) {
            itemsFile = createGridImage(build.itemUrls(), "items", 42, 6, 6);
        }

        List<FileUpload> uploads = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();

        if (runesFile != null) {
            uploads.add(FileUpload.fromData(runesFile, "runes.png"));
            embeds.add(new EmbedBuilder()
                    .setTitle("Build de " + champ + " (" + mode.toUpperCase() + ") - Runas")
                    .setColor(new Color(0x00ADEF))
                    .setImage("attachment://runes.png")
                    .build());
        }
        if (itemsFile != null) {
            uploads.add(FileUpload.fromData(itemsFile, "items.png"));
            embeds.add(new EmbedBuilder()
                    .setTitle("Build de " + champ + " (" + mode.toUpperCase() + ") - Objetos")
                    .setColor(new Color(0x00ADEF))
                    .setImage("attachment://items.png")
                    .build());
        }
        return new BuildResult(uploads, embeds, runesFile, itemsFile);
    }

    private record BuildResult(List<FileUpload> uploads, List<MessageEmbed> embeds,
                               File runesFile, File itemsFile) {
        File[] files() {
            return new File[] { runesFile, itemsFile };
        }
    }

    private File createGridImage(List<String> urls, String namePrefix,
                                 int size, int gap, int perRow) throws Exception {
        int n = urls.size();
        int rows = (int) Math.ceil(n / (double) perRow);
        int cols = Math.min(n, perRow);

        int width = cols * size + (cols - 1) * gap;
        int height = rows * size + (rows - 1) * gap;

        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>(n);
        for (String url : urls) futures.add(downloadAsync(url));

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        try {
            for (int i = 0; i < n; i++) {
                BufferedImage icon = futures.get(i).join();
                if (icon == null) continue;
                int r = i / perRow;
                int c = i % perRow;
                g.drawImage(icon, c * (size + gap), r * (size + gap), size, size, null);
            }
        } finally {
            g.dispose();
        }

        File out = Files.createTempFile("bertinibot-" + namePrefix + "-", ".png").toFile();
        out.deleteOnExit();
        ImageIO.write(canvas, "png", out);
        return out;
    }

    private CompletableFuture<BufferedImage> downloadAsync(String rawUrl) {
        String url = rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (BertiniBot)")
                .GET()
                .build();
        return AsyncHttp.client()
                .sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) return null;
                    try {
                        return ImageIO.read(new ByteArrayInputStream(resp.body()));
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .exceptionally(ex -> null);
    }

    private void cleanup(File... files) {
        for (File f : files) {
            if (f != null) {
                try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) { }
            }
        }
    }
}
