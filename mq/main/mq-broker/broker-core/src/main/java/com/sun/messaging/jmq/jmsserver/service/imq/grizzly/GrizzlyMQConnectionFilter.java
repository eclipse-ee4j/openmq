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

import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.attributes.AttributeHolder; 
import org.glassfish.grizzly.filterchain.NextAction;

public class GrizzlyMQConnectionFilter extends BaseFilter {

    private static boolean DEBUG = false;

    protected static final String GRIZZLY_MQIPCONNECTION_ATTR = 
        GrizzlyMQConnectionFilter.class + "connAttr"; 

    private final Attribute<GrizzlyMQIPConnection>
            connAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(GRIZZLY_MQIPCONNECTION_ATTR);

    private static final BrokerResources br = Globals.getBrokerResources();

    private GrizzlyIPService service = null;

    public GrizzlyMQConnectionFilter(GrizzlyIPService s) {
        this.service = s;
    }

    /**
     * Method is called, when new {@link Connection} was
     * accepted by a {@link org.glassfish.grizzly.Transport}
     *
     * @param ctx the filter chain context
     * @return the next action to be executed by chain
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleAccept(FilterChainContext ctx)
    throws IOException {
        Connection c = ctx.getConnection(); 
        try {
            GrizzlyMQIPConnection conn = service.createConnection(c);
            connAttr.set(c, conn);
            Globals.getConnectionManager().addConnection(conn);
            if (DEBUG) {
               Globals.getLogger().log(Logger.INFO, 
               "GrizzlyMQConnectionFilter.handleAccept(): "+conn+"["+c+"]");
            }
        } catch (Exception e) {
            Globals.getLogger().logStack(Globals.getLogger().ERROR, e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }

        return ctx.getInvokeAction();
    }

    /**
     * Method is called, when the {@link Connection} is getting closed
     *
     * @param ctx the filter chain context
     * @return the next action to be executed by chain
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx)
    throws IOException {
        Connection c = ctx.getConnection();
        GrizzlyMQIPConnection conn = connAttr.get(c);
        if (DEBUG) {
            Globals.getLogger().log(Logger.INFO, 
            "GrizzlyMQConnectionFilter.handleClose(): "+conn+"["+c+"]");
        }
        if (conn != null) {
            if (conn.getConnectionState() < GrizzlyMQIPConnection.STATE_CLOSED) {
                try {
                    conn.destroyConnection(true, GoodbyeReason.CLIENT_CLOSED, 
                                           br.getKString(br.M_CONNECTION_CLOSE));
                } catch (Exception e) {
                    if (DEBUG) {
                    Globals.getLogger().log(Logger.WARNING, e.getMessage(), e);
                    }
                }
            }
        }

        return super.handleClose(ctx);
    }

}
