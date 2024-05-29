/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.util;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import javax.crypto.IllegalBlockSizeException;

import org.openhab.binding.tuya.internal.data.CommandByte;
import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.binding.tuya.internal.exceptions.ParseException;

/**
 * Parser for messages, with decryption where needed. Hence, a parser
 * is device dependent.
 * <p>
 * Ported from https://github.com/codetheweb/tuyapi.
 *
 * @author Wim Vissers.
 */
public class MessageParser {

    // Helper class instances.
    private TuyaCipher cipher;
    private final String version;

    public MessageParser(String version, String key) {
        try {
            cipher = new TuyaCipher(key);
        } catch (UnsupportedEncodingException e) {
        }
        // Should not happen.
        this.version = version;
    }

    public Message decode(byte[] buffer) throws ParseException {
        //https://github.com/jasonacox/tinytuya/discussions/260
        int length = buffer.length;
        // Check for length
        // At minimum requires: prefix (4), sequence (4), command (4), length (4),
        // CRC (4), and suffix (4) for 24 total bytes
        // Messages from the device also include return code (4), for 28 total bytes
        if (length < 24) {
            throw new ParseException("Packet too short. Length: " + length);
        }

        int sequenceNumberIndex, commandByteIndex, payloadSizeIndex, returnCodeIndex, retcode_len, payloadStart, payloadEnd;

        // Check for prefix
        long prefix = BufferUtils.getUInt32(buffer, 0);
        if (prefix == 0x000055AA) {
            sequenceNumberIndex = 4;
            commandByteIndex = 8;
            payloadSizeIndex = 12;
            returnCodeIndex = 16;
            retcode_len = 4;
            payloadStart = 20;
            payloadEnd = BufferUtils.indexOfUInt32(buffer, 0x0000AA55) - 4/*CRC*/;
        } else if (false && prefix == 0x00006699) {
            sequenceNumberIndex = 6;
            commandByteIndex = 10;
            payloadSizeIndex = 14;
            returnCodeIndex = 18;
            retcode_len = 0;
            payloadStart = 30;
            payloadEnd = BufferUtils.indexOfUInt32(buffer, 0x00009966) - 16/*CRC*/;
        } else {
            throw new ParseException("Prefix does not match: " + String.format("%x", prefix));
        }

        // Get sequence number
        long sequenceNumber = BufferUtils.getUInt32(buffer, sequenceNumberIndex);

        // Get command byte
        long commandByte = BufferUtils.getUInt32(buffer, commandByteIndex);

        // Get payload size
        long payloadSize = BufferUtils.getUInt32(buffer, payloadSizeIndex);

        // Get the return code, 0 = success
        // This field is only present in messages from the devices
        // Absent in messages sent to device
        long returnCode = retcode_len > 0 ? BufferUtils.getUInt32(buffer, returnCodeIndex) : 0;

        // Get the payload
        // Adjust for messages lacking a return code
        byte[] payload;

        payload = Arrays.copyOfRange(buffer, payloadStart, payloadEnd);

        // Check CRC
        long expectedCrc = BufferUtils.getUInt32(buffer, payloadEnd);
        long computedCrc = Crc.crc32(Arrays.copyOfRange(buffer, 0, payloadEnd));

        if (computedCrc != expectedCrc) {
            throw new ParseException("Crc error. Expected: " + expectedCrc + ", computed: " + computedCrc);
        }
        try {
            byte[] data = cipher.decrypt(payload);
            String text = new String(data, "UTF-8");
            return new Message(payload, sequenceNumber, commandByte, text);
        } catch (UnsupportedEncodingException | IllegalBlockSizeException e) {
            return new Message(payload, sequenceNumber, commandByte, new String(payload));
        }
    }

    public byte[] encode(byte[] input, CommandByte command, long sequenceNo) {
        byte[] payload = null;
        // Version 3.3 is always encrypted.
        if (version.equals("3.3")) {
            payload = cipher.encrypt(input);
            // Check if we need an extended header. Depends on command.
            if (!command.equals(CommandByte.DP_QUERY)) {
                // Add 3.3 header.
                byte[] buffer = new byte[payload.length + 15];
                BufferUtils.fill(buffer, (byte) 0x00, 0, 15);
                BufferUtils.copy(buffer, "3.3", 0);
                BufferUtils.copy(buffer, payload, 15);
                payload = buffer;
            }
        } else {
            // todo: older protocols
            payload = input;
        }

        // Allocate buffer with room for payload + 24 bytes for
        // prefix, sequence, command, length, crc, and suffix
        byte[] buffer = new byte[payload.length + 24];

        // Add prefix, command and length.
        BufferUtils.putUInt32(buffer, 0, 0x000055AA);
        BufferUtils.putUInt32(buffer, 8, command.getValue());
        BufferUtils.putUInt32(buffer, 12, payload.length + 8);

        // Optionally add sequence number.
        if (sequenceNo >= 0) {
            BufferUtils.putUInt32(buffer, 4, sequenceNo);
        }

        // Add payload, crc and suffix
        BufferUtils.copy(buffer, payload, 16);
        byte[] crcbuf = new byte[payload.length + 16];
        BufferUtils.copy(crcbuf, buffer, 0, payload.length + 16);
        BufferUtils.putUInt32(buffer, payload.length + 16, Crc.crc32(crcbuf));
        BufferUtils.putUInt32(buffer, payload.length + 20, 0x0000AA55);

        return buffer;
    }
}
