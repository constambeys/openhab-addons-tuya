/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.data;

import org.openhab.binding.tuya.internal.discovery.JsonDiscovery;

import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Message received from the network.
 *
 * @author Wim Vissers.
 */
public class Message {

    private static final Gson GSON = new Gson();

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

    public Message(long sequenceNumber, long returnCode, long commandByte, byte[] data) {
        this.sequenceNumber = sequenceNumber;
        this.returnCode = returnCode;
        this.commandByte = CommandByte.valueOf((int) commandByte);
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

        String text = null;
        try {
            text = new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.err.println(e.getStackTrace());
        }
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
        return GSON.fromJson(getData(), JsonDiscovery.class);
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
        return GSON.fromJson(getData(), clazz);
    }
}
