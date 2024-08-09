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
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.openhab.binding.tuya.internal.net.UdpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cipher class for encrypting and decrypting messages.
 * <p>
 * Ported from https://github.com/codetheweb/tuyapi.
 *
 * @author Wim Vissers.
 */
public class TuyaCipher implements UdpConfig {

    private static Logger logger = LoggerFactory.getLogger(TuyaCipher.class);

    private byte[] key;

    public TuyaCipher() {
        this.key = getDigest(DEFAULT_UDP_KEY);
    }

    public TuyaCipher(byte[] key) {
        this.key = key;
    }

    private static final byte[] getDigest(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(key.getBytes());
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            // Should not happen.
            return null;
        }
    }

    /**
     * Encrypt an input buffer with the key specified in the constructor.
     *
     * @param buffer the input buffer.
     * @return the encrypted output.
     * @throws UnsupportedEncodingException
     */
    public ByteBuffer encrypt(ByteBuffer buffer) {
        try {
            ByteBuffer result = ByteBuffer.allocate(buffer.capacity() + 16);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            cipher.doFinal(buffer, result);
            return result;
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException
                 | BadPaddingException | ShortBufferException e1) {
            // Should not happen
            return null;
        }
    }

    /**
     * Encrypt an input buffer with the key specified in the constructor.
     *
     * @param buffer the input buffer.
     * @return the encrypted output.
     * @throws UnsupportedEncodingException
     */
    public byte[] encryptV3(byte[] buffer) throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(buffer);
    }

    public byte[] encryptV5(byte[] buffer, byte[] iv, byte header[]) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(16 * 8, iv);
        Cipher cipher = Cipher.getInstance("aes/gcm/nopadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
        cipher.updateAAD(header);
        return cipher.doFinal(buffer);
    }

    /**
     * Decrypt an input buffer with the key specified in the constructor.
     *
     * @param buffer the input buffer.
     * @return the encrypted output.
     * @throws IllegalBlockSizeException
     * @throws UnsupportedEncodingException
     */
    public ByteBuffer decrypt(ByteBuffer buffer) {
        try {
            ByteBuffer result = ByteBuffer.allocate(buffer.capacity());
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher;
            cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            cipher.doFinal(buffer, result);
            return result;
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | ShortBufferException
                 | IllegalBlockSizeException | BadPaddingException e1) {
            // logger.error("Unexpected error when decrypting.", e1);
            return null;
        }
    }

    /**
     * Decrypt an input buffer with the key specified in the constructor.
     *
     * @param buffer the input buffer.
     * @return the encrypted output.
     * @throws IllegalBlockSizeException
     * @throws UnsupportedEncodingException
     */
    public byte[] decryptV3(byte[] buffer) throws IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(buffer);
    }

    public byte[] decryptV5(byte[] enc, byte[] iv, byte header[]) throws IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(16 * 8, iv);
        Cipher cipher = Cipher.getInstance("aes/gcm/nopadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
        cipher.updateAAD(header);
        return cipher.doFinal(enc);

    }
}
