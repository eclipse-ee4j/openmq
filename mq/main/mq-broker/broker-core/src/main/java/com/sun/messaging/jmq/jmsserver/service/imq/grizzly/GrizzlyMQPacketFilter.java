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

package com.sun.messaging.jmq.jmsserver.service.imq.grizzly;

import java.io.IOException;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.NullaryFunction; 
import org.glassfish.grizzly.utils.BufferInputStream;
import org.glassfish.grizzly.utils.BufferOutputStream;
import org.glassfish.grizzly.memory.MemoryManager; 
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.CompositeBuffer;
import com.sun.messaging.jmq.io.Packet;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.io.BigPacketException;

public class GrizzlyMQPacketFilter extends BaseFilter 
{
    private static boolean DEBUG = false;

    private final Attribute<PacketParseState> parsestateAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            GrizzlyMQPacketFilter.class + ".parsestateAttr",
            new NullaryFunction<PacketParseState>() {

                @Override
                public PacketParseState evaluate() {
                    return new PacketParseState();
                }
            });

    private final Attribute<GrizzlyMQIPConnection>
            connAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
               GrizzlyMQConnectionFilter.GRIZZLY_MQIPCONNECTION_ATTR);

    private Logger logger = Globals.getLogger();

    public GrizzlyMQPacketFilter() {
    }

    /**
     * Method is called, when new data was read from the Connection and ready
     * to be processed.
     *
     * We override this method to perform Buffer -> GIOPMessage transformation.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {

        final GrizzlyMQPacketList packetList = GrizzlyMQPacketList.create();
        
        final Connection c = ctx.getConnection();
        final Buffer buf = ctx.getMessage();

        final PacketParseState parsestate = parsestateAttr.get(c);

        while (buf.hasRemaining()) {
            int buflen = buf.remaining();
            if (DEBUG) {
                logger.log(logger.INFO,
                        "[@" + c.hashCode() + "]buflen=" + buflen + ", gotpsize=" + parsestate.gotpsize
                        + ", psize=" + parsestate.psize + ", pos=" + buf.position());
            }

            if (!parsestate.gotpsize) {
                if (buflen < Packet.HEADER_SIZE) {
                    if (DEBUG) {
                        logger.log(logger.INFO, "[@" + c.hashCode()
                                + "] not enough for header size " + Packet.HEADER_SIZE);
                    }
//                    return ctx.getStopAction(buf);
                    break;
                }
                int pos = buf.position();
                parsestate.psize = GrizzlyMQPacket.parsePacketSize(buf);
                buf.position(pos);
                parsestate.gotpsize = true;
            }

            if (buflen < parsestate.psize) {
                if (DEBUG) {
                    logger.log(logger.INFO, "[@" + c.hashCode()
                            + "] not enough for packet size " + parsestate.psize);
                }
//                return ctx.getStopAction(buf);
                break;
            }
            if (DEBUG) {
                logger.log(logger.INFO, "[@" + c.hashCode() + "]reading packet at pos="
                        + buf.position() + ", size=" + parsestate.psize);
            }

            final int pos = buf.position();

            Packet pkt = null;
            BufferInputStream bis = null;
            try {
                pkt = new GrizzlyMQPacket(false); //XXX
                pkt.generateSequenceNumber(false);
                pkt.generateTimestamp(false);

                bis = new BufferInputStream(buf);
                pkt.readPacket(bis);
                //        ctx.setMessage(pkt);
                if (DEBUG) {
                    logger.log(logger.INFO, "[@" + c.hashCode() + "]read packet: " + pkt + ", pre-pos=" + pos);
                }

                packetList.getPackets().add(pkt);
            } catch (OutOfMemoryError err) {
                Globals.handleGlobalError(err,
                        Globals.getBrokerResources().getKString(
                        BrokerResources.M_LOW_MEMORY_READALLOC) + ": "
                        + (pkt == null ? "null":pkt.headerToString()));
                buf.position(pos);
                try {
                Thread.sleep(1000L);
                } catch (Exception e) {}
                continue;
            } catch (BigPacketException e) {
                 GrizzlyMQIPConnection conn = connAttr.get(c);
                 conn.handleBigPacketException(pkt, e);
            } catch (IllegalArgumentException e) {
                 GrizzlyMQIPConnection conn = connAttr.get(c);
                 conn.handleIllegalArgumentExceptionPacket(pkt, e);
            } finally {
                 if (bis != null) {
                     bis.close();
                 }
            }

            buf.position(pos + parsestate.psize);
            parsestate.reset();
        }

        // There are no packets parsed
        if (packetList.getPackets().isEmpty()) {
            packetList.recycle(false);
            return ctx.getStopAction(buf);
        }
        
        final Buffer remainder;
        if (buf.hasRemaining()) {
            remainder = buf.split(buf.position());
        } else {
            remainder = null;
        }
        
        packetList.setPacketsBuffer(buf);
        ctx.setMessage(packetList);
        
        if (DEBUG) {
            logger.log(logger.INFO,
            "[@"+c.hashCode()+"]handleRead.return: " + 
                    (remainder == null ?
                    "no remainder" :
                    "remainer="+remainder.hasRemaining()+ ", remaining="+remainder.remaining()
                    ));
        }

        return ctx.getInvokeAction(remainder);
    }

    @Override
    public NextAction handleWrite(final FilterChainContext ctx)
    throws IOException {
        final Packet packet = ctx.getMessage();

        final MemoryManager mm = ctx.getConnection().
                            getTransport().getMemoryManager();
        BufferOutputStream bos = null;
        try {

        bos = new BufferOutputStream(mm);
        packet.writePacket(bos);
        bos.close();
        Buffer buf = bos.getBuffer();
        buf.trim();
        buf.allowBufferDispose(true);
        if (buf.isComposite()) {
            ((CompositeBuffer) buf).allowInternalBuffersDispose(true);
        }
        
        ctx.setMessage(buf);

        return ctx.getInvokeAction();

        } finally {
        if (bos != null) bos.close();
        }
    }


    static final class PacketParseState {
        boolean gotpsize = false; 
        int psize = -1;

        void reset() {
            gotpsize = false;
            psize = -1;
        }
    }

}
