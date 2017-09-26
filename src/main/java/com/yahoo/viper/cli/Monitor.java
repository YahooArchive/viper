/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper.cli;

import com.yahoo.viper.HostInfo;
import com.yahoo.viper.HostMonitor;
import com.yahoo.viper.LoadBalancingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the "monitor" command line tool. See the readme for usage information.
 */
public class Monitor {
    final static Logger logger = LoggerFactory.getLogger(Monitor.class);

    /**
     * The main entry point for the monitor command line tool.
     *
     * @param args non-null array of arguments passed to the tool.
     * @throws Exception {@link Exception}
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: monitor <host:port|url> [<host:port|url>]*");
            return;
        }

        List<HostInfo> hosts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            HostInfo hi;
            if (args[i].matches("\\d+")) {
                hi = new HostInfo("localhost", Integer.parseInt(args[i]));
            } else if (args[i].startsWith("http")) {
                hi = new HostInfo(args[i]);
            } else if (args[i].matches("^\\S+:\\d+$")) {
                String[] ss = args[i].split(":");
                hi = new HostInfo(ss[0], Integer.parseInt(ss[1]));
            } else {
                System.err.printf("Invalid host: %s\n", args[i]);
                return;
            }
            hosts.add(hi);
        }
        HostMonitor monitor = new HostMonitor("MonitorCmd", hosts, LoadBalancingPolicy.ROUND_ROBIN, 500, 0);

        // Register listener
        monitor.registerForEvents(event -> {
            if (event.numLiveHosts == 0) {
                logger.error("monitor: {}", event.message);
            } else if (event.numLiveHosts < hosts.size()) {
                logger.warn("monitor: {}", event.message);
            } else {
                logger.info("monitor: {}", event.message);
            }
        });

        // Keep displaying the found live host
        while (true) {
            HostInfo hi = monitor.liveHost();
            if (hi == null) {
                logger.info("monitor: no live hosts");
            } else {
                logger.info("monitor: host {} is live",
                        hi.url == null ? hi.socketAddress() : hi.url);
            }
            Thread.sleep(10000);
        }
    }

    private static String label(HostInfo hi) {
        if (hi.url != null) {
            return hi.url.toString();
        }
        return hi.name + ":" + hi.port;
    }
}
