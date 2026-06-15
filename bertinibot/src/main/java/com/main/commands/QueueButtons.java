package com.main.commands;

import com.main.audio.Scheduler;
import com.main.util.EmbedFactory;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

/**
 * Navigation button row for the paginated {@code /queue} embed.
 *
 * Custom IDs follow the pattern {@code queue:nav:<page>} where {@code page}
 * is the zero-based destination, or {@code queue:nav:noop} for the disabled
 * "X / Y" label in the middle.
 */
public final class QueueButtons {

    public static final String PREFIX = "queue";

    private QueueButtons() {}

    /** Returns the navigation row, or {@code null} if the queue fits on one page. */
    public static ActionRow navRow(Scheduler scheduler, int page) {
        int size = scheduler.snapshot().size();
        int totalPages = Math.max(1, (size + EmbedFactory.QUEUE_PAGE_SIZE - 1) / EmbedFactory.QUEUE_PAGE_SIZE);
        if (totalPages <= 1) return null;
        int p = Math.max(0, Math.min(page, totalPages - 1));
        boolean atFirst = p <= 0;
        boolean atLast = p >= totalPages - 1;

        return ActionRow.of(
                Button.secondary("queue:nav:0", "\u23EE").withDisabled(atFirst),
                Button.secondary("queue:nav:" + (p - 1), "\u25C0").withDisabled(atFirst),
                Button.secondary("queue:nav:noop", (p + 1) + " / " + totalPages).asDisabled(),
                Button.secondary("queue:nav:" + (p + 1), "\u25B6").withDisabled(atLast),
                Button.secondary("queue:nav:" + (totalPages - 1), "\u23ED").withDisabled(atLast)
        );
    }
}
