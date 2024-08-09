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
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;

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
        this.version = version;
        cipher = new TuyaCipher(key.getBytes(StandardCharsets.UTF_8));
    }

    public MessageParser(String version, byte[] key) {
        this.version = version;
        cipher = new TuyaCipher(key);
    }

    public MessageParser() {
        this.version = "";
        cipher = new TuyaCipher();
    }

    public Message decode(byte[] buffer) throws ParseException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, NoSuchPaddingException, NoSuchAlgorithmException {
        //https://github.com/jasonacox/tinytuya/discussions/260
        //String hex = BufferUtils.bytesToHex(buffer);

        int length = buffer.length;
        // Check for length
        // At minimum requires: prefix (4), sequence (4), command (4), length (4),
        // CRC (4), and suffix (4) for 24 total bytes
        // Messages from the device also include return code (4), for 28 total bytes
        if (length < 24) {
            throw new ParseException("Packet too short. Length: " + length);
        }

        int sequenceNumberIndex, commandByteIndex, payloadSizeIndex, returnCodeIndex, payloadStartIndex, payloadEndIndex;

        // Check for prefix
        long prefix = BufferUtils.getUInt32(buffer, 0);
        if (prefix == 0x000055AA) {

            //EndMarker - 0x0000AA55
            if (buffer[buffer.length - 4] != 0
                    || buffer[buffer.length - 3] != 0
                    || buffer[buffer.length - 2] != (byte) 0xAA
                    || buffer[buffer.length - 1] != (byte) 0x55)
                throw new ParseException("Suffix does not match: 0x0000AA55");

            sequenceNumberIndex = 4;
            commandByteIndex = 8;
            payloadSizeIndex = 12;
            returnCodeIndex = 16;
            payloadStartIndex = 20;
            payloadEndIndex = buffer.length - 8/*CRC*/;

        } else if (prefix == 0x00006699) {

            //EndMarker - 0x00009966
            if (buffer[buffer.length - 4] != 0
                    || buffer[buffer.length - 3] != 0
                    || buffer[buffer.length - 2] != (byte) 0x99
                    || buffer[buffer.length - 1] != (byte) 0x66)
                throw new ParseException("Suffix does not match: 0x00009966");

            sequenceNumberIndex = 6;
            commandByteIndex = 10;
            payloadSizeIndex = 14;
            returnCodeIndex = 18;
            payloadStartIndex = 30;
            payloadEndIndex = buffer.length - 4;

        } else {
            throw new ParseException("Prefix does not match: " + String.format("%x", prefix));
        }

        // Get sequence number
        long sequenceNumber = BufferUtils.getUInt32(buffer, sequenceNumberIndex);

        // Get command byte
        long commandByte = BufferUtils.getUInt32(buffer, commandByteIndex);

        // Get payload size
        long payloadSize = BufferUtils.getUInt32(buffer, payloadSizeIndex);

        // Get the payload
        // Adjust for messages lacking a return code
        byte[] payload;

        payload = Arrays.copyOfRange(buffer, payloadStartIndex, payloadEndIndex);

        if (prefix == 0x000055AA) {
            // Get the return code, 0 = success
            // This field is only present in messages from the devices
            // Absent in messages sent to device
            long returnCode = BufferUtils.getUInt32(buffer, returnCodeIndex);

            // Check CRC
            long expectedCrc = BufferUtils.getUInt32(buffer, payloadEndIndex);
            long computedCrc = Crc.crc32(Arrays.copyOfRange(buffer, 0, payloadEndIndex));

            if (computedCrc != expectedCrc) {
                throw new ParseException("Crc error. Expected: " + expectedCrc + ", computed: " + computedCrc);
            }

            byte[] data = cipher.decryptV3(payload);
            return new Message(sequenceNumber, returnCode, commandByte, data);

        } else if (prefix == 0x00006699) {
            byte[] nonce = new byte[12];
            byte[] header = new byte[14];
            BufferUtils.copy(nonce, 0, buffer, 18, 12);
            BufferUtils.copy(header, 0, buffer, 4, 14);

            byte[] dataWithReturnCode = cipher.decryptV5(payload, nonce, header);
            byte[] data = new byte[dataWithReturnCode.length - 4];
            BufferUtils.copy(data, 0, dataWithReturnCode, 4, dataWithReturnCode.length - 4);
            long returnCode = dataWithReturnCode[0] * 256 * 256 * 256 + dataWithReturnCode[1] * 256 * 256 + dataWithReturnCode[2] * 256 + dataWithReturnCode[3];
            return new Message(sequenceNumber, returnCode, commandByte, data);
        } else {
            throw new ParseException("Prefix does not match: " + String.format("%x", prefix));
        }
    }

    public byte[] encode(byte[] input, CommandByte command, long sequenceNo) throws Exception {

        // Version 3.3 is always encrypted.
        if (version.equals("3.3")) {

            byte[] payload = cipher.encryptV3(input);

            // Check if we need an extended header. Depends on command.
            if (!command.equals(CommandByte.DP_QUERY)) {
                // Add 3.3 header.
                byte[] buffer = new byte[payload.length + 15];
                Arrays.fill(buffer, 0, 15, (byte) 0x00);
                BufferUtils.copy(buffer, 0, "3.3".getBytes());
                BufferUtils.copy(buffer, 15, payload);
                payload = buffer;
            }

            if (sequenceNo < 0) {
                sequenceNo = 0;
            }
            // Allocate buffer with room for  6 * 4 = 24 bytes
            // prefix (4), sequence (4), command (4), length (4), payload (X), crc (4), and suffix (4)
            byte[] buffer = new byte[payload.length + 24];

            BufferUtils.putUInt32(buffer, 0, 0x000055AA); /*prefix */
            BufferUtils.putUInt32(buffer, 4, sequenceNo); /*sequence number*/
            BufferUtils.putUInt32(buffer, 8, command.getValue()); /*command id*/
            BufferUtils.putUInt32(buffer, 12, payload.length + 4/*crc*/ + 4/*footer*/); /* length*/

            // Add payload, crc and suffix
            BufferUtils.copy(buffer, 16, payload); /*variable length encrypted payload data*/
            BufferUtils.putUInt32(buffer, 16 + payload.length, Crc.crc32(buffer, 0, 16 + payload.length));
            BufferUtils.putUInt32(buffer, 16 + payload.length + 4, 0x0000AA55);

            return buffer;

        } else if (version.equals("3.5")) {

            byte[] nonce = "17229322668.".getBytes(StandardCharsets.UTF_8); //12
            // Allocate buffer with room for  6 * 4 = 24 bytes
            // prefix (4),unknown (2), sequence (4), command id (4), length (4), nonce (12), payload (X), tag (16) and suffix (4)
            byte[] buffer = new byte[input.length + 50];

            // Add prefix, command and length.
            BufferUtils.putUInt32(buffer, 0, 0x00006699);  /*prefix */
            BufferUtils.putUInt32(buffer, 4, 0x0000);      /*unknown*/
            BufferUtils.putUInt32(buffer, 6, sequenceNo);       /*sequence number*/
            BufferUtils.putUInt32(buffer, 10, command.getValue());  /*command id*/
            BufferUtils.putUInt32(buffer, 14, 12/*IV*/ + input.length + 16/*TAG*/);  /* length*/
            BufferUtils.copy(buffer, 18, nonce); /*nonce*/

            byte[] header = new byte[14];
            BufferUtils.copy(header, 0, buffer, 4, 2/*unknown*/ + 4/*sequence id*/ + 4/*command id*/ + 4/*length*/);
            byte[] payload = cipher.encryptV5(input, nonce, header);
            BufferUtils.copy(buffer, 18 + 12, payload); /*variable length encrypted payload data*/
            BufferUtils.putUInt32(buffer, 18 + 12 + payload.length, 0x00009966);
            return buffer;

        } else {
            throw new Exception("Not implemented");
        }
    }

}
