/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper;

import com.yahoo.viper.cli.MockServer;
import com.yahoo.viper.util.Utils;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * This is the TestNG suite of HostMonitor unit tests.
 */
public class HostMonitorTest {
    MockServer[] mockServers = new MockServer[3];
    int checkPeriodMs = 50;

    @BeforeTest
    public void beforeTest() throws Exception {
        // Create the mock servers that will be monitored
        for (int i = 0; i < mockServers.length; i++) {
            mockServers[i] = new MockServer(5000 + i);
            mockServers[i].start();
        }
    }

    @BeforeMethod
    public void beforeMethod() throws Exception {
        // Reset the mock server's status
        for (int i = 0; i < mockServers.length; i++) {
            mockServers[i].setMode(MockServer.Mode.UP);
        }
    }

    @AfterTest
    public void afterTest() throws Exception {
        for (MockServer s : mockServers) {
            s.close();
        }
    }

    public HostMonitor createMonitor(LoadBalancingPolicy policy, int checkPeriodMs) throws Exception {
        List<HostInfo> hinfos = Arrays.asList(
                new HostInfo("localhost", 5000),
                new HostInfo("localhost", 5001),
                new HostInfo("http://localhost:5002"));

        HostMonitor monitor = new HostMonitor(Thread.currentThread().getStackTrace()[2].getMethodName(),
                hinfos, policy, checkPeriodMs, 0);

        int liveCount = 0;
        for (MockServer s : mockServers) {
            if (s.getMode() == MockServer.Mode.UP) {
                liveCount++;
            }
        }

        // Wait until all three servers are live. Up to 5 seconds.
        long start = Utils.getActualTime();
        int count = 0;
        while (count < liveCount && Utils.getActualTime() - start < 10 * checkPeriodMs) {
            count = 0;
            for (HostInfo hi : hinfos) {
                if (hi.isLive()) {
                    count++;
                }
            }
            Thread.sleep(checkPeriodMs);
        }
        if (count < liveCount) {
            throw new IllegalStateException("All hosts have not become live within 10 X the check period");
        }
        return monitor;
    }

    /**
     * Check that creating a HostInfo with an invalid name, fails.
     */
    @Test
    public void invalidName() throws Exception {
        try {
            new HostInfo("a.b.c.d", 80);
            Assert.fail("An invalid host name did not fail");
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    /**
     * Create a valid HostMonitor using public hosts.
     */
    @Test
    public void watcher() throws Exception {
        List<HostInfo> hinfos = Arrays.asList(
                new HostInfo("https://www.yahoo.com"),
                new HostInfo("https://www.google.com"),
                new HostInfo("http://example.com"));
        HostMonitor watcher = new HostMonitor("watcher", hinfos, LoadBalancingPolicy.ROUND_ROBIN, 1000, 0);
        try {
            HostInfo hi = watcher.liveHost();
            Assert.assertNotNull(hi);
        } finally {
            watcher.close();
        }
    }

    @Test
    public void testServer() throws Exception {
        HostMonitor watcher = createMonitor(LoadBalancingPolicy.FIRST_LIVE, checkPeriodMs);
        try {
            HostInfo hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getHostString(), "localhost");
            Assert.assertEquals(hi.socketAddress.getPort(), 5000);

            hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getHostString(), "localhost");
            Assert.assertEquals(hi.socketAddress.getPort(), 5000);
        } finally {
            watcher.close();
        }
    }


    @Test
    public void roundRobin() throws Exception {
        HostMonitor watcher = createMonitor(LoadBalancingPolicy.ROUND_ROBIN, checkPeriodMs);
        try {
            // first
            HostInfo hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getHostString(), "localhost");
            Assert.assertEquals(hi.socketAddress.getPort(), 5000);

            // second
            hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getHostString(), "localhost");
            Assert.assertEquals(hi.socketAddress.getPort(), 5001);
        } finally {
            watcher.close();
        }
    }

    /**
     * Make the first host down so the second host will be returned.
     *
     * @throws Exception
     */
    @Test
    public void testServerWithDown() throws Exception {
        mockServers[0].setMode(MockServer.Mode.DOWN);
        HostMonitor watcher = createMonitor(LoadBalancingPolicy.FIRST_LIVE, checkPeriodMs);
        try {
            HostInfo hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getPort(), 5001);
        } finally {
            watcher.close();
        }
    }

    /**
     * Make the first host hang so the second host will be returned.
     *
     * @throws Exception
     */
    @Test
    public void testServerWithHang() throws Exception {
        mockServers[0].setMode(MockServer.Mode.HANG);
        HostMonitor watcher = createMonitor(LoadBalancingPolicy.FIRST_LIVE, checkPeriodMs);
        try {
            HostInfo hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getPort(), 5001);
        } finally {
            watcher.close();
        }
    }

    /**
     * Make the first host down and the bring it back up again.
     *
     * @throws Exception
     */
    @Test
    public void testRecovery() throws Exception {
        mockServers[0].setMode(MockServer.Mode.DOWN);
        HostMonitor watcher = createMonitor(LoadBalancingPolicy.FIRST_LIVE, checkPeriodMs);
        try {
            // Expect second host
            HostInfo hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getPort(), 5001);

            // Recover first host
            mockServers[0].setMode(MockServer.Mode.UP);
            Thread.sleep(checkPeriodMs * 10);

            // Get first host
            hi = watcher.liveHost();
            Assert.assertEquals(hi.socketAddress.getPort(), 5000);
        } finally {
            watcher.close();
        }
    }
}
