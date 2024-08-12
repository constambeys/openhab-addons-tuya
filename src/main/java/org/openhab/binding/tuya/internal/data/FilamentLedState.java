/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.data;

import static org.openhab.binding.tuya.TuyaBindingConstants.*;

import org.openhab.binding.tuya.internal.annotations.Channel;
import org.openhab.binding.tuya.internal.discovery.DeviceDescriptor;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;

import com.google.gson.annotations.SerializedName;

/**
 * This is the description of the status of the Filament LED device.
 *
 * @author Wim Vissers.
 */
public class FilamentLedState extends DeviceState {

    private Dps dps;

    public FilamentLedState() {
        super();
        dps = new Dps();
    }

    public FilamentLedState withPower(Command command) {
        dps.dp1 = toBoolean(command);
        return this;
    }

    @Channel(CHANNEL_POWER)
    public OnOffType getPower() {
        return toOnOffType(dps.dp1);
    }

    public FilamentLedState withBrightness(Command command) {
        dps.dp2 = toInt(command, 1000);
        dps.dp1 = dps.dp2 > 0;
        return this;
    }

    @Channel(CHANNEL_BRIGHTNESS)
    public DecimalType getBrightness() {
        return dps.dp2 == null ? null : toDecimalType(dps.dp2, 1000);
    }

    public FilamentLedState withColorTemperature(Command command) {
        dps.dp3 = toInt(command, 1000);
        return this;
    }

    @Channel(CHANNEL_COLOR_TEMPERATURE)
    public DecimalType getColorTemperature() {
        return dps.dp3 == null ? null : toDecimalType(dps.dp3, 1000);
    }

    /**
     * The device properties. Please note that we use boxed classes here,
     * to allow them to be null. In case of setting properties, null properties
     * will not be serialized by Gson.
     */
    public class Dps {

        /**
         * Lamp on/off.
         */
        @SerializedName("20")
        private Boolean dp1;

        /**
         * Brightness 0..255.
         */
        @SerializedName("22")
        private Integer dp2;

        /**
         * Color temperature 0..255.
         */
        @SerializedName("23")
        private Integer dp3;
    }
}
