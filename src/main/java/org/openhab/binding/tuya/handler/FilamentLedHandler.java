/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.handler;

import static org.openhab.binding.tuya.TuyaBindingConstants.*;

import org.openhab.binding.tuya.internal.data.FilamentLedState;
import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.Thing;

/**
 * A handler for a Tuya Filament LED device.
 *
 * @author Wim Vissers
 */
public class FilamentLedHandler extends AbstractTuyaHandler {

    public FilamentLedHandler(Thing thing) {
        super(thing);
    }

    /**
     * This method is called when a DeviceEventEmitter.Event.MESSAGE_RECEIVED is received from the device. This could
     * result in a possible state change of this things channels.
     */
    @Override
    protected void handleStatusMessage(Message message) {
        super.handleStatusMessage(message);
        updateStates(message, FilamentLedState.class);
    }

    /**
     * Add the commands to the dispatcher.
     */
    @Override
    protected void initCommandDispatcher() {
        // Channel power command with OnOffType.
        commandDispatcher.on(CHANNEL_POWER, OnOffType.class, (ev, command) -> {
            return new FilamentLedState().withPower(command);
        });

        // Brightness with PercentType.
        commandDispatcher.on(CHANNEL_BRIGHTNESS, PercentType.class, (ev, command) -> {
            return new FilamentLedState().withBrightness(command);
        });

        // Brightness with DecimalType (deprecated).
        commandDispatcher.on(CHANNEL_BRIGHTNESS, DecimalType.class, (ev, command) -> {
            return new FilamentLedState().withBrightness(command);
        });

        // Color temperature with PercentType.
        commandDispatcher.on(CHANNEL_COLOR_TEMPERATURE, PercentType.class, (ev, command) -> {
            return new FilamentLedState().withColorTemperature(command);
        });

        // Color temperature with DecimalType (deprecated).
        commandDispatcher.on(CHANNEL_COLOR_TEMPERATURE, DecimalType.class, (ev, command) -> {
            return new FilamentLedState().withColorTemperature(command);
        });
    }
}
