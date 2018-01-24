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
 * @(#)IMQIPServiceFactory.java	1.17 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq;

import com.sun.messaging.jmq.jmsserver.service.*;

import java.util.*;
import java.io.*;

import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;

import com.sun.messaging.jmq.jmsserver.Globals;

import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.net.tcp.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.util.log.Logger;

//XXX-LKS we currently dont do anything with changed properties
//        we may want to change this in future releases
public abstract class IMQIPServiceFactory extends ServiceFactory
{

    public static final String PROTOCOL_PREFIX = Globals.IMQ + ".protocol.";

    private BrokerConfig props = Globals.getConfig();

    private int DEFAULT_DESTROY_TIMEOUT=30;

    protected Map getProtocolParams(String protoname, String prefix)
    {
        List proto_props = getProtocolNames(protoname);

        if (proto_props == null) return null;

        HashMap map = new HashMap();

        for (int i =0; i <proto_props.size(); i ++) {
            String name = (String)proto_props.get(i);
 
            String value = Globals.getConfig().getProperty(prefix +"."+ name);

            if (value != null)map.put(name, value);
        }
        return map;
    }

    protected List getProtocolNames(String protoname)
    {
        return Globals.getConfig().getList(PROTOCOL_PREFIX +
            protoname + ".propertylist");
    }


    public  void updateService(Service s)
        throws BrokerException
    {
        IMQService ss = (IMQService)s;
        String name = s.getName();

        // set changes to the protocol
        String protoname = SERVICE_PREFIX + name + ".protocoltype";
        String protocol =  props.getProperty(protoname);
        String prefix = SERVICE_PREFIX+name +"."+protocol;

        Protocol p = ss.getProtocol();
        Map params = getProtocolParams(protocol, prefix);
        // check the parameters are OK
        p.checkParameters(params);
        // set the parameters
        try {
            p.setParameters(params);
        } catch (IOException ex) {
            String args[] = { protocol, p.toString(), name};
            throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_PORT_UNAVAILABLE, args), ex);
        }

        // set changes to the service
        int newmin = getThreadMin(name);
        int newmax = getThreadMax(name);
        try {
        ss.setMinMaxThreadpool(newmin, newmax);
        } catch (IllegalArgumentException e) {
            throw new BrokerException(
                     Globals.getBrokerResources().getKString(
                         BrokerResources.X_THREADPOOL_BAD_SET,
                         String.valueOf(newmin),
                         String.valueOf(newmax)),
                     e);
        }

        // Register port with portmapper
        Globals.getPortMapper().addService(name, protocol,
            props.getProperty(SERVICE_PREFIX + name + ".servicetype"),
            p.getLocalPort(), ss.getServiceProperties());
        
    }

