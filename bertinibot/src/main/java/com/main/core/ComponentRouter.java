package com.main.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Generic dispatcher for component (button) interactions.
 *
 * Handlers are registered against the component ID prefix (the text before the
 * first {@code ':'}), so a panel can issue button IDs like {@code panel:skip}
 * and the same handler covers all of them.
 */
public final class ComponentRouter extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ComponentRouter.class);

    private final Map<String, Consumer<ButtonInteractionEvent>> handlers = new HashMap<>();

    public void onButton(String prefix, Consumer<ButtonInteractionEvent> handler) {
        handlers.put(prefix, handler);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        int colon = id.indexOf(':');
        String prefix = colon < 0 ? id : id.substring(0, colon);

        Consumer<ButtonInteractionEvent> handler = handlers.get(prefix);
        if (handler == null) return;
        try {
            handler.accept(event);
        } catch (Exception ex) {
            log.error("Button handler for prefix '{}' threw", prefix, ex);
            if (!event.isAcknowledged()) {
                event.reply("Error procesando boton.").setEphemeral(true).queue();
            }
        }
    }
}
