package com.yahoo.viper;

/**
 * This class holds information about a monitoring event. Monitoring events are generated every time a host
 * goes up or down.
 */
public class HostMonitorEvent {
    /**
     * A reference to the host monitor object that generated this event.
     */
    public HostMonitor hostMonitor;

    /**
     * The current number of hosts that are live.
     */
    public int numLiveHosts;

    /**
     * A human-readable message that informs of the current state of the hosts. If any hosts are non-live,
     * the message will include those hosts.
     */
    public String message;
}