// XXX - this is not optimized, but it should rarely happen

    public  void startMonitoringService(Service s)
        throws BrokerException {

        String name = s.getName();
        String protoname = SERVICE_PREFIX + name + ".protocoltype";
        String protocol =  props.getProperty(protoname);

        // add protocol properties
        List params = getProtocolNames(protocol);
    
        for (int i =0; params != null && i < params.size(); i ++ ) 
        {
            String prop =(String)params.get(i);
   
            props.addListener(prop, this);

        }

        // add min/max properties
        String bstr = SERVICE_PREFIX + name + ".min_threads";
        props.addListener(bstr, this);


        bstr = SERVICE_PREFIX + name + ".max_threads";
        props.addListener(bstr, this);
    }

    public  void stopMonitoringService(Service s)   
        throws BrokerException
    {
        String name = s.getName();
        String protoname = SERVICE_PREFIX + name + ".protocoltype";
        String protocol =  props.getProperty(protoname);

        // remove protocol properties
        List params = getProtocolNames(protocol);
        for (int i =0; params != null && i < params.size(); i ++ ) 
        {
            String prop =(String)params.get(i);
        
            props.removeListener(prop, this);

        }

        // remove min/max properties
        String bstr = SERVICE_PREFIX + name + ".min";
        props.removeListener(bstr, this);


        bstr = SERVICE_PREFIX + name + ".max";
        props.removeListener(bstr, this);
    }


    public  void validate(String name, String value)
        throws PropertyUpdateException {
        // for now, dont bother with validation
    }

    public  boolean update(String name, String value) 
    {

        return true;
    }

    protected int getThreadMin(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".min_threads";
        return props.getIntProperty(bstr); 
    }

    protected int getPoolTimeout(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".destroy_timeout";

        // get timer and covert to seconds
        return props.getIntProperty(bstr,DEFAULT_DESTROY_TIMEOUT )*1000; 
    }

    protected int getThreadMax(String instancename) 
    {
        String bstr = SERVICE_PREFIX + instancename + ".max_threads";
        return props.getIntProperty(bstr); 
    }

    public Service createService(String instancename, int type) 
        throws BrokerException
    {
        String protocol_type_string =  SERVICE_PREFIX + instancename 
                         + ".protocoltype";

        String protocol =  props.getProperty(protocol_type_string);
        if (protocol == null) { // throw exception
              throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_MISSING_SERVICE_PROPERTY, instancename,
                    protocol_type_string));
        }

        String pclass_type_string = PROTOCOL_PREFIX + protocol + ".class";

        String pclass =  props.getProperty(pclass_type_string);
        if (pclass == null) { // throw exception
              throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_MISSING_SERVICE_PROPERTY, instancename,
                    pclass_type_string));
        }

        if (DEBUG) {
            logger.log(Logger.DEBUG, " Creating new Service("+ instancename +
                  ":" + pclass +  ")");
        }

        Protocol proto = null;
        try {
            proto = (Protocol)Class.forName(pclass).newInstance();

        } catch (Exception ex) {
            String args[] = { protocol, instancename};
            throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_PROTO_UNAVAILABLE, args), ex);
        }

        // handle the no delay flag

        String prefix = PROTOCOL_PREFIX + protocol;

        boolean nodelay = Globals.getConfig().getBooleanProperty(
                       prefix + ".nodelay", true);
        proto.setNoDelay(nodelay);

        int inputBufferSize = Globals.getConfig().getIntProperty(
                       prefix+ ".inbufsz", 0);

        int outputBufferSize = Globals.getConfig().getIntProperty(
                       prefix + ".outbufsz", 0);

        proto.setInputBufferSize(inputBufferSize);
        proto.setOutputBufferSize(outputBufferSize);

        String serviceprefix = SERVICE_PREFIX+instancename +"."+protocol;

        Map params = getProtocolParams(protocol, serviceprefix);
        params.put("serviceFactoryHandlerName", getFactoryHandlerName());

        // check the parameters are OK
        proto.checkParameters(params);
        // set the parameters

        // now create service
        try {
            proto.setParameters(params);
            proto.open();
        } catch (IOException ex) {
            String args[] = { pclass, protocol, instancename};
            throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.X_PORT_UNAVAILABLE, args), ex);
        }
        //XXX - allow service used to be selected
        try {
            Service svc = createService(instancename, proto,
                Globals.getPacketRouter(type), type, getThreadMin(instancename),
                getThreadMax(instancename)); 

            // bug 4433282 -> support optional timeout for pool
            long timeout = getPoolTimeout(instancename);
            if (timeout > 0)
               ((IMQService)svc).setDestroyWaitTime(timeout);
            return svc;
        } catch (IOException ex) {
            try {
                proto.close();
            } catch (Exception ex1) {
                logger.log(Logger.DEBUGHIGH,"Error closing protocol", ex1);
            }
            throw new BrokerException(Globals.getBrokerResources().getKString(
                    BrokerResources.E_ERROR_STARTING_SERVICE, instancename), ex);
        }
 
    }

    protected abstract IMQService createService(String instancename, 
            Protocol proto, PacketRouter router, int type, int min, int max)
        throws IOException;

    

}
/*
 * EOF
 */
