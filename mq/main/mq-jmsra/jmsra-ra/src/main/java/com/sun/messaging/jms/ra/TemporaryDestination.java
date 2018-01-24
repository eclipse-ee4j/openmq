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

package com.sun.messaging.jms.ra;

//import javax.jms.*;
import javax.jms.JMSException;

import java.net.InetAddress;
import java.util.logging.Logger;

import com.sun.messaging.jmq.ClientConstants;
import com.sun.messaging.jmq.jmsservice.Destination;


/**
 *  TemporaryDestination for DIRECT Mode
 */
public abstract class TemporaryDestination
        extends com.sun.messaging.Destination {

    /**
     *  Logging
     */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.TemporaryDestination";
    private static transient final String _lgrNameOutboundConnection =
            "javax.resourceadapter.mqjmsra.outbound.connection";
    private static transient final String _lgrNameJMSConnection =
            "javax.jms.Connection.mqjmsra";
    private static transient final Logger _loggerOC =
            Logger.getLogger(_lgrNameOutboundConnection);
    private static transient final Logger _loggerJC =
            Logger.getLogger(_lgrNameJMSConnection);
    private static transient final String _lgrMIDPrefix = "MQJMSRA_TD";
    private static transient final String _lgrMID_EET = _lgrMIDPrefix+"1001: ";
    private static transient final String _lgrMID_INF = _lgrMIDPrefix+"1101: ";
    private static transient final String _lgrMID_WRN = _lgrMIDPrefix+"2001: ";
    private static transient final String _lgrMID_ERR = _lgrMIDPrefix+"3001: ";
    private static transient final String _lgrMID_EXC = _lgrMIDPrefix+"4001: ";

    /**
     *  Holds the DirectConnection that this TemporaryDestination was created in
     */
    private transient DirectConnection dc = null;

    /**
     *  Holds the jmsservice representation of this TemporaryDestination
     */
    private com.sun.messaging.jmq.jmsservice.Destination destination = null;

    /**
     *  Indicates whether this TemporaryDestination is deleted or not
     */
    private boolean deleted = false;

    /**
     *  Indicates the count of local consumers on this TemporaryDestination
     */
    private int consumer_count = 0;

    /**
     *  Creates a new instance of TemporaryDestination for use by
     *  Session.createTemporaryQueue() and Session.createTemporaryTopic()
     */
    protected TemporaryDestination(DirectConnection dc,
            com.sun.messaging.jmq.jmsservice.Destination.Type _type,
            com.sun.messaging.jmq.jmsservice.Destination.TemporaryType _tType)
    throws JMSException {
        super(Destination.TEMPORARY_DESTINATION_PREFIX +
                _tType + "/" +
                dc._getConnectionIdentifierForTemporaryDestination() + "/" +
                dc.nextTemporaryDestinationId());
        String _name = super.getName();
        this.dc = dc;
        this.destination = new com.sun.messaging.jmq.jmsservice.Destination(
                _name, _type,
                com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY);
    }

    /**
     *  Creates a new instance of TemporaryDestination for use when it is not
     *  explicitly created by Session.createTemporary----(); but when one is
     *  needed from either a Message.getJMSReply() or a MessageProducer's 
     *  send or publish methods
     */
    protected TemporaryDestination(String _name,
            com.sun.messaging.jmq.jmsservice.Destination.Type _type)
    throws JMSException {
        super(_name);
        this.destination = new com.sun.messaging.jmq.jmsservice.Destination(
                _name, _type,
                com.sun.messaging.jmq.jmsservice.Destination.Life.TEMPORARY);
    }

    /////////////////////////////////////////////////////////////////////////
    //  methods that implement javax.jms.TemporaryQueue_&_TemporaryTopic
    /////////////////////////////////////////////////////////////////////////
    /**
     *  Delete a TemporaryDestination
     */
    public void delete()
    throws JMSException {
        this._delete();
        dc.removeTemporaryDestination(this);
    }
    /////////////////////////////////////////////////////////////////////////
    //  end javax.jms.TemporaryQueue_&_TemporaryTopic
    /////////////////////////////////////////////////////////////////////////

    /**
     *  Return whether this is a temporary destination or not
     */
    public boolean isTemporary(){
        return true;
    }

    /**
     *  Return whether this TemporaryDestination is deleted or not
     */
    public boolean _isDeleted(){
        return deleted;
    }

    /**
     *  Return the Destination that represents this TemporaryDestination
     */
    protected com.sun.messaging.jmq.jmsservice.Destination _getDestination(){
        return this.destination;
    }

    /**
     *  Delete this temporary destination from the JMSService.
     */
    protected void _delete()
    throws JMSException {
        if (dc== null){
            //Cannot delete as this TD does not have an owning Connection
            String deleteMsg = _lgrMID_EXC + "delete()" +
                    ":Can only delete user created TemporaryDestinations";
            _loggerJC.warning(deleteMsg);
            throw new javax.jms.JMSException(deleteMsg);
        }
        if (dc._hasConsumers(this)){
            //Cannot delete as there are consumers on this TD
            String deleteMsg = _lgrMID_EXC + "delete()" +
                    ":Cannot delete TemporaryDestination with active consumers";
            _loggerJC.warning(deleteMsg);
            throw new javax.jms.JMSException(deleteMsg);
        }
        dc._deleteDestination(this, destination);
        this.deleted = true;
    }

    /**
     *  Increment the consumer count for this TemporaryDestination
     */
    protected int _incrementConsumerCount(){
        return ++this.consumer_count;
    }

    /**
     *  Decrement the consumer count for this TemporaryDestination
     */
    protected int _decrementConsumerCount(){
        --this.consumer_count;
        assert this.consumer_count >= 0;
        return this.consumer_count;
    }

    /**
     *  Return the consumer count for this TemporaryDestination
     */
    protected int _getConsumerCount(){
        return this.consumer_count;
    }

}
