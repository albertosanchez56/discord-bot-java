package com.main.panel;

import com.main.audio.Scheduler;
import com.main.audio.Scheduler.LoopMode;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

public final class PanelButtons {

    public static final String PREFIX = "panel";

    private PanelButtons() {}

    public static ActionRow transportRow(Scheduler s) {
        boolean paused = s.isPaused();
        Button playPause = paused
                ? Button.success("panel:resume", "Reanudar")
                : Button.primary("panel:pause", "Pausar");
        return ActionRow.of(
                playPause,
                Button.secondary("panel:skip", "Saltar"),
                Button.secondary("panel:loop", "Loop: " + loopLabel(s.getLoopMode())),
                Button.secondary("panel:shuffle", "Mezclar"),
                Button.danger("panel:stop", "Parar")
        );
    }

    public static ActionRow volumeRow(Scheduler s) {
        return ActionRow.of(
                Button.secondary("panel:voldown", "Vol -10"),
                Button.secondary("panel:vollabel", s.getVolume() + "%").asDisabled(),
                Button.secondary("panel:volup", "Vol +10")
        );
    }

    private static String loopLabel(LoopMode mode) {
        return switch (mode) {
            case OFF -> "off";
            case TRACK -> "pista";
            case QUEUE -> "cola";
        };
    }
}
