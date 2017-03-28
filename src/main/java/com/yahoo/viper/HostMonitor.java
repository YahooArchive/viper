/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.viper.util.Utils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * This is the main class used to monitor a set of hosts. See the README for usage information.
 */
public class HostMonitor {

    final private static Logger logger = LoggerFactory.getLogger(HostMonitor.class);
    final private static ExceptionLogger excLogger = new ExceptionLogger(HostMonitor.class);
    final private LoadBalancingPolicy loadBalancingPolicy;
    final private ExecutorService checkerPool;

    // This value is used to prevent false errors during the start up of this instance
    final private long startTime = Utils.getActualTime();

    // Used by other classes in this package
    final private List<HostInfo> hinfos;
    final int retries;
    final String name;
    final int checkPeriodMs;
    boolean showFullStackTraces = false;

    // The background thread continues to run while this is true
    volatile private boolean runBgThread = true;

    // An index used to implement the round-robin policy
    private AtomicInteger roundRobinIx = new AtomicInteger();

    // Number of host info.
    private int numHosts;

    // The number of hosts that are live. Updated by the bg thread
    private volatile int liveCount;

    List<Consumer<HostMonitorEvent>> listeners = new ArrayList<>();

    /**
     * Monitors the specified list of hosts. All hosts will be initially unavailable. The true host status
     * will be available no later than the specified check period.
     *
     * @param name                Non-null string that is displayed with all log entries from this instance.
     * @param hinfos              Non-null list of hosts to monitor.
     * @param loadBalancingPolicy Determines which live host is returned.
     * @param checkPeriodMs       The frequency of checking the host. In Milliseconds.
     * @param retries             The number of failed checks before the host is considered down. Set to 0 if
     *                            the host should be down with any failed check.
     */
    public HostMonitor(String name, List<HostInfo> hinfos, LoadBalancingPolicy loadBalancingPolicy,
                       int checkPeriodMs, int retries) {
        this.name = name;
        this.hinfos = hinfos;
        this.loadBalancingPolicy = loadBalancingPolicy;
        this.checkPeriodMs = checkPeriodMs;
        this.retries = retries;
        this.checkerPool = Executors.newFixedThreadPool(hinfos.size());
        numHosts = hinfos.size();
        liveCount = 0;

        // Create the check tasks
        long now = Utils.getActualTime();
        for (HostInfo hi : hinfos) {
            hi.checkTask = new CheckTask(this, hi);
            hi.lastCheck = hi.lastLive = 0;
        }

        new BgThread().start();
    }

