package org.openhab.binding.tuya.internal.data;

public enum Version {
    V3_3,
    V3_5,
    VALL,
    VUKNOWN;

    public static Version parse(String value) {
        if (value.equals("3.5")) {
            return V3_5;
        } else if (value.equals("3.3")) {
            return V3_3;
        } else {
            return VUKNOWN;
        }
    }
}
