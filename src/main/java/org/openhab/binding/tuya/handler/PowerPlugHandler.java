/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.handler;

import static org.openhab.binding.tuya.TuyaBindingConstants.CHANNEL_POWER;

import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.binding.tuya.internal.data.PowerPlugState;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A handler for a Tuya Switch device.
 *
 * @author Wim Vissers
 */
public class PowerPlugHandler extends AbstractTuyaHandler {

    private Logger logger = LoggerFactory.getLogger(PowerPlugHandler.class);

    public PowerPlugHandler(Thing thing) {
        super(thing);
    }

    /**
     * This method is called when a DeviceEventEmitter.Event.MESSAGE_RECEIVED is received from the device. This could
     * result in a possible state change of this things channels.
     */
    @Override
    protected void handleMessage(Message message) {
        super.handleMessage(message);
        updateStates(message, PowerPlugState.class);
    }

    /**
     * Add the commands to the dispatcher.
     */
    @Override
    protected void initCommandDispatcher() {
        // Channel power command with OnOffType.
        commandDispatcher.on(CHANNEL_POWER, OnOffType.class, (ev, command) -> {
            return new PowerPlugState().withPower(command);
        });
    }
}
