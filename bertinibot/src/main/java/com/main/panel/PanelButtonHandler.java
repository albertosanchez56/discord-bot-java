package com.main.panel;

import com.main.audio.AudioService;
import com.main.audio.GuildAudio;
import com.main.audio.Scheduler;
import com.main.audio.Scheduler.LoopMode;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * Handles button presses on the control panel.
 *
 * Custom IDs follow the pattern {@code panel:<action>}. The handler ignores
 * non-actionable IDs (like the disabled volume label).
 */
public final class PanelButtonHandler {

    private final AudioService audio;
    private final PanelService panel;

    public PanelButtonHandler(AudioService audio, PanelService panel) {
        this.audio = audio;
        this.panel = panel;
    }

    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Solo en servidores.").setEphemeral(true).queue();
            return;
        }

        String id = event.getComponentId();
        int colon = id.indexOf(':');
        String action = colon < 0 ? id : id.substring(colon + 1);

        GuildAudio g = audio.get(guild);
        Scheduler s = g.scheduler();

        boolean handled = switch (action) {
            case "pause" -> { s.setPaused(true); yield true; }
            case "resume" -> { s.setPaused(false); yield true; }
            case "skip" -> { audio.skip(guild); yield true; }
            case "loop" -> { s.setLoopMode(nextLoop(s.getLoopMode())); yield true; }
            case "shuffle" -> { s.shuffle(); yield true; }
            case "stop" -> {
                audio.clear(guild);
                audio.disconnect(guild);
                g.clearPanel();
                yield true;
            }
            case "volup" -> { s.setVolume(s.getVolume() + 10); yield true; }
            case "voldown" -> { s.setVolume(s.getVolume() - 10); yield true; }
            default -> false;
        };

        if (!handled) {
            event.reply("Accion no reconocida.").setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();
        panel.update(guild);
    }

    private static LoopMode nextLoop(LoopMode current) {
        return switch (current) {
            case OFF -> LoopMode.TRACK;
            case TRACK -> LoopMode.QUEUE;
            case QUEUE -> LoopMode.OFF;
        };
    }
}
