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

/*
 * @(#)PacketRouter.java	1.33 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.data;


import com.sun.messaging.jmq.io.*;
import java.security.AccessControlException;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.jmsserver.service.*;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQService;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.util.ServiceRestrictionException;
import com.sun.messaging.jmq.jmsserver.util.ServiceRestrictionWaitException;
import com.sun.messaging.jmq.io.PacketUtil;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.util.log.Logger;

/**
 * Class which handles passing messages read in from
 * a protocol to the correct Message Handler
 */

public class PacketRouter
{

    /**
     * dump all packets comming on
     */
    private static  boolean DEBUG = false;

    public static boolean getDEBUG() {
        return DEBUG;
    }

    private final Logger logger = Globals.getLogger();

    /**
     * list of message handlers for specific message types 
     */
    private PacketHandler list[] = new PacketHandler[PacketType.LAST];

    /**
     * default handler which handles errors and messages which do not
     * have handlers registered 
     */
    private ErrHandler defaultHandler = null;

    private FaultInjection fi = null;

    /**
     * Constructor for PacketRouter class.
     */
    public PacketRouter() {
	defaultHandler = new DefaultHandler();
        fi = FaultInjection.getInjection();
    }

    /**
     * registers a new handler for a specific message id
     *
     * @param id the messageID
     * @param handler the handler to add
     * @throws ArrayIndexOutOfBoundsException if the id is too high
     */
    public void addHandler(int id, PacketHandler handler) 
        throws ArrayIndexOutOfBoundsException 
    {
        if (id > PacketType.LAST) {
            throw new ArrayIndexOutOfBoundsException(Globals.getBrokerResources().getString(
           BrokerResources.X_INTERNAL_EXCEPTION, "Trying to add handler which has no corresponding packet type [ " + id + "]"));
        }
        list[id] = handler;
    }

    /**
     * registers a new handler for a group of message id
     *
     * @param sid the starting message id
     * @param eid the ending message id
     * @param handler the handler to add
     * @throws ArrayIndexOutOfBoundsException if the id is too high
     */
    public void addHandler(int sid, int eid, PacketHandler handler) 
        throws ArrayIndexOutOfBoundsException 
    {
        // NOTE: this is not that efficient, but it should ONLY happen at initialization
        // so I'm not worrying about it
        for (int i = sid; i < eid; i ++ )
            addHandler(i, handler);

    }

    /**
     * Return the handler for a specific packet type.
     *
     * @param id the packet type
     * @throws ArrayIndexOutOfBoundsException if the id is too high
     */
    public PacketHandler getHandler(int id)
        throws ArrayIndexOutOfBoundsException 
    {
        if (id > PacketType.LAST) {
            throw new ArrayIndexOutOfBoundsException(id);
        }
        return list[id];
    }

    /**
     * This routine handles passing messages off to the correct handler.
     * Messages should be first checked for authorization (if any)
     * and then sent to the correct handler.
     * If there is no handler, it should be sent to the default
     * handler (aka error handler).
     *
     * @param con the connection the message was received from
     * @param msg the message received
     *
     */
    public void handleMessage(IMQConnection con, Packet msg) {
        int id = msg.getPacketType();

        if (id < 0) {
            logger.log(Logger.ERROR, Globals.getBrokerResources().getString(
           BrokerResources.X_INTERNAL_EXCEPTION, "invalid packet type {0}",
                    String.valueOf(id)));
            defaultHandler.sendError(con, msg, "invalid packet type " + id,Status.ERROR);
            return;
        }

        PacketHandler handler = null;

        if (id >= PacketType.LAST) {
            handler = defaultHandler;
        } else {
            handler = list[id];
        }

        if (handler == null) {
            handler = defaultHandler;
        }
        try {
            if (handler != defaultHandler) { 
                checkServiceRestriction(msg, con, handler, id, defaultHandler);
                if (!checkAccessControl(msg, con, handler, id)) {
                    return;
                }
            }
            if (fi.FAULT_INJECTION && 
                ((IMQService)con.getService()).getServiceType() != ServiceType.ADMIN &&
                fi.checkFaultAndSleep(FaultInjection.FAULT_PACKET_ROUTER_1_SLEEP, null, true)) {
                fi.unsetFault(FaultInjection.FAULT_PACKET_ROUTER_1_SLEEP);
            }
            boolean freepkt = handler.handle(con, msg);
            if (freepkt == true) {
                msg.destroy();
            }
        } catch (ServiceRestrictionException ex) {
            defaultHandler.sendError(con, msg,
                ex.getMessage(), Status.UNAVAILABLE);
        } catch (ServiceRestrictionWaitException ex) {
            msg.destroy();
            return;
        } catch (BrokerException ex) {
            assert defaultHandler != null;

            if (defaultHandler != null) {
                if (ex.getStatusCode() == Status.UNAVAILABLE) {
                    defaultHandler.sendError(con, msg,
                            ex.getMessage(), Status.UNAVAILABLE);
                } else {
                    defaultHandler.sendError(con, ex, msg);
                }
            }
        } catch (Exception ex) {
            logger.logStack(logger.ERROR, ex.getMessage(), ex);
            defaultHandler.sendError(con, new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unexpected Error processing message"), ex), msg);
        }
    }

    private boolean checkAccessControl(Packet msg, IMQConnection con,
                                          PacketHandler handler, int pktype) {
        AccessController ac = con.getAccessController(); 
        if (pktype != PacketType.HELLO && pktype != PacketType.PING &&
                pktype != PacketType.AUTHENTICATE &&
                       pktype != PacketType.GOODBYE) {

            if (!ac.isAuthenticated()) {
                String emsg = Globals.getBrokerResources().getKString(
                        BrokerResources.E_UNEXPECTED_PACKET_NOT_AUTHENTICATED,
                                                 PacketType.getString(pktype));
                defaultHandler.sendError(con, msg, emsg, Status.ERROR); 
                return false;
            }
            try {
                handler.checkPermission(msg, con);
                return true;
            } catch (AccessControlException e) {
                try {                 
                    handler.handleForbidden(con, msg, pktype+1);
                } catch (BrokerException ex) {
                    defaultHandler.sendError(con, ex, msg);
                } catch (Exception ex) {
                    defaultHandler.sendError(con, 
			new BrokerException(
                            Globals.getBrokerResources().getKString(
                            BrokerResources.X_INTERNAL_EXCEPTION,
                           "Unexpected Error processing message"), ex), msg);
                }
            } catch (BrokerException ex) {
                defaultHandler.sendError(con, msg, ex.getMessage(), ex.getStatusCode());
            } catch (Exception ex) {
                defaultHandler.sendError(con,
                    new BrokerException(
                       Globals.getBrokerResources().getKString(
                       BrokerResources.X_INTERNAL_EXCEPTION,
                       "Unexpected Error processing message"), ex), msg);
            }
            return false;
        }    
        else {
            return true;
        }
    }

    private void checkServiceRestriction(Packet msg, IMQConnection con,
                                         PacketHandler handler, int pktype,
                                         ErrHandler defHandler) 
                                         throws BrokerException, Exception {

         if (pktype != PacketType.HELLO && pktype != PacketType.PING &&
             pktype != PacketType.AUTHENTICATE && pktype != PacketType.GOODBYE) {
             handler.checkServiceRestriction(msg, con, defHandler);
         }
    }

}