    /**
     * Registers a listener for monitoring events. A monitoring event is generated every time a
     * host goes up or down.
     *
     * @param listener non-null listener for monitoring events.
     */
    public void registerForEvents(Consumer<HostMonitorEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Used primarily to debug issues. When enabled, all the exceptions encountered by checking a host
     * will show up in the logs as full ERROR stack traces.
     *
     * @param enable Set to true to show full stack traces.
     */
    public void setShowFullStackTraces(boolean enable) {
        showFullStackTraces = enable;
    }

    /**
     * Returns the list of host information objects that was supplied to the constructor.
     *
     * @return non-null list of host information objects.
     */
    public List<HostInfo> hostInfos() {
        return hinfos;
    }

    /**
     * Releases all resources used by this object. The object is no longer usable after this call.
     * This call may block up to twice the check period as specified in the constructor.
     */
    public void close() throws InterruptedException {
        runBgThread = false;
        checkerPool.shutdownNow();
    }

    /**
     * The live host returned depends on the loadBalancingPolicy.
     * If this instance is created at time T, then this call may block until T + checkPeriodMs,
     * waiting for a live host.
     *
     * @return null if there are no live hosts.
     */
    public HostInfo liveHost() {
        HostInfo hi = null;
        // If no live hosts are found and this instance was just created, try again
        try {
            while ((hi = liveHost2()) == null && Utils.getActualTime() - startTime < 2 * checkPeriodMs) {
                Thread.sleep(checkPeriodMs);
            }
        } catch (InterruptedException e) {
            // Do nothing since an interrupted sleep should return null
        }
        return hi;
    }

    private HostInfo liveHost2() throws InterruptedException {
        switch (loadBalancingPolicy) {
            case FIRST_LIVE:
                for (HostInfo hi : hinfos) {
                    if (hi.isLive()) {
                        return hi;
                    }
                }
                return null;
            case ROUND_ROBIN:
                for (int i = 0; i < numHosts; i++) {
                    int n = roundRobinIx.getAndIncrement();
                    HostInfo hi = hinfos.get(n % numHosts);
                    if (hi.isLive()) {
                        System.out.println("returning " + hi);
                        return hi;
                    }
                }
                return null;
            case RANDOM:
                while (liveCount > 0) {
                    HostInfo hi = hinfos.get((int) (Math.random() * numHosts));
                    if (hi.isLive()) {
                        return hi;
                    }
                }
                return null;
        }
        throw new IllegalStateException();
    }

    /*
     * This thread is used to look for hung checker threads.
     * If one is determined to be hung, it is interrupted.
     */
    class BgThread extends Thread {
        long now = Utils.getActualTime();
        long lastInfo = now;

        public void run() {
            int numChecks = 0;
            int lastLives = -1;
            int lastNumListeners = 0;
            HostInfo[] temp = new HostInfo[hinfos.size()];

            StringBuilder sb = new StringBuilder();
            while (runBgThread) {
                try {
                    long now = Utils.getActualTime();
                    int lives = 0;

                    // Start another round of checks and tally the live hosts
                    for (int i = 0; i < temp.length; i++) {
                        HostInfo hi = hinfos.get(i);
                        if (hi.isLive()) {
                            lives++;
                            temp[i] = null;
                        } else {
                            if (hi.isHung()) {
                                hi.checkTask.cancel();
                            }
                            temp[i] = hi;
                        }
                        try {
                            checkerPool.submit(hi.checkTask);
                        } catch (RejectedExecutionException e) {
                            // Ignore rejected exceptions if the executor service is being shut down.
                            if (runBgThread) {
                                throw e;
                            } else if (showFullStackTraces) {
                                logger.error("Failed to check " + hi, e);
                            }
                        }
                    }
                    // Update instance values
                    liveCount = lives;

                    now = Utils.getActualTime();
                    if (lives != lastLives || lastNumListeners != listeners.size() || now - lastInfo > 60000) {
                        HostMonitorEvent event = new HostMonitorEvent();
                        event.numLiveHosts = lives;

                        // Add any non-live hosts
                        sb.setLength(0);
                        for (int i = 0; i < temp.length; i++) {
                            if (temp[i] != null) {
                                HostInfo hi = temp[i];
                                sb.append(hi.url == null ? hi.socketAddress : hi.url);
                                if (hi.isHung()) {
                                    sb.append("(hung)");
                                }
                                sb.append(" ");
                            }
                        }
                        event.hostMonitor = HostMonitor.this;
                        if (liveCount == 0) {
                            event.message = String.format("[%s] All %d hosts are unavailable: %s",
                                    name, numHosts, sb.toString());
                            if (now - startTime < checkPeriodMs) {
                                // Avoid logging an error during start up, to avoid triggering an alert
                                logger.info(event.message);
                            } else {
                                logger.error(event.message);
                            }
                        } else if (liveCount < numHosts) {
                            event.message = String.format("[%s] %d out of %d hosts are unavailable: %s",
                                    name, numHosts - liveCount, numHosts, sb.toString());
                            logger.warn(event.message);
                        } else {
                            event.message = String.format("[%s] All hosts are up. (period=%dms)", name, checkPeriodMs);
                            logger.info(event.message + ". (" + numChecks + " checks)");
                        }

                        // Notify listeners
                        if (lives != lastLives || lastNumListeners != listeners.size()) {
                            for (Consumer<HostMonitorEvent> listener : listeners) {
                                listener.accept(event);
                            }
                        }

                        lastLives = lives;
                        lastNumListeners = listeners.size();
                        lastInfo = now;
                        numChecks = 0;
                    }

                    Thread.sleep(checkPeriodMs);
                } catch (Throwable e) {
                    excLogger.error(e.getMessage(), e);
                }
                numChecks++;
            }
        }
    }
}
