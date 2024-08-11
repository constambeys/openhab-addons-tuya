/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.net;

import org.openhab.binding.tuya.internal.data.CommandByte;
import org.openhab.binding.tuya.internal.data.DeviceState;
import org.openhab.binding.tuya.internal.data.Version;
import org.openhab.binding.tuya.internal.discovery.DeviceDescriptor;
import org.openhab.binding.tuya.internal.util.MessageParser;

import java.nio.charset.StandardCharsets;

/**
 * Item to populate the TuyaClient send queue.
 *
 * @author Wim Vissers - Initial contribution.
 */
public class QueueItem {

    private final DeviceDescriptor device;
    private final CommandByte commandByte;
    private final DeviceState deviceState;

    public QueueItem(DeviceDescriptor device, DeviceState deviceState, CommandByte commandByte) {
        this.device = device;
        this.deviceState = deviceState;
        this.commandByte = commandByte;
    }

    public CommandByte getCommandByte() {
        return commandByte;
    }

    public DeviceState getDeviceState() {
        return deviceState;
    }

    /**
     * Encode the item for sending.
     *
     * @param messageParser the message parser (depends on the thing).
     * @param sequenceNo    sequence number provided by the Tuya client.
     * @return the byte array, ready to send.
     */
    public byte[] encode(MessageParser messageParser, long sequenceNo) throws Exception {

        String payload;
        switch (commandByte) {
            case HEART_BEAT:
                payload = String.format("{\"gwId\":\"%s\",\"devId\":\"%s\"}", device.getDevId(), device.getDevId());
                break;
            case DP_QUERY:
                payload = String.format("{}");
                break;
            case CONTROL:
                if (device.getVersion() == Version.V3_5) {
                    payload = String.format("%s%s{\"protocol\":5,\"t\":%d,\"data\":%s}", "3.5", new String(new byte[12]), deviceState.getTime(), deviceState.toJson());
                } else if (device.getVersion() == Version.V3_3) {
                    payload = String.format("{}");
                } else {
                    payload = String.format("{}");
                }
                break;
            default:
                payload = deviceState.toJson();
                break;

        }

        return messageParser.encode(payload.getBytes(StandardCharsets.UTF_8), commandByte, sequenceNo);
    }

    /**
     * Return true when the given QueueItem is conflicting with this item. This test is used to remove conflicting items
     * from the queue. An example is a switch that may be on or off, and it makes no sense to have both an on and an off
     * command in the queue at the same time.
     *
     * @param other the item to compare to.
     * @return true when conflicting.
     */
    public boolean isConflicting(QueueItem other) {
        if (other != null && getCommandByte().equals(CommandByte.HEART_BEAT)
                && other.getCommandByte().equals(CommandByte.HEART_BEAT)) {
            return true;
        }
        return deviceState == null ? false : deviceState.isConflicting(other);
    }
}
