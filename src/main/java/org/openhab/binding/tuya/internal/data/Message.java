/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.openhab.binding.tuya.internal.discovery.JsonDiscovery;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Message received from the network.
 *
 * @author Wim Vissers.
 */
public class Message {

    private long sequenceNumber;
    private long returnCode;
    private CommandByte commandByte;
    private byte[] data;

    public Message(String error) {
        returnCode = 1;
        this.data = error.getBytes(StandardCharsets.UTF_8);
    }

    public Message(Exception ex) {
        returnCode = 1;
        this.data = ex.getMessage().getBytes(StandardCharsets.UTF_8);
    }

    public Message(long sequenceNumber, long returnCode, CommandByte commandByte, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.returnCode = returnCode;
        this.commandByte = commandByte;
        this.data = data;
    }

    /**
     * The raw data may be just an error message, or decoded more complex data.
     *
     * @return
     */
    public byte[] getRawData() {
        return data;
    }

    public String getData() {

        // Find the position of the first zero byte
        int n;
        for (n = 0; n < data.length && data[n] != 0; n++) {
        }
        String text = new String(data, 0, n, StandardCharsets.UTF_8);

        return text;
    }

    /**
     * Return true is the message contains data that is probably json encoded. If this method returns false, it is
     * useless to try to parse it as json data. An empty json object is still valid data, so "{}" will also return true.
     *
     * @return true if the message contains data.
     */
    public boolean hasData() {
        return getData() != null && !getData().isEmpty() && getData().startsWith("{");
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getReturnCode() {
        return returnCode;
    }

    public CommandByte getCommandByte() {
        return commandByte;
    }

    /**
     * Try to parse the message data as a DeviceDatagram.
     *
     * @return the DeviceDatagram if possible.
     */
    public JsonDiscovery toJsonDiscovery() {
        Gson gson = new Gson();
        return gson.fromJson(getData(), JsonDiscovery.class);
    }

    /**
     * Try to parse the message data to the given class.
     *
     * @param %lt;T&gt; This method converts the data to DeviceState or subclasses thereof, given by the target
     *                  class.
     * @param clazz     the target class.
     * @return
     * @return a new instance of clazz filled with the message data.
     */
    public <T extends DeviceState> T toDeviceState(Class<T> clazz) {
        Gson gson = new GsonBuilder()
                .create();

        Map<String, Object> nestedMap = gson.fromJson(getData(), Map.class);
        Object oDps = findKeyRecursively(nestedMap, "dps");
        Map<String, Object> state = new HashMap<>();
        state.put("dps", oDps);
        JsonElement jsonElement = gson.toJsonTree(state);
        return gson.fromJson(jsonElement, clazz);
    }

    public static Object findKeyRecursively(Map<String, Object> map, String keyToFind) {
        // Check if the map contains the key at the current level
        if (map.containsKey(keyToFind)) {
            return map.get(keyToFind);
        }

        // Iterate over the entries in the map
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();

            // If the value is another map, perform a recursive search
            if (value instanceof Map) {
                Object result = findKeyRecursively((Map<String, Object>) value, keyToFind);
                if (result != null) {
                    return result; // Return the value if the key is found
                }
            }

            // If the value is a list, iterate through the list and check for maps
            if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        Object result = findKeyRecursively((Map<String, Object>) item, keyToFind);
                        if (result != null) {
                            return result; // Return the value if the key is found
                        }
                    }
                }
            }
        }

        // Return null if the key was not found
        return null;
    }
}
