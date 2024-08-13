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

import org.openhab.binding.tuya.internal.data.ColorLedState;
import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;

/**
 * A handler for a Tuya Color LED device.
 *
 * @author Wim Vissers
 */
public class ColorLedHandler extends AbstractTuyaHandler {

    public ColorLedHandler(Thing thing) {
        super(thing);
    }

    /**
     * This method is called when a DeviceEventEmitter.Event.MESSAGE_RECEIVED is received from the device. This could
     * result in a possible state change of this things channels.
     */
    @Override
    protected void handleMessage(Message message) {
        super.handleMessage(message);
        updateStates(message, ColorLedState.class);
    }

    /**
     * Add the commands to the dispatcher.
     */
    @Override
    protected void initCommandDispatcher() {
        // Channel power command with OnOffType.
        commandDispatcher.on(CHANNEL_POWER, OnOffType.class, (ev, command) -> {
            return new ColorLedState().withPower(command);
        });

        // Color mode command with OnOffType.
        commandDispatcher.on(CHANNEL_COLOR_MODE, OnOffType.class, (ev, command) -> {
            return new ColorLedState().withColorMode(command);
        });

        // Brightness with PercentType.
        commandDispatcher.on(CHANNEL_BRIGHTNESS, PercentType.class, (ev, command) -> {
            updateState(new ChannelUID(thing.getUID(), CHANNEL_COLOR_MODE), OnOffType.OFF);
            return new ColorLedState().withBrightness(command).withColorMode(OnOffType.OFF);
        });

        // Brightness with DecimalType (deprecated).
        commandDispatcher.on(CHANNEL_BRIGHTNESS, DecimalType.class, (ev, command) -> {
            updateState(new ChannelUID(thing.getUID(), CHANNEL_COLOR_MODE), OnOffType.OFF);
            return new ColorLedState().withBrightness(command).withColorMode(OnOffType.OFF);
        });

        // Color temperature with PercentType.
        commandDispatcher.on(CHANNEL_COLOR_TEMPERATURE, PercentType.class, (ev, command) -> {
            updateState(new ChannelUID(thing.getUID(), CHANNEL_COLOR_MODE), OnOffType.OFF);
            return new ColorLedState().withColorTemperature(command).withColorMode(OnOffType.OFF);
        });

        // Color temperature with DecimalType (deprecated).
        commandDispatcher.on(CHANNEL_COLOR_TEMPERATURE, DecimalType.class, (ev, command) -> {
            updateState(new ChannelUID(thing.getUID(), CHANNEL_COLOR_MODE), OnOffType.OFF);
            return new ColorLedState().withColorTemperature(command).withColorMode(OnOffType.OFF);
        });

        // Color with HSBType.
        commandDispatcher.on(CHANNEL_COLOR, HSBType.class, (ev, command) -> {
            updateState(new ChannelUID(thing.getUID(), CHANNEL_COLOR_MODE), OnOffType.ON);
            return new ColorLedState().withColor(command).withColorMode(OnOffType.ON);
        });
    }
}
