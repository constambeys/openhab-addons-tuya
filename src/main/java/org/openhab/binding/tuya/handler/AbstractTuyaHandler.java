/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.handler;

import static org.openhab.binding.tuya.internal.data.CommandByte.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.openhab.binding.tuya.internal.CommandDispatcher;
import org.openhab.binding.tuya.internal.annotations.Property;
import org.openhab.binding.tuya.internal.data.CommandByte;
import org.openhab.binding.tuya.internal.data.DeviceState;
import org.openhab.binding.tuya.internal.data.Message;
import org.openhab.binding.tuya.internal.discovery.DeviceDescriptor;
import org.openhab.binding.tuya.internal.discovery.DeviceRepository;
import org.openhab.binding.tuya.internal.discovery.JsonDiscovery;
import org.openhab.binding.tuya.internal.exceptions.HandlerInitializationException;
import org.openhab.binding.tuya.internal.exceptions.UnsupportedVersionException;
import org.openhab.binding.tuya.internal.net.TcpConfig;
import org.openhab.binding.tuya.internal.net.TuyaClient;
import org.openhab.binding.tuya.internal.net.TuyaClient.Event;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonSyntaxException;

/**
 * The {@link AbstractTuyaHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Wim Vissers - Initial contribution
 */
public abstract class AbstractTuyaHandler extends BaseThingHandler implements TcpConfig {

    private final Logger logger = LoggerFactory.getLogger(AbstractTuyaHandler.class);

    protected String id;
    protected DeviceDescriptor deviceDescriptor;

    protected TuyaClient tuyaClient;
    protected final CommandDispatcher commandDispatcher;
    private ScheduledFuture<?> watchdog;

    public AbstractTuyaHandler(Thing thing) {
        super(thing);
        this.commandDispatcher = new CommandDispatcher(thing.getUID());
    }

    /**
     * Update the states of channels that are changed.
     */
    protected void updateStates(Message message, Class<? extends DeviceState> clazz) {
        try {
            DeviceState dev = message.toDeviceState(clazz);
            if (dev != null) {
                BiConsumer<String, State> handler = (channel, state) -> {
                    updateState(new ChannelUID(thing.getUID(), channel), state);
                };
                dev.forChangedProperties(handler);
            }
        } catch (JsonSyntaxException e) {
            logger.error("Message invalid", e);
            logger.debug("Message: {}", message.getData());
        }
    }

    /**
     * Return true if connected.
     *
     * @return
     */
    public boolean isOnline() {
        return tuyaClient != null && tuyaClient.isOnline();
    }

