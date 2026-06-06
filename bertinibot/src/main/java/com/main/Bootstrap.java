package com.main;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.audio.AudioService;
import com.main.commands.BuildCommand;
import com.main.commands.ClearCommand;
import com.main.commands.CoinFlipCommand;
import com.main.commands.HelpCommand;
import com.main.commands.InfoCommand;
import com.main.commands.LoopCommand;
import com.main.commands.PanelCommand;
import com.main.commands.PingCommand;
import com.main.commands.PlayCommand;
import com.main.commands.QueueCommand;
import com.main.commands.SeekCommand;
import com.main.commands.ShuffleCommand;
import com.main.commands.SkipCommand;
import com.main.commands.VolumeCommand;
import com.main.config.Config;
import com.main.core.CommandRegistry;
import com.main.core.ComponentRouter;
import com.main.core.PrefixCommand;
import com.main.core.PrefixCommandRouter;
import com.main.core.SlashCommand;
import com.main.filters.TrackFilter;
import com.main.listeners.VoiceChannelListener;
import com.main.panel.PanelButtonHandler;
import com.main.panel.PanelButtons;
import com.main.panel.PanelService;

import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

/**
 * Entry point for BertiniBot V2.
 *
 * Wires the configuration, the audio service, the command registry, the panel
 * service and the JDA client. Requires the privileged
 * {@link GatewayIntent#MESSAGE_CONTENT} intent (alongside
 * {@link GatewayIntent#GUILD_MESSAGES} and {@link GatewayIntent#GUILD_VOICE_STATES})
 * to power the {@code !}-prefixed text commands. The intent must also be
 * enabled in the Discord Developer Portal for the bot application.
 */
public final class Bootstrap {

    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private Bootstrap() {}

    public static void main(String[] args) throws Exception {
        applyWindowsTrustStore();
        String token = Config.discordToken();

        AudioService audio = new AudioService();
        TrackFilter filter = new TrackFilter(Config.blockedTitlesCsv());
        audio.setTrackBlockedFilter(filter::isBlocked);
        PanelService panel = new PanelService(audio);
        audio.setOnGuildUpdate(panel::update);

        CommandRegistry registry = new CommandRegistry();
        ComponentRouter router = new ComponentRouter();
        PrefixCommandRouter prefixRouter = new PrefixCommandRouter();

        PanelButtonHandler buttonHandler = new PanelButtonHandler(audio, panel);
        router.onButton(PanelButtons.PREFIX, buttonHandler::handle);

        List<SlashCommand> commands = List.of(
                new PlayCommand(audio),
                new SkipCommand(audio),
                new QueueCommand(audio),
                new ClearCommand(audio),
                new LoopCommand(audio),
                new ShuffleCommand(audio),
                new SeekCommand(audio),
                new VolumeCommand(audio),
                new PanelCommand(audio),
                new CoinFlipCommand(),
                new BuildCommand(),
                new PingCommand(),
                new InfoCommand(),
                new HelpCommand());
        registry.registerAll(commands);

        // Any command that also implements PrefixCommand is auto-registered as !cmd.
        for (SlashCommand cmd : commands) {
            if (cmd instanceof PrefixCommand pc) prefixRouter.register(pc);
        }

        VoiceChannelListener voiceListener = new VoiceChannelListener(audio);

        AudioModuleConfig audioConfig = new AudioModuleConfig()
                .withDaveSessionFactory(new LDJDADaveSessionFactory(new NativeDaveFactory()));

        JDA jda = JDABuilder.createLight(token,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.VOICE)
                .enableCache(CacheFlag.VOICE_STATE)
                .setAudioModuleConfig(audioConfig)
                .addEventListeners(registry, router, voiceListener, prefixRouter)
                .build()
                .awaitReady();

        panel.bind(jda);
        registry.publish(jda);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down BertiniBot V2...");
            audio.shutdown();
            jda.shutdown();
        }, "bot-shutdown"));

        log.info("BertiniBot V2 online as {} | {} slash + {} prefix command(s) | filter terms: {}",
                jda.getSelfUser().getName(), commands.size(), prefixRouter.commands().size(),
                filter.blockedTerms());
    }

    /**
     * On Windows, route SSL validation through the OS truststore.
     *
     * Some antivirus / firewall setups intercept HTTPS and present a locally
     * signed certificate. The JDK truststore (cacerts) doesn't know it, but
     * the Windows root store does, so this prevents PKIX errors when talking
     * to Discord and YouTube. No-op on other operating systems.
     */
    private static void applyWindowsTrustStore() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.startsWith("windows")) {
            System.setProperty("javax.net.ssl.trustStoreType", "WINDOWS-ROOT");
        }
    }
}
