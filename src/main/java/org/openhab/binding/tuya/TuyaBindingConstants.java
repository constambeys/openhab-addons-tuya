/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya;

import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link TuyaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Wim Vissers - Initial contribution
 * @author Gert Van Hoecke - Curtain switch added
 */
public class TuyaBindingConstants {

    // Binding id.
    public static final String BINDING_ID = "tuya";

    // List of all Thing Type UIDs.
    public final static ThingTypeUID THING_TYPE_POWER_PLUG = new ThingTypeUID(BINDING_ID, "powerplug");
    public final static ThingTypeUID THING_TYPE_COLOR_LED = new ThingTypeUID(BINDING_ID, "colorled");
    public final static ThingTypeUID THING_TYPE_FILAMENT_LED = new ThingTypeUID(BINDING_ID, "filamentled");
    public final static ThingTypeUID THING_TYPE_SIREN = new ThingTypeUID(BINDING_ID, "siren");
    public final static ThingTypeUID THING_TYPE_CURTAIN_SWITCH = new ThingTypeUID(BINDING_ID, "curtainswitch");
    public final static ThingTypeUID THING_TYPE_SWITCH = new ThingTypeUID(BINDING_ID, "switch");

    // List of all Channel ids.
    public final static String CHANNEL_POWER = "power";
    public final static String CHANNEL_BRIGHTNESS = "brightness";
    public final static String CHANNEL_COLOR = "color";
    public final static String CHANNEL_COLOR_TEMPERATURE = "colorTemperature";
    public final static String CHANNEL_COLOR_MODE = "colorMode";
    public final static String CHANNEL_ALARM = "alarm";
    public final static String CHANNEL_VOLUME = "volume";
    public final static String CHANNEL_DURATION = "duration";
    public final static String CHANNEL_CURTAIN = "curtain";

}
