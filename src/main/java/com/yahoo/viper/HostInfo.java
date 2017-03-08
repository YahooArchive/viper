/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper;

import java.net.*;
import java.security.InvalidParameterException;

/**
 * This class holds information about each registered host.
 */
public class HostInfo {
    final ExceptionLogger logger = new ExceptionLogger(HostInfo.class);

    // Non-null name.
    public String name;

    // If null, the port is value.
    public URL url;

    // Valid if url is null.
    public int port = -1;

    // The DNS-resolved value of name and port.
    InetSocketAddress socketAddress;

    // If true, indicates that the last check succeeded.
    boolean live;

    // If not null, refers to the checker that is currently checking this host.
    CheckTask checkTask;

    // The time of the last check. It is set just before the check is attempted.
    long lastCheck;

    // The time of the last successful check. The host is considered live if this time is later than now-timeout.
    long lastLive;

    // Each failure increments this count.
    int failedChecks;

    /**
     * This check reads the contents from the specified URL. The check is considered successful only if
     * a status of 200 is returned.
     *
     * @param url a non-null URL to perform a check.
     */
    public HostInfo(String url) throws UnknownHostException, MalformedURLException {
        if (url == null) {
            throw new InvalidParameterException("url must not be null");
        }
        this.url = new URL(url);
        this.name = this.url.getHost();
    }

    /**
     * This check creates a socket connection to the specified host and port.
     * Although a reader and writer are also created, no information will
     * be written or read.
     *
     * @param port The port to connect to.
     */
    public HostInfo(String name, int port) throws UnknownHostException {
        this.name = name;
        this.port = port;
        this.socketAddress = new InetSocketAddress(InetAddress.getByName(name), port);
    }

    /**
     * Returns true if the most recently completed check was successful.
     * In the case of a hung checker, false is returned.
     *
     * @return true if the host is live.
     */
    public boolean isLive() {
        if (live && checkTask.isChecking()) {
            long time = System.currentTimeMillis() - lastLive();
            // The threshold is increased by one check period to allow time for the check itself
            if (time > (checkTask.monitor.retries + 2) * checkTask.monitor.checkPeriodMs) {
                live = false;
                logger.info(String.format("[%s] %s check is taking over %d ms. Marking this host unavailable.",
                        checkTask.monitor.name, url == null ? socketAddress : url, time));
            }
        }
        return live;
    }

    /**
     * Returns true if the host is hung. A host is considered hung if the check has taken longer than
     * (retries+1) * checkPeriod.
     *
     * @return true if the host is hung.
     */
    public boolean isHung() {
        return System.currentTimeMillis() - lastCheck() > (checkTask.monitor.retries + 2) * checkTask.monitor.checkPeriodMs;
    }

    /**
     * The time of the last health check. Before the first check, this
     * field is 0.
     *
     * @return the unix time of the last check.
     */
    public long lastCheck() {
        return lastCheck;
    }

    public int failedChecks() {
        return failedChecks;
    }

    /**
     * The most recent time that the host was found live. Before the
     * first time the host is found to be live, this field is 0.
     *
     * @return the unix time of the most recent time that the host was found live.
     */
    public long lastLive() {
        return lastLive;
    }

    /**
     * Returns the socket address of the supplied host and port.
     *
     * @return a possibly null socket address. Null is returned if this object was constructed with a URL.
     */
    public InetSocketAddress socketAddress() {
        return socketAddress;
    }

    /**
     * Returns a string representation of this host. For debugging purposes.
     *
     * @return Non-null string
     */
    @Override
    public String toString() {
        return String.format("HostInfo[%s, live=%b, lastLive=%dms, lastCheck=%dms]",
                url == null ? socketAddress : url, isLive(),
                lastLive == 0 ? 0 : lastLive - System.currentTimeMillis(),
                lastCheck - System.currentTimeMillis());
    }
}
