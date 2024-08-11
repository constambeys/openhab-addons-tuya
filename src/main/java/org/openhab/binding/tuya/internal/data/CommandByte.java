/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Human readable command types.
 *
 * @author Wim Vissers.
 */
public enum CommandByte {

    SESS_KEY_NEG_START(map(Version.V3_5, 3)),
    SESS_KEY_NEG_RESP(map(Version.V3_5, 4)),
    SESS_KEY_NEG_FINISH(map(Version.V3_5, 5)),

    CONTROL(map(Version.V3_3, 7, Version.V3_5, 13)),
    STATUS(map(Version.VALL, 8)),
    HEART_BEAT(map(Version.VALL, 9)),
    DP_QUERY(map(Version.V3_3, 10, Version.V3_5, 16)),
    UNKNOWN(map(Version.VALL, 255));

    private Map<String, Integer> values;

    CommandByte(Map values) {
        this.values = values;
    }

    public int getValue(Version version) {
        if (values.containsKey(version)) {
            return values.get(version);
        } else {
            return values.get(Version.VALL);
        }
    }

    public static CommandByte valueOf(Version version, int value) {
        for (CommandByte cb : CommandByte.values()) {
            if ((cb.values.containsKey(version) && cb.values.get(version) == value)
                    || (cb.values.containsKey(Version.VALL) && cb.values.get(Version.VALL) == value)) {
                return cb;
            }
        }
        return UNKNOWN;
    }

    private static <K, V> Map<K, V> map(Object... objects) {
        Map<K, V> map = new HashMap<>();
        for (int n = 0; n < objects.length; n += 2) {
            map.put((K) objects[n], (V) objects[n + 1]);
        }
        return map;
    }

}