    /**
     * This method is called when a DeviceEventEmitter.Event.MESSAGE_RECEIVED is received from the device. In
     * subclasses, this should result in a possible state change of the things channels.
     */
    protected void handleMessage(Message message) {
        // When a message is received, the thing is ONLINE.
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * This method is called when the device is connected, for an initial status request if the device supports it.
     */
    protected void sendStatusQuery() {
        try {
            if (tuyaClient != null && tuyaClient.isStarted()) {
                tuyaClient.send(null, CommandByte.DP_QUERY);
            }
        } catch (Exception e) {
            logger.error("Error on status request", e);
        }
    }

    /**
     * Clean up resources on removal.
     */
    @Override
    public void handleRemoval() {
        cleanUp();
        super.handleRemoval();
    }

    /**
     * Cleanup for reinitializing or removing this handler.
     */
    private void cleanUp() {
        if (tuyaClient != null) {
            tuyaClient.stop();
            tuyaClient = null;
        }
        if (watchdog != null) {
            watchdog.cancel(true);
            watchdog = null;
        }
        if (deviceDescriptor != null && deviceDescriptor.getDevId() != null) {
            DeviceRepository.getInstance().removeHandler(deviceDescriptor.getDevId());
        }
        if (commandDispatcher != null) {
            commandDispatcher.removeAllHandlers();
        }
        deviceDescriptor = null;
    }

    /**
     * Dispose of allocated resources.
     */
    @Override
    public void dispose() {
        cleanUp();
        super.dispose();
    }

    /**
     * Update the properties that can be inspected with e.g. the Paper UI. May be
     * overridden in subclasses to add more specific device properties.
     */
    protected void updateProperties(boolean clear) {
        if (clear) {
            thing.setProperties(new HashMap<>());
        } else {
            for (Method method : DeviceDescriptor.class.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Property.class) && method.getParameterCount() == 0) {
                    Property prop = method.getAnnotation(Property.class);
                    thing.setProperty(prop.value(), "");

                    try {
                        Object obj = method.invoke(deviceDescriptor, (Object[]) null);
                        thing.setProperty(prop.value(), obj == null ? prop.nullValue() : obj.toString());
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        logger.error("Property value could not be retrieved", e);
                    }

                }
            }
        }
    }

    /**
     * Handle a device found by the discovery service. In particular, set or update the IP address.
     *
     * @param device the device descriptor, received from the DeviceRepository service.
     * @throws UnsupportedVersionException
     */
    private void deviceFound(DeviceDescriptor device) throws UnsupportedVersionException {
        if (device != null && device.getDevId().equals(id)) {
            if (deviceDescriptor == null || !deviceDescriptor.getIp().equals(device.getIp())) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "IP address: " + device.getIp());
                deviceDescriptor = device;
                updateProperties(false);
                deviceDescriptor.setHandler(this);
                thing.getConfiguration().put("ip", device.getIp());
                tuyaClient = new TuyaClient(device);

                // Handle error events
                tuyaClient.on(Event.CONNECTION_ERROR, (ev, msg) -> {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            msg == null ? "" : msg.getData());
                    return true;
                });

                // Handle connected event.
                tuyaClient.on(Event.CONNECTED, (ev, msg) -> {
                    updateStatus(ThingStatus.ONLINE);
                    updateProperties(false);
                    // Ask status after some delay to let the items be created first.
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            sendStatusQuery();
                        }
                    }, STATUS_REQUEST_DELAY_SECONDS, TimeUnit.SECONDS);
                    return true;
                });

                // Handle messages received.
                tuyaClient.on(Event.MESSAGE_RECEIVED, (ev, msg) -> {
                    if (msg.getCommandByte() == STATUS || msg.getCommandByte() == DP_QUERY) {
                        handleMessage(msg);
                    }
                    return true;
                });

                tuyaClient.start(scheduler);
            }

        }
    }

    /**
     * Handle specific commands for this type of device. Subclasses should initialize the command dispatcher with device
     * specific commands.
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (tuyaClient != null && tuyaClient.isStarted()) {
            if (command instanceof RefreshType) {
                sendStatusQuery();
            } else {
                if (!commandDispatcher.dispatchCommand(tuyaClient, channelUID, command, CONTROL)) {
                    logger.info("Command {} for channel {} could not be handled.", command, channelUID);
                }
            }
        }
    }

    /**
     * Subclasses should add the commands to the dispatcher.
     */
    protected void initCommandDispatcher() {
    }

    @Override
    public void initialize() {

        // Dispose of allocated resources when re-initializing.
        cleanUp();

        // Get the configuration object.
        Configuration config = thing.getConfiguration();

        // Clear properties.
        updateProperties(true);

        id = config.get("id").toString();
        String localKey = config.get("key").toString();
        String version = config.get("version").toString();
        String ip = (String) config.get("ip");

        // If ip-address is specified, try to use it.
        if (ip != null && !ip.isEmpty()) {
            try {
                deviceFound(new DeviceDescriptor(new JsonDiscovery(id, version, ip)).withLocalKey(localKey));
            } catch (UnsupportedVersionException e) {
                throw new HandlerInitializationException(e.getMessage());
            }
        }

        // Initialize auto-discovery of the ip-address.
        try {
            DeviceRepository.getInstance().on(id, (ev, device) -> {
                try {
                    deviceFound(device.withLocalKey(localKey));
                } catch (UnsupportedVersionException e) {
                    throw new HandlerInitializationException(e.getMessage());
                }
                return true;
            });
        } catch (Exception e) {
            throw new HandlerInitializationException("Device ID already assigned to a Tuya thing.");
        }

        // Init dispatcher.
        initCommandDispatcher();

        /*
        // Start the watchdog to reinitialize when offline.
        startWatchdog();
        */
    }

    private void startWatchdog() {
        if (watchdog == null) {
            watchdog = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (getThing().getStatus() != ThingStatus.ONLINE) {
                        initialize();
                    }
                }
            }, WATCHDOG_CHECK_SECONDS, WATCHDOG_CHECK_SECONDS, TimeUnit.SECONDS);
        }
    }
}
