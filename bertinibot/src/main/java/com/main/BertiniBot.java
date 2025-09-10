package com.main;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.main.commands.BuildCommand;
import com.main.commands.CleanListCommand;
import com.main.commands.ListCommand;
import com.main.commands.PlayCommand;
import com.main.commands.SkipCommand;
import com.main.config.BotConfig;
import com.main.listeners.CommandListener;
import com.main.listeners.VoiceChannelListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class BertiniBot {
    private static final Logger logger = LoggerFactory.getLogger(BertiniBot.class);
    private static JDA jda;
    public static final ExecutorService YTDLP_POOL = Executors.newFixedThreadPool(4);

    public static void main(String[] args) {
        String botToken = BotConfig.getBotToken();

        if (botToken == null || botToken.isEmpty()) {
            logger.error("Bot Token not found in config.properties. Please provide a valid token");
            return;
        }

        try {
            jda = JDABuilder.createDefault(
                    botToken,
                    GatewayIntent.GUILD_MESSAGES, // poder ver mensajes en texto
                    GatewayIntent.MESSAGE_CONTENT, // poder leer el contenido de los mensajes
                    GatewayIntent.GUILD_VOICE_STATES // poder manejar conexiones de voz
            )
                    // registramos nuestros listeners:
                    .addEventListeners(
                            new CommandListener(), // tu listener genérico
                            new PlayCommand(),
                            new SkipCommand(),
                            new CleanListCommand(),
                            new ListCommand(),
                            new VoiceChannelListener() // el listener del !play que reproduce audio
                    )
                    .build();

            jda.awaitReady();
            logger.info("Bot is online and ready!");

            registerSlashCommands();
        } catch (Exception e) {
            logger.error("Error strarting the bot: ", e);
        }
    }

    private static void registerSlashCommands() {
        if (jda == null) {
            logger.error("JDA instance is not initialiced. Cannot register slash comands.");
            return;
        }

        logger.info("Registering Slash Commands...");
        jda.updateCommands().addCommands(Commands.slash("ping", "Checks the bot's latency to Discord's gateway."),
                Commands.slash("info", "Displays information about the bot."),
                Commands.slash("help", "Muestra todos los comandos"),
                Commands.slash("echo", "Responds back with your message").addOption(OptionType.STRING, "text",
                        "the text to echo", true),
                Commands.slash("moneda", "Lanza una moneda."),
                BuildCommand.getCommandData(),
                // Comando slash para reproducir música (opcional si prefieres solo !play)
                Commands.slash("play", "Reproduce audio desde YouTube")
                        .addOption(OptionType.STRING, "query", "URL de YouTube o búsqueda", true))
                .queue(success -> logger.info("Slash commands registered successfully!"),
                        failure -> logger.error("Failed to register slash commands: ", failure));
        ;
    }
}
