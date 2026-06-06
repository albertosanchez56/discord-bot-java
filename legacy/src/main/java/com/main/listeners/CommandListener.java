package com.main.listeners;

import java.util.HashMap;
import java.util.Map;

import com.main.commands.BuildCommand;
import com.main.commands.CoinFlipCommand;
import com.main.commands.Command;
import com.main.commands.PingCommand;
import com.main.commands.InfoCommand;
import com.main.commands.EchoCommand;
import com.main.commands.HelpCommand;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;


public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class); 
    private final Map<String, Command> commands = new HashMap<>();

    public CommandListener(){
        commands.put("ping", new PingCommand());
        commands.put("info", new InfoCommand());
        commands.put("echo", new EchoCommand());
        commands.put("help", new HelpCommand());
        commands.put("moneda", new CoinFlipCommand());
        commands.put("build", new BuildCommand());

        logger.info("Registered {} commands.", commands.size());
    }

    @Override
    public void onReady(@NotNull ReadyEvent event)
    {
        logger.info("JDA is ready! Logged in as {}#{}", event.getJDA().getSelfUser().getName(),event.getJDA().getSelfUser().getDiscriminator());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Command command = commands.get(commandName);

        if (command != null) {
            logger.debug("Executing slash command: {} from user: {}", commandName, event.getUser().getName());
            command.executeSlash(event);
        } else {
            logger.warn("Unknown slash command: {} from user: {}", commandName, event.getUser().getName());
            event.reply("Unknown command!").setEphemeral(true).queue(); // Ephemeral reply for unknown commands
        }
    }
}
