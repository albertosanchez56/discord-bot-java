package com.main.commands;

import java.io.InputStream;
import java.net.HttpURLConnection;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.main.json.JSONReader;
import com.main.service.MetasrcService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Comando slash /build <champion> [mode]
 */
public class BuildCommand extends ListenerAdapter implements Command {

    public static CommandData getCommandData() {
        return Commands.slash("build", "Muestra runas y objetos de un campeón usando METAsrc")
                .addOption(OptionType.STRING, "champion", "Clave del campeón (ej. zed)", true)
                .addOption(OptionType.STRING, "mode", "Modo (classic, aram, urf)", false);
    }

    @Override public String getName()        { return "build"; }
    @Override public String getDescription() { return "Muestra build de un campeón usando METAsrc"; }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("build")) return;

        String champ = event.getOption("champion").getAsString().trim().toLowerCase();
        String mode  = event.getOption("mode") != null
                ? event.getOption("mode").getAsString().trim().toLowerCase()
                : "classic";

        event.deferReply().queue();
        try {
            MetasrcService.Build build = new MetasrcService().fetchBuild(champ, mode);

            // 1) Crear imágenes en rejilla (runes más grandes para que luzcan)
            File runesFile = null;
            if (!build.runeUrls.isEmpty()) {
                // iconos grandes y en 2 filas aprox (5 por fila)
                runesFile = createGridImage(build.runeUrls, "runes.png",
                        /*iconSize*/56, /*gap*/6, /*perRow*/5);
            }

            File itemsFile = null;
            if (!build.itemUrls.isEmpty()) {
                // iconos típicos de item 42px, 6 por fila
                itemsFile = createGridImage(build.itemUrls, "items.png",
                        /*iconSize*/42, /*gap*/6, /*perRow*/6);
            }

            // 2) Preparar uploads
            List<FileUpload> uploads = new ArrayList<>();
            if (runesFile != null) uploads.add(FileUpload.fromData(runesFile, "runes.png"));
            if (itemsFile != null) uploads.add(FileUpload.fromData(itemsFile, "items.png"));

            // 3) Embeds separados para que ambas imágenes se vean grandes
            List<net.dv8tion.jda.api.entities.MessageEmbed> embeds = new ArrayList<>();

            if (runesFile != null) {
                EmbedBuilder ebRunes = new EmbedBuilder()
                        .setTitle("Build de " + champ + " (" + mode.toUpperCase() + ") · 🔱 Runas")
                        .setColor(new Color(0x00ADEF))
                        .setImage("attachment://runes.png"); // imagen principal, grande
                embeds.add(ebRunes.build());
            }

            if (itemsFile != null) {
                EmbedBuilder ebItems = new EmbedBuilder()
                        .setTitle("Build de " + champ + " (" + mode.toUpperCase() + ") · 🛡️ Objetos")
                        .setColor(new Color(0x00ADEF))
                        .setImage("attachment://items.png"); // imagen principal, grande
                embeds.add(ebItems.build());
            }

            if (uploads.isEmpty()) {
                event.getHook().sendMessage("❌ No pude encontrar runas ni objetos para ese campeón/modo.").queue();
                return;
            }

            event.getHook()
                    .sendFiles(uploads)
                    .addEmbeds(embeds)
                    .queue();

        } catch (Exception e) {
            event.getHook()
                    .sendMessage("❌ Error al obtener la build: " + e.getMessage())
                    .queue();
        }
    }

    /**
     * Descarga y compone una rejilla de imágenes.
     *
     * @param urls    URLs de iconos
     * @param file    nombre del fichero de salida
     * @param size    tamaño de icono (cuadrado) en px
     * @param gap     separación entre iconos en px
     * @param perRow  cuántos iconos por fila
     */
    private File createGridImage(List<String> urls, String file, int size, int gap, int perRow) throws Exception {
        if (urls == null || urls.isEmpty()) return null;

        int n = urls.size();
        int rows = (int) Math.ceil(n / (double) perRow);
        int cols = Math.min(n, perRow);

        int width  = cols * size + (cols - 1) * gap;
        int height = rows * size + (rows - 1) * gap;

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (int i = 0; i < n; i++) {
            int r = i / perRow;
            int c = i % perRow;
            int x = c * (size + gap);
            int y = r * (size + gap);

            BufferedImage icon = safeDownload(urls.get(i));
            if (icon != null) {
                g.drawImage(icon, x, y, size, size, null);
            }
        }
        g.dispose();

        File out = new File(file);
        ImageIO.write(canvas, "png", out);
        return out;
    }

    /** Descarga una imagen con User-Agent y timeouts. Devuelve null si falla. */
    private BufferedImage safeDownload(String urlStr) {
        try {
            URL url = new URL(urlStr.startsWith("//") ? "https:" + urlStr : urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}