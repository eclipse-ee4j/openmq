/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

1.  Protocol -

    Packet format -

     0                   1                   2                   3
    |0 1 2 3 4 5 6 7|8 9 0 1 2 3 4 5|6 7 8 9 0 1 2 3|4 5 6 7 8 9 0 1|
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |            version            |        packet type            |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                             size                              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                        source connection id                   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                      destination connection id                |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                       packet seq number                       |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           checksum                            |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    Connection establishment -

    1.  Client initiates connection by sending a request.
        Client -> Server : packet type = CONNECT,
                           source connection id = unique ID (client).

    2.  When the server accepts the connection, it responds with -
        Server -> Client : packet type = ACCEPTED,
                           dest connection id = unique ID (server).

    Sending packets from CLIENT to BROKER -

    1.  Client sends an HTTP request, client data is sent as request
        payload/content.

    2.  Servlet consumes the HTTP request and writes the data to the
        broker ingress queue.
    3.  Servlet responds with HTTP 200 OK. If there are any packets
        in the broker egress queue, they are sent as response content.

    4.  If client receives an HTTP error - it resends the packet.
    5.  If client receives the 200 OK, it read the data, if any, and
        enqueues it.

    Sending packets from BROKER to CLIENT -

    1.  Broker writes the data to the socket - the packet gets added
        to the egress queue.
    2.  Appropriate servlet request instance is selected for carrying
        the data back to the client - Piggybacking the packet on a
        'push' response is more efficient.

    3.  The data is sent back as response content.


2.  CONNECTION CLOSE protocol

    On HttpTunnelSocket.close() -
        rxShutdown
            Junk recvQ
            Start discarding all the incoming data pkts
            Stop sending ACKs.
        Enqueue CONN_CLOSE_PACKET to the transmit window.
        Disable further read / write calls from the application.
        Retransmit timers continue to work normally
        Continue processing ACKs normally

    On CONN_CLOSE_ACK -
        txShutdown
            Junk sendQ
            Stop all the retransmit timers
            disable ACK processing.

    On CONN_CLOSE_PACKET -
        Disable writes from the App.
        txShutdown
            Junk sendQ
            Stop all the retransmit timers
            disable ACK processing.

        Continue processing incoming packets -
            if incoming packet is :
                nextRecvSeq <= seq <= closeConnSeq
            then process it normally, else ignore it.
            Send acks normally.

    When HttpTunnelConnection.readData() hits the CONN_CLOSE_PACKET -
        rxShutdown
            Junk recvQ
            Start discarding all the incoming data pkts
            Stop sending ACKs.


3.  TODO -

    Development

        *   Retransmit timer handling - reentrancy issues???
        *   Window update timer - i.e. probe packet
        *   Configurable/Negotiable pull request rate
        *   Multiple listeners (listener ports)
        *   Multiple ServerLinks (scalability)
        *   Stats

    Testing
        Data communication
            Fast retransmit - random packet loss at the servlet.
            RTO binary exponential backoff - Intermediary failure
            Stress test with random packet loss - Track the following metrics -
                Throughput
                Queue lengths
                Memory consumption / object population.
                RTO
                Retranmission stats
            Flow control
            Run stress test on a multi-processor system.

        Connection state machine
            Connection establishment
            Connection failure
                Web server failure
                Server failure
                Client failure
            Connection closure

        API tests

