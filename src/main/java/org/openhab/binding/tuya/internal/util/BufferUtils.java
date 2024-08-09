/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.util;

import java.nio.ByteBuffer;

import org.openhab.binding.tuya.internal.exceptions.ParseException;

/**
 * Utility class for buffer operations.
 *
 * @author Wim Vissers.
 */
public class BufferUtils {

    /**
     * Extract the contents of the byte buffer as a new array for further processing
     * in byte array oriented APis. In particular, the bytes between position() and
     * limit() are copied.The position in the buffer is increased by the number of
     * remaining bytes.
     *
     * @param buffer the buffer.
     * @return the copy of the contents of the buffer as byte array.
     */
    public static byte[] getBytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    /**
     * Get an unsigned 4 bytes number from the byte buffer.
     *
     * @param buffer the buffer containing the bytes.
     * @param start  the start index (0-based).
     * @return the number, reading 4 bytes from start to start + 4.
     * @throws ParseException
     */
    public static long getUInt32(byte[] buffer, int start) throws ParseException {
        if (buffer.length - start - 4 < 0) {
            throw new ParseException("Buffer too short.");
        }
        long result = 0;
        for (int i = start; i < start + 4; i++) {
            result *= 256;
            result += (buffer[i] & 0xff);
        }
        return result;
    }

    /**
     * Write an unsigned 4 bytes to the byte buffer.
     *
     * @param to the byte buffer.
     * @param start  the start index.
     * @param value  the number to store.
     */
    public static void putUInt32(byte[] to, int start, long value) {
        long lv = value;
        for (int i = 3; i >= 0; i--) {
            to[start + i] = (byte) (((lv & 0xFFFFFFFF) % 0x100) & 0xFF);
            lv /= 0x100;
        }
    }

    /**
     * Get the position of the marker.
     *
     * @param marker
     * @return
     */
    public static byte[] longToBytes(long marker) {
        long mrk = marker;
        byte[] m = new byte[4];
        for (int i = 3; i >= 0; i--) {
            m[i] = (byte) (mrk & 0xFF);
            mrk /= 256;
        }
        return m;
    }


    public static void copy(byte[] to, int start, byte[] source) {
        System.arraycopy(source, 0, to, start, source.length);
    }


    public static void copy(byte[] to, int start, byte[] source, int sourceIndex, int length) {
        System.arraycopy(source, sourceIndex, to, start, length);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');  // Pad with leading zero if necessary
            }
            hexString.append(hex);
        }
        return hexString.toString().toUpperCase();  // Convert to uppercase for consistency
    }
}
