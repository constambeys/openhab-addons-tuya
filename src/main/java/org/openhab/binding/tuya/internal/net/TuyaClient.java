/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.net;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static org.openhab.binding.tuya.internal.data.CommandByte.SESS_KEY_NEG_FINISH;
import static org.openhab.binding.tuya.internal.data.CommandByte.SESS_KEY_NEG_START;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.openhab.binding.tuya.internal.data.CommandByte;
import org.openhab.binding.tuya.internal.data.DeviceState;
import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.binding.tuya.internal.discovery.DeviceDescriptor;
import org.openhab.binding.tuya.internal.exceptions.ParseException;
import org.openhab.binding.tuya.internal.util.MessageParser;
import org.openhab.binding.tuya.internal.util.SingleEventEmitter;
import org.openhab.binding.tuya.internal.util.TuyaCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TuyaClient is a TCP client implementation for communicating with a single device. Please use the factory method in
 * TuyaClientService to obtain a client. It will be automatically registered with the service and serviced.
 *
 * @author Wim Vissers.
 */
public class TuyaClient extends SingleEventEmitter<TuyaClient.Event, Message, Boolean> implements TcpConfig {

    // The message parser is to encode/decode messages. It is dedicated to a
    // single device, since the localKey is different from device to device.
    private MessageParser messageParser;

    // The sequence number of messages sent to the device.
    private long currentSequenceNo;

    // The queue for outgoing messages.
    private final LinkedBlockingQueue<QueueItem> queue;

    // The selection key.
    private SelectionKey key;

    // The heartbeat task.
    private ScheduledFuture<?> heartbeat;

    // Count heartbeats that have not been acknowledged yet.
    private final AtomicInteger heartbeatCnt;

    // Count retries when connection is reset by peer.
    private final AtomicInteger retryCnt;

    private DeviceDescriptor device;


    private boolean online;
    private final Logger logger;

