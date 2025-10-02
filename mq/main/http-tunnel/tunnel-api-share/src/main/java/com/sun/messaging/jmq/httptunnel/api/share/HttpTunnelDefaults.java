/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.messaging.jmq.httptunnel.api.share;

/**
 * Protocol constants, packet types, default values etc.
 */
public interface HttpTunnelDefaults {

    //
    // VARIOUS DEFAULT VALUES :
    //

    /**
     * Default listening port for the TCP connection between the servlet and the <code>HttpTunnelServerDriver</code>.
     */
    int DEFAULT_HTTP_TUNNEL_PORT = 7675;
    int DEFAULT_HTTPS_TUNNEL_PORT = 7674;

    /**
     * Default connection retry attempt interval for the TCP connection between the servlet and the
     * <code>HttpTunnelServerDriver</code>.
     */
    int CONNECTION_RETRY_INTERVAL = 5000;

    /**
     * Default max connection retry wait for re-establish TCP connection from HttpTunnelServerDriver with the servlet
     */
    int MAX_CONNECTION_RETRY_WAIT = 900000;

    /**
     * Inactive connection abort interval.
     *
     * In 'continuous pull mode' (pullPeriod &lt;= 0) the connection is aborted if the servlet does not receive a pull
     * request for more than DEFAULT_CONNECTION_TIMEOUT_INTERVAL seconds.
     *
     * If pullPeriod is greater than 0, the connection is aborted if the servlet does not receive a pull request for more
     * than (5 * pullPeriod) seconds.
     */
    int DEFAULT_CONNECTION_TIMEOUT_INTERVAL = 60;

    /**
     * Maximum blocking period for HTTP pull requests in continuous pull mode.
     */
    int MAX_PULL_BLOCK_PERIOD = 60 * 1000;

    /**
     * Transmit window size (number of packets).
     */
    int DEFAULT_WINDOW_SIZE = 64;

    /**
     * Maximum data bytes per packet.
     */
    int MAX_PACKETSIZE = 8192;

    /**
     * Initial packet retransmission period.
     */
    int INITIAL_RETRANSMIT_PERIOD = 15000;

    /**
     * Minimum limit on measured retranmission timeout (based on round trip delay).
     */
    int MIN_RETRANSMIT_PERIOD = 1000;

    /**
     * Maximum limit on retransmission period binary exponential backoff.
     */
    int MAX_RETRANSMIT_PERIOD = 3 * 60 * 1000;

    /**
     * Number of repeat acknowledgements before a fast retransmit.
     */
    int FAST_RETRANSMIT_ACK_COUNT = 3;

    boolean ONE_PACKET_PER_REQUEST = false;

    //
    // PACKET TYPES :
    //

    /**
     * Packet type : Connection initiation request.
     */
    int CONN_INIT_PACKET = 1;

    /**
     * Packet type : Connection initiation acknowledgement.
     */
    int CONN_INIT_ACK = 2;

    /**
     * Packet type : Connection rejected.
     */
    int CONN_REJECTED = 3;

    /**
     * Packet type : Application data.
     */
    int DATA_PACKET = 4;

    /**
     * Packet type : Connection close request.
     */
    int CONN_CLOSE_PACKET = 5;

    /**
     * Packet type : Acknowledgement.
     */
    int ACK = 6;

    /**
     * Packet type : Cleanup connection table resources at the servlet.
     */
    int CONN_SHUTDOWN = 7;

    /**
     * Packet type : Link initialization information from the <code>HttpTunnelServerDriver</code> to the servlet. The
     * payload contains the connection table information. When the web server restarts, this is the first packet received by
     * the servlet so that it can restore its connection table.
     */
    int LINK_INIT_PACKET = 8;

    /**
     * Packet type : Connection aborted notification.
     */
    int CONN_ABORT_PACKET = 9;

    /**
     * Packet type : Connection aborted notification.
     */
    int CONN_OPTION_PACKET = 10;

    /**
     * Packet type : Listen state change notifications (server to servlet)
     */
    int LISTEN_STATE_PACKET = 11;

    /**
     * Packet type : No-op filler packet. Used as payload for empty responses.
     */
    int NO_OP_PACKET = 12;

    /**
     * Packet type : Test packet.
     */
    int DUMMY_PACKET = 100;

    //
    // CONNECTION OPTION TYPES :
    //

    /**
     * Connection option : Pull request period. By default connections operate in 'continuous pull mode'. Since this can hog
     * web server resources, it is advisable to use a positive 'pullPeriod' value. This value is used by the client as a
     * delay (in seconds) between pull requests, when the connection is idle.
     */
    int CONOPT_PULL_PERIOD = 1;

    /**
     * Connection option : Connection timeout. If the client is unable to communicate with the web server for the
     * 'connectionTimeout' period, the connection is aborted by the client driver..
     */
    int CONOPT_CONNECTION_TIMEOUT = 2;
}

