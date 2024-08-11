/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.data;

import static org.openhab.binding.tuya.TuyaBindingConstants.CHANNEL_POWER;

import org.openhab.binding.tuya.internal.annotations.Channel;
import org.openhab.binding.tuya.internal.discovery.DeviceDescriptor;
import org.openhab.binding.tuya.internal.net.QueueItem;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;

import com.google.gson.annotations.SerializedName;

/**
 * This is the description of the status of the PowerPlug device.
 *
 * @author Frédéric Hannes
 */
public class SwitchState extends DeviceState {

    private final Dps dps;

    public SwitchState() {
        super();
        dps = new Dps();
    }

    public SwitchState withPower(Command command) {
        dps.dp1 = toBoolean(command);
        return this;
    }

    @Channel(CHANNEL_POWER)
    public OnOffType getPower() {
        return toOnOffType(dps.dp1);
    }

    /**
     * Return true when the given QueueItem is conflicting with this item. This test is used to remove conflicting items
     * from the queue. An example is a switch that may be on or off, and it makes no sense to have both an on and an off
     * command in the queue at the same time.
     *
     * @param other the item to compare to.
     * @return true when conflicting.
     */
    @Override
    public boolean isConflicting(QueueItem other) {
        DeviceState ds = other == null ? null : other.getDeviceState();
        return ds != null && ds.getClass().equals(getClass()) && !((SwitchState) ds).dps.dp1.equals(dps.dp1);
    }

    public class Dps {

        /**
         * Switch state on/off.
         */
        @SerializedName("1")
        private Boolean dp1;

    }
}