    /**
     * Create a new TuyaClient with the given parameters.
     */
    public TuyaClient(DeviceDescriptor device) {
        logger = LoggerFactory.getLogger(this.getClass());

        this.device = device;
        this.heartbeatCnt = new AtomicInteger(0);
        this.retryCnt = new AtomicInteger(0);

        this.queue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_SIZE);
        this.messageParser = new MessageParser(device.getVersion(), device.getLocalKey());
    }

    /**
     * Start this client. It will be registered to the TuyaClientService. The scheduler will be used for repetitive or
     * short running tasks.
     *
     * @param scheduler the scheduler.
     */
    public synchronized void start(ScheduledExecutorService scheduler) {
        try {
            connect();
        } catch (Exception ex) {
            online = false;
            emit(Event.CONNECTION_ERROR, new Message(ex));
        }
        if (heartbeat == null) {
            heartbeat = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        send(new DeviceState(device.getGwId()), CommandByte.HEART_BEAT);
                    } catch (Exception e) {
                        logger.error("Cannot send command HEART_BEAT", e);
                    }
                }
            }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Stop the client.
     */
    @Override
    public synchronized void stop() {
        online = false;
        if (heartbeat != null) {
            heartbeat.cancel(true);
            heartbeat = null;
        }

        if (key != null) {
            close(key.channel());
            key.cancel();
        }

        super.stop();
    }

    private synchronized SocketChannel negotiate_session_key() throws Exception {

        SocketChannel channel = null;

        byte dataToSend[];

        byte[] local_nonce = "0123456789abcdef".getBytes();

        MessageParser messageParser = new MessageParser(device.getVersion(), device.getLocalKey());

        dataToSend = messageParser.encode(local_nonce, SESS_KEY_NEG_START, currentSequenceNo++);
        Message msg1 = null;

        for (int i = 0; i < 3; i++) {
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress(device.getIp(), DEFAULT_SERVER_PORT));
            channel.write(ByteBuffer.wrap(dataToSend));
            ByteBuffer bb = ByteBuffer.allocate(102);
            int bytesRead = channel.read(bb);
            if (bytesRead == 102) {
                msg1 = messageParser.decode(bb.array());
                break;
            } else {
                Thread.sleep(10);
            }
        }

        byte[] remote_nonce = Arrays.copyOfRange(msg1.getRawData(), 0, 16);

        SecretKeySpec secretKeySpec = new SecretKeySpec(device.getLocalKey().getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmac_check1 = mac.doFinal(local_nonce);
        byte[] hmac_check2 = Arrays.copyOfRange(msg1.getRawData(), 16, 48);

        if (!Arrays.equals(hmac_check1, hmac_check2)) {
            throw new Exception("HMAC check failed.");
        }

        byte[] rkey_hmac = mac.doFinal(remote_nonce);
        dataToSend = messageParser.encode(rkey_hmac, SESS_KEY_NEG_FINISH, currentSequenceNo++);
        channel.write(ByteBuffer.wrap(dataToSend));

        byte[] xorKey = new byte[local_nonce.length];
        for (int i = 0; i < xorKey.length; i++) {
            xorKey[i] = (byte) (local_nonce[i] ^ remote_nonce[i]);
        }

        TuyaCipher cipher = new TuyaCipher(device.getLocalKey().getBytes(StandardCharsets.UTF_8));

        byte[] newKeyAll = cipher.encryptV5(xorKey, Arrays.copyOfRange(local_nonce, 0, 12), new byte[0]);
        byte[] newKey = Arrays.copyOfRange(newKeyAll, 0, 16);

        this.messageParser = new MessageParser(device.getVersion(), newKey);

        return channel;
    }

    /**
     * Connect the client and register to the client service.
     *
     * @throws IOException
     */
    private synchronized void connect() throws Exception {
        if (device.getVersion().equals("3.5")) {
            SocketChannel channel = negotiate_session_key();
            key = TuyaClientService.getInstance().register(this, channel);
            heartbeatCnt.set(0);
            handleConnect(key);
        } else {
            key = TuyaClientService.getInstance().register(this, device.getIp(), DEFAULT_SERVER_PORT);
            heartbeatCnt.set(0);
        }
    }

    /**
     * Send a message. If the device responds, the response will be emitted as a new event.
     *
     * @param item the item to send.
     * @throws IOException
     * @throws ParseException
     */
    private void send(QueueItem item) throws Exception {
        if (!online || key == null) {
            if (key != null) {
                close(key.channel());
            }
            connect();
        }
        CommandByte command = item.getCommandByte();
        if (command.equals(CommandByte.HEART_BEAT) && queue.remainingCapacity() < DEFAULT_QUEUE_SIZE / 2) {
            heartbeatCnt.set(OUTSTANDING_HEARTBEATS_LIMIT);
            logger.debug("Skipping heartbeat since outstanding heartbeat > {}.", OUTSTANDING_HEARTBEATS_LIMIT);
        } else if (queue.remainingCapacity() == 0) {
            if (online) {
                online = false;
                emit(Event.CONNECTION_ERROR, new Message("send queue overflow"));
            }
        } else {
            // Remove conflicting items from the queue.
            queue.removeIf(qi -> qi.isConflicting(item));
            queue.offer(item);
            if (command.equals(CommandByte.HEART_BEAT)) {
                if (heartbeatCnt.incrementAndGet() > HEARTBEAT_RETRIES) {
                    online = false;
                    emit(Event.CONNECTION_ERROR, new Message("no response to heartbeat"));
                }
            }
            key.interestOps(OP_WRITE);
        }
    }

    /**
     * Send a message. If the device responds, the response will be emitted as a new event.
     *
     * @param deviceState the deviceState object that will be transformed to a json string.
     * @param command     the commandbyte enum constant.
     * @throws IOException
     * @throws ParseException
     */
    public void send(DeviceState deviceState, CommandByte command) throws Exception {
        send(new QueueItem(deviceState, command));
    }

    /**
     * Called by the service when connected.
     *
     * @param key the selection key.
     */
    void handleConnect(SelectionKey key) {
        if (this.key == key) {
            online = true;
            retryCnt.set(0);
            emit(Event.CONNECTED, null);
        }
    }

    /**
     * Called by the service when disconnected.
     *
     * @param key the selection key.
     * @param ex  the IOException (may by null).
     */
    void handleDisconnect(SelectionKey key, IOException ex) {
        logger.debug("Disconnected.", ex);
        if (key != null) {
            close(key.channel());
            key.cancel();
            this.key = null;
        }
        online = false;
        if (ex == null) {
            emit(Event.DISCONNECTED, null);
        } else {
            if (retryCnt.addAndGet(1) < MAX_RETRIES) {
                logger.debug("Connection error in retry window.");
                emit(Event.CONNECTION_ERROR_WITHIN_RETRY, new Message(ex));
                try {
                    // Wait a short time between retries
                    Thread.sleep(RETRY_DELAY);
                    send(queue.poll());
                } catch (Exception e) {
                }
            } else {
                // Remove the message from the queue after max retries.
                logger.debug("Connection error exceeds retries, cancel request.");
                retryCnt.set(0);
                queue.poll();
                emit(Event.CONNECTION_ERROR, new Message(ex));
            }
        }
    }

    /**
     * Called by the service when data arrived.
     *
     * @param key  the selection key.
     * @param data the raw data bytes.
     */
    void handleData(SelectionKey key, byte[] data) {
        logger.debug("Incoming message from {} with data {}", key, data);
        try {
            Message message = messageParser.decode(data);
            if (message.getCommandByte().equals(CommandByte.HEART_BEAT)) {
                if (heartbeatCnt.intValue() > 0) {
                    heartbeatCnt.decrementAndGet();
                }
            }
            emit(Event.MESSAGE_RECEIVED, message);
        } catch (Exception e) {
            logger.error("Invalid message received.", e);
        }
        queue.poll();
        if (!queue.isEmpty() && key != null) {
            key.interestOps(OP_WRITE);
        }
    }

    /**
     * Return true if running and connected.
     *
     * @return
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Called by the service when ready for writing.
     *
     * @param key the selection key.
     */
    void writeData(SelectionKey key) {
        logger.debug("Write data requested for channel {}.", key.channel());
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            if (!queue.isEmpty()) {
                // Use peek to leave the message in the queue.
                channel.write(ByteBuffer.wrap(queue.peek().encode(messageParser, currentSequenceNo++)));
            }
        } catch (Exception e) {
            logger.debug("Exception in writeData.", e);
            if (retryCnt.addAndGet(1) >= MAX_RETRIES) {
                queue.poll();
                retryCnt.set(0);
            }
            return;
        }
        key.interestOps(OP_READ);
    }

    public enum Event {
        CONNECTION_ERROR,
        CONNECTION_ERROR_WITHIN_RETRY,
        CONNECTED,
        DISCONNECTED,
        MESSAGE_RECEIVED;
    }
}
