/*
 * Copyright 2016, Yahoo Inc.
 * Copyrights licensed under the New BSD License.
 * See the accompanying LICENSE file for terms.
 */

package com.yahoo.viper;

/**
 * These constants are used by the host monitor to determine how live hosts are returned to the client.
 */
public enum LoadBalancingPolicy {
    /**
     * Live hosts are returned in round-robin fashion, based on the order of the hosts.
     */
    ROUND_ROBIN,

    /**
     * The first live host from the original host list is returned.
     */
    FIRST_LIVE,

    /**
     * A random live host is returned.
     */
    RANDOM
}
