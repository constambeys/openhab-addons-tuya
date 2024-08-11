/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.tuya.internal.discovery;

import org.openhab.binding.tuya.handler.AbstractTuyaHandler;
import org.openhab.binding.tuya.internal.annotations.Property;
import org.openhab.binding.tuya.internal.data.Version;

/**
 * Descriptor of the device in the repository.
 *
 * @author Wim Vissers.
 */
public class DeviceDescriptor {

    /**
     * The datagram retrieved from the UDP broadcast.
     */
    private JsonDiscovery jsonDiscovery;
    /**
     * The local encryption key must be set in the configuration. It is not transmitted by UDP.
     */

    private String devId;
    private String ip;
    private Version version;
    private String localKey;
    private AbstractTuyaHandler handler;

    public DeviceDescriptor() {
    }

    public DeviceDescriptor(JsonDiscovery jsonDiscovery) {
        this.devId = jsonDiscovery.getDevId();
        this.ip = jsonDiscovery.getIp();
        this.version = Version.parse(jsonDiscovery.getVersion());
    }

    @Property("id")
    public String getDevId() {
        return devId;
    }

    @Property("ip")
    public String getIp() {
        return ip;
    }

    @Property("version")
    public Version getVersion() {
        return version;
    }

    public String getLocalKey() {
        return localKey;
    }

    public DeviceDescriptor withLocalKey(String localKey) {
        this.localKey = localKey;
        return this;
    }

    public AbstractTuyaHandler getHandler() {
        return handler;
    }

    public void setHandler(AbstractTuyaHandler handler) {
        this.handler = handler;
    }
}
