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

package com.sun.messaging.bridge.admin;

import java.util.Properties;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import javax.jms.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.BridgeUtil;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.BridgeBaseContext;
import com.sun.messaging.bridge.api.BridgeServiceManager;
import com.sun.messaging.bridge.api.FaultInjection;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.admin.resources.BridgeManagerResources;
import com.sun.messaging.bridge.admin.handlers.AdminMessageHandler;
import com.sun.messaging.bridge.admin.util.AdminMessageType;
import com.sun.messaging.jmq.io.Status;
import com.sun.messaging.bridge.api.BridgeCmdSharedResources;
import com.sun.messaging.bridge.api.BridgeCmdSharedReplyData;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.api.DupKeyException;
import org.jvnet.hk2.annotations.Service;
import javax.inject.Singleton;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.api.StartupRunLevel;
import javax.inject.Inject;
import org.glassfish.hk2.api.ServiceLocator;

/**
 * The Bridge Services Manager
 *
 * @author amyk
 */
@RunLevel(StartupRunLevel.VAL+2)
@Service
//@Singleton
public class BridgeServiceManagerImpl extends BridgeServiceManager
                     implements ExceptionListener, MessageListener,
                     PostConstruct, PreDestroy
{

    private static transient final String BROKER_BRIDGE_BASE_CONTEXT_CLASS_STR = 
                   "com.sun.messaging.bridge.internal.BrokerBridgeBaseContext";

    private static transient final String className =
                   "com.sun.messaging.bridge.admin.BridgeServiceManagerImpl";

    private static transient final Logger logger =
	      Logger.getLogger("com.sun.messaging.bridge");
    private static transient final String LOGMSG_PREFIX = "MQBRIDGESTRAP: ";

    private enum State {UNINITIALIZED, STOPPING, STOPPED, STARTING, STARTED};
    private static boolean DEBUG = false;
    

    private static BridgeManagerResources _bmr = getBridgeManagerResources();
    private static Map<Locale, BridgeManagerResources> _bmrs = Collections.synchronizedMap(
                                              new HashMap<Locale, BridgeManagerResources>());

    private Map<String, Bridge> _bridges = Collections.synchronizedMap(
                                   new LinkedHashMap<String, Bridge>()); 
    private BridgeBaseContext _bc = null;

    private com.sun.messaging.ConnectionFactory _cf = null;
    private Connection _connection = null;
    private Session _session = null;
    private String _user = null;
    private String _passwd = null;
    private TemporaryQueue _adminQueue = null; 

    private State _state = State.UNINITIALIZED;

    private AdminMessageHandler _adminHandler = null;

    @Inject    
    private ServiceLocator habitat;

    public void postConstruct() {
        logger.entering(className, LOGMSG_PREFIX+"postConstruct()", "");
        BridgeBaseContext bbc = habitat.getService(BridgeBaseContext.class, 
                                    BROKER_BRIDGE_BASE_CONTEXT_CLASS_STR);
        if (bbc == null) {
            String emsg = "BridgeBaseContext null";
            logger.log(Level.SEVERE, emsg);
            throw new RuntimeException(emsg);
        }
        try {
            init(bbc);
            start();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException("Failed to start bridge service manager", e);
        }
    }

    public void preDestroy() {
	logger.entering(className, LOGMSG_PREFIX+"preDestroy()", "");
	logger.info(LOGMSG_PREFIX+"Stopping bridge service manager ..");
        try {
            stop();
        } catch (Exception e) {
            logger.log(Level.WARNING, LOGMSG_PREFIX+"Failed to stop bridge service manager", e);
        }
	logger.info(LOGMSG_PREFIX+"Stopped bridge service manager");
    }

    /**
     *  
     */
    public BridgeServiceManagerImpl() {} 

    /**
     * Initialize the bridge service manager 
     */
    public synchronized void init(BridgeBaseContext ctx) throws Exception {
        com.sun.messaging.jmq.jmsclient.Debug.setUseLogger(true);
        FaultInjection.setBridgeBaseContext(ctx);

        _bc = ctx;
        Properties props = _bc.getBridgeConfig();

        String activekey = props.getProperty(BridgeBaseContext.PROP_PREFIX)+".activelist";
        List<String> alist = BridgeUtil.getListProperty(activekey, props);
        int size = alist.size();

        String name = null;
        Iterator<String> itr = alist.iterator();
        while (itr.hasNext()) {
            name = itr.next();
            try {
                loadBridge(name);
            } catch (BridgeException e) {
                if (e.getStatus() == Status.NOT_MODIFIED) continue;

                _bc.logError(_bmr.getKString(_bmr.E_LOAD_BRIDGE_FAILED, name, e.getMessage()),  null);
                throw e;
            }
        }

        if (_bc.isHAEnabled()) {
            
            JMSBridgeStore store = (JMSBridgeStore)_bc.getJDBCStore();
            if (store == null) throw new BridgeException("null JDBC store");

            List jmsbridges = store.getJMSBridges(null);

            name = null;
            itr = alist.iterator();
            while (itr.hasNext()) {
                name = itr.next();
                String type = props.getProperty(
                              props.getProperty(
                              BridgeBaseContext.PROP_PREFIX)+"."+name+".type");
                if (type == null) {
                    throw new BridgeException(_bmr.getString(_bmr.X_BRIDGE_NO_TYPE, name));
                }
                if (!type.trim().toUpperCase(_bmr.getLocale()).equals(Bridge.JMS_TYPE)) {
                    continue;
                }
                if (jmsbridges.contains(name)) {
                    continue;
                }
                try {
                    store.addJMSBridge(name, true, null);
                } catch (DupKeyException e)  {
                    _bc.logInfo(_bmr.getKString(_bmr.I_JMSBRIDGE_NOT_OWNER, name), null);
                    itr.remove();
                }
            }
            jmsbridges = store.getJMSBridges(null);
            itr = jmsbridges.iterator();
            while (itr.hasNext()) {
                name = itr.next();
                if (alist.contains(name)) {
                    continue;
                }
                alist.add(name);
                try {
                    loadBridge(name);
                } catch (BridgeException e) {
                    _bc.logError(_bmr.getKString(_bmr.E_LOAD_BRIDGE_FAILED, name, e.getMessage()),  null);
                    throw e;
                }
            }
            if (alist.size() != size) {
                StringBuffer sb = new StringBuffer();
                int i = 0;
                itr = alist.iterator();
                while (itr.hasNext()) {
                    if (i > 0) sb.append(",");
                    sb.append(itr.next());
                    i++;
                }
                Properties p = new Properties();
                p.setProperty(activekey, sb.toString());
                _bc.updateBridgeConfig(p);
            }
        }


        String keyu = props.getProperty(BridgeBaseContext.PROP_PREFIX)+ctx.PROP_ADMIN_USER_SUFFIX;
        String keyp = props.getProperty(BridgeBaseContext.PROP_PREFIX)+ctx.PROP_ADMIN_PASSWORD_SUFFIX;
        _user = props.getProperty(keyu);
        _passwd = props.getProperty(keyp); 
        if (_user == null || _user.trim().length() == 0) {
            throw new JMSException(_bmr.getString(_bmr.X_BRIDGE_NO_ADMIN_USER, keyu));
        }
        _user = _user.trim();
        if (_passwd  == null || _passwd.trim().length() == 0) {
            throw new JMSException(_bmr.getString(_bmr.X_BRIDGE_NO_ADMIN_PASSWORD, keyp));
        } 
        _passwd = _passwd.trim();

        _adminHandler = new AdminMessageHandler(this);

        _state = State.STOPPED;
    }

    public synchronized boolean isRunning() {
        return (_state == State.STARTED);
    }

    public synchronized String getAdminDestinationName() throws Exception {
        if (_state != State.STARTED) {
            throw new BridgeException(_bmr.getString(_bmr.X_BRIDGE_SERVICE_MANAGER_NOT_RUNNING));
        }
        return _adminQueue.getQueueName();
    }

    public String getAdminDestinationClassName() throws Exception {
        if (_state != State.STARTED) {
            throw new BridgeException(_bmr.getString(_bmr.X_BRIDGE_SERVICE_MANAGER_NOT_RUNNING));
        }
        return _adminQueue.getClass().getName();
    }

    /**
     * Start the bridge service manager
     */
    public synchronized void start() throws Exception {
        if (_bc == null || _state == State.UNINITIALIZED) {
            throw new BridgeException(_bmr.getString(_bmr.X_BRIDGE_SERVICE_MANAGER_NOT_INITED));
        }
        _state = State.STARTING;

        Properties props = _bc.getBridgeConfig();

        try {

        Bridge b = null;
        String name = null;
        for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
            b = pair.getValue();
            name = b.getName();
            String autostart = props.getProperty(
                               props.getProperty(
                               BridgeBaseContext.PROP_PREFIX)+"."+name+".autostart", "true");
            boolean doautostart = Boolean.valueOf(autostart);
            try {
                if (doautostart) {
                    String[] args = null;
                    if (_bc.isStartWithReset()) args = new String[]{"-reset"};
                    startBridge(b, args);
                }
            } catch (BridgeException e) {
                if (e.getStatus() == Status.CREATED) {
                    continue; 
                }
                throw e;
            }
        }

        } catch (Exception e) {
            try {
                stopBridge(null, null, null);
            } catch (Throwable t) { }
            throw e;
        }

        try {
        _cf = new com.sun.messaging.ConnectionFactory();
        _cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqAddressList,
                        _bc.getBrokerServiceAddress("tcp",
                            com.sun.messaging.jmq.ClientConstants.CONNECTIONTYPE_ADMIN));

        _cf.setProperty(com.sun.messaging.ConnectionConfiguration.imqReconnectEnabled, "false");

        _connection = _cf.createConnection(_user, _passwd);
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		_adminQueue = _session.createTemporaryQueue();
        MessageConsumer mc = _session.createConsumer(_adminQueue);
        mc.setMessageListener(this);
        _connection.start();

        } catch (Exception e) {
            try {
                stopBridge(null, null, null);
                if (_connection != null) _connection.close();
            } catch (Throwable t) { }
            
            throw e;
        }
        _state = State.STARTED;
    }

    /**
     *
     */
    public synchronized void loadBridge(String name) throws Exception {
        _bc.logInfo("Loading bridge "+name, null);
        Locale loc = _bmr.getLocale(); 

        Bridge b = _bridges.get(name);
        if (b != null) { 
            _bc.logInfo("Bridge "+name+" is already loaded.", null);
           throw new BridgeException(_bmr.getString(_bmr.I_BRIDGE_ALREADY_LOADED, name), Status.NOT_MODIFIED);
        }

        /*
        Properties props = _bc.getBridgeConfig();

        String activekey = props.getProperty(
                                  BridgeBaseContext.PROP_PREFIX)+".activelist";
        List<String> alist = BridgeUtil.getListProperty(activekey, props);
        
        String tmpn = null;
        boolean found = false;
        Iterator<String> itr = alist.iterator();
        while (itr.hasNext()) {
            tmpn = itr.next();
            if (tmpn.equals(name)) {
                found = true;
                break; 
            }
        }
        if (!found) {
            String oldactives = props.getProperty(activekey);
            String newactives = oldactives+","+name;
            Properties p = new Properties();
            p.setProperty(activekey, newactives);
            _bc.updateBridgeConfig(p);
        }
        */

        Properties props = _bc.getBridgeConfig();
        String type = props.getProperty(
                      props.getProperty(
                      BridgeBaseContext.PROP_PREFIX)+"."+name+".type");
        if (type == null) {
            String emsg = _bmr.getKString(_bmr.E_LOAD_BRIDGE_NO_TYPE, name);
            _bc.logError(emsg, null);
            throw new BridgeException(emsg);
        }
        type = type.toLowerCase(loc);
        if (!type.toUpperCase(loc).equals(Bridge.JMS_TYPE) &&
            !type.toUpperCase(loc).equals(Bridge.STOMP_TYPE)) { 
            String emsg = _bmr.getKString(_bmr.X_BRIDGE_TYPE_NOSUPPORT, name, type);
            _bc.logError(emsg, null);
            throw new BridgeException(emsg);
        }

        String classn = props.getProperty(
                        props.getProperty(
                        BridgeBaseContext.PROP_PREFIX)+"."+type+".class");

        if (classn == null) {
            String emsg = _bmr.getKString(_bmr.E_LOAD_BRIDGE_NO_CLASS, name);
            _bc.logError(emsg, null);
            throw new BridgeException(emsg);
        }
        if (_bc.isRunningOnNucleus()) {
            b = habitat.getService(Bridge.class, type.toUpperCase(loc));
        } else {
            b = (Bridge)Class.forName(classn).newInstance();
        }

        if (!b.isMultipliable() && !b.getType().toLowerCase().equals(name.toLowerCase())) {
            String emsg =  _bmr.getKString(_bmr.E_BRIDGE_NAME_TYPE_NOT_SAME, name, b.getType());
            _bc.logError(emsg, null);
            throw new BridgeException(emsg);
        }

        if (DEBUG) {
            _bc.logInfo("Loaded brigde "+name+" by classloader "+
                        b.getClass().getClassLoader()+ 
                        "(parent:"+this.getClass().getClassLoader()+")", null);
        }

        b.setName(name);
        _bridges.put(name, b);

        _bc.logInfo("Loaded bridge "+name, null);

    }

    /**
     *
     * @return true if started successful, false if asynchronously started
     *
     * @throws Exception if start failed
     */
    public synchronized boolean startBridge(String name, String[] args, String type) throws Exception {

        if (type != null && 
            !type.equals(Bridge.JMS_TYPE) && !type.equals(Bridge.STOMP_TYPE)) { 
            throw new IllegalArgumentException(_bmr.getKString(_bmr.X_BRIDGE_INVALID_TYPE, type));
        }
        Bridge b = null;
        if (name != null) {
            b = _bridges.get(name);
            if (b == null) { 
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name));
            }
            if (type != null && !type.equals(b.getType())) {
                String[] eparam = new String[] {name, b.getType(), type};
                throw new  BridgeException(_bmr.getKString(_bmr.X_BRIDGE_TYPE_MISMATCH, eparam));
            }
            return startBridge(b, args);
        }

        boolean async = false;
        for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
            b = pair.getValue();
            if (type != null && !b.getType().equals(type)) continue;

            if (!startBridge(b, args)) {
                async = true;
            }
        }
        return !async;
    }

    /**
     * @param name must not be null
     *
     * @return true if started successful, false if asynchronously started
     *
     * @throws Exception if start failed
     */
    private boolean startBridge(Bridge b, String[] args) throws Exception {
         if (args == null) {
             _bc.logInfo(_bmr.getString(_bmr.I_STARTING_BRIDGE, b.getName()), null);
         }

         if (b.getState() == Bridge.State.STARTED && args == null) {
             _bc.logInfo(_bmr.getString(_bmr.I_BRIDGE_ALREADY_STARTED, b.getName()), null);
             return true;
         } 

         BridgeContext ctx = new BridgeContextImpl(_bc, b.getName());
         StringBuffer sf = new StringBuffer();
         sf.append(_bmr.getString(_bmr.I_STARTING_BRIDGE_WITH_PROPS, b.getName()));
         String key = null;
         Enumeration e = ctx.getConfig().propertyNames();
         while (e.hasMoreElements()) {
             key = (String)e.nextElement();
             sf.append("\t"+key+"="+ctx.getConfig().getProperty(key)+"\n");
         }
         if (args == null) {
             _bc.logInfo(sf.toString(), null);
         }

         try {
             boolean ret = b.start(ctx, args);
             if (args == null) {
                 _bc.logInfo(_bmr.getString(_bmr.I_STARTED_BRIDGE, b.getName()), null);
             }
             return ret;
         } catch (Exception e1) {
             _bc.logError(_bmr.getKString(_bmr.E_START_BRIDGE_FAILED, b.getName(), e1.getMessage()), null);
             throw e1;
         }
    }

    /**
     *
     */
    public synchronized void stopBridge(String name, String[] args, String type) throws Exception {

        if (type != null &&
            !type.equals(Bridge.JMS_TYPE) && !type.equals(Bridge.STOMP_TYPE)) {
            throw new IllegalArgumentException(_bmr.getKString(_bmr.X_BRIDGE_INVALID_TYPE, type));
        }
        Bridge b = null;
        if (name != null) {
            b = _bridges.get(name);
            if (b == null) {
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name));
            }
            if (type != null && !type.equals(b.getType())) {
                String[] eparam = new String[] {name, b.getType(), type};
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_TYPE_MISMATCH, eparam));
            }
            stopBridge(b, args);
            return;
        }

        for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
             b = pair.getValue();
             if (type != null && !b.getType().equals(type)) continue;

             stopBridge(b, args);
        }
    }


    /**
     *
     */
    public synchronized void pauseBridge(String name, String[] args, String type) throws Exception {

        if (type != null &&
            !type.equals(Bridge.JMS_TYPE) && !type.equals(Bridge.STOMP_TYPE)) {
            throw new IllegalArgumentException(_bmr.getKString(_bmr.X_BRIDGE_INVALID_TYPE, type));
        }
        if (name == null && type == null) {
            throw new UnsupportedOperationException(_bmr.getKString(_bmr.X_BRIDGE_PAUSE_NO_TYPE));
        }
        Bridge b = null;
        if (name != null) {
            b = _bridges.get(name);
            if (b == null) {
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name));
            }
            if (type != null && !type.equals(b.getType())) {
                String[] eparam = new String[] {name, b.getType(), type};
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_TYPE_MISMATCH, eparam));
            }
            pauseBridge(b, args);
            return;
        }

        for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
             b = pair.getValue();
             if (type != null && !b.getType().equals(type)) continue;

             pauseBridge(b, args);
        }
    }


    /**
     *
     */
    public synchronized void resumeBridge(String name, String[] args, String type) throws Exception {

        if (type != null &&
            !type.equals(Bridge.JMS_TYPE) && !type.equals(Bridge.STOMP_TYPE)) {
            throw new IllegalArgumentException(_bmr.getKString(_bmr.X_BRIDGE_INVALID_TYPE, type));
        }
        if (name == null && type == null) {
            throw new UnsupportedOperationException(_bmr.getKString(_bmr.X_BRIDGE_RESUME_NO_TYPE));
        }

        Bridge b = null;
        if (name != null) {
            b = _bridges.get(name);
            if (b == null) {
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name));
            }
            if (type != null && !type.equals(b.getType())) {
                String[] eparam = new String[] {name, b.getType(), type};
                throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_TYPE_MISMATCH, eparam));
            }
            resumeBridge(b, args);
            return;
        }

        for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
             b = pair.getValue();
             if (type != null && !b.getType().equals(type)) continue;

             resumeBridge(b, args);
        }
    }

    /**
     *
     */
    public synchronized void pauseBridge(Bridge b, String[] args) throws Exception {
        _bc.logInfo(_bmr.getString(_bmr.I_PAUSING_BRIDGE, b.getName()), null);

        if (b.getState() == Bridge.State.PAUSED && args == null) {
             _bc.logInfo(_bmr.getString(_bmr.I_BRIDGE_ALREADY_PAUSED, b.getName()), null);
             return;
         }

        b.pause(new BridgeContextImpl(_bc, b.getName()), args);
        _bc.logInfo(_bmr.getString(_bmr.I_PAUSED_BRIDGE, b.getName()), null);
    }

    /**
     *
     */
    public synchronized void resumeBridge(Bridge b, String[] args) throws Exception {
        _bc.logInfo(_bmr.getString(_bmr.I_RESUMING_BRIDGE, b.getName()), null);

        if (b.getState() == Bridge.State.STARTED && args == null) {
             _bc.logInfo(_bmr.getString(_bmr.I_BRIDGE_IS_RUNNING, b.getName()), null);
             return;
         }

        b.resume(new BridgeContextImpl(_bc, b.getName()), args);
        _bc.logInfo(_bmr.getString(_bmr.I_RESUMED_BRIDGE, b.getName()), null);

    }

    /**
     *
     */
    public synchronized void unloadBridge(String name) throws Exception {
        _bc.logInfo("Unloading bridge "+name, null);

        Bridge b = _bridges.get(name);
        if (b == null) { 
            _bc.logInfo("Bridge "+name+" is not loaded.",  null);
            throw new BridgeException("Bridge "+name+" is not loaded.", Status.NOT_MODIFIED);
        }
        stopBridge(name, null, null);
        _bridges.remove(name);
        b.setName(null);

        Properties props = _bc.getBridgeConfig();
        List<String> alist = BridgeUtil.getListProperty(
                                props.getProperty(
                                BridgeBaseContext.PROP_PREFIX)+".activelist", props);
        
        String tmpn = null;
        StringBuffer sbuf = new StringBuffer();
        Iterator<String> itr = alist.iterator();
        while (itr.hasNext()) {
            tmpn = itr.next();
            if (!tmpn.equals(name)) {
                if (sbuf.length() > 0 ) {
                    sbuf.append(",");
                }
                sbuf.append(tmpn);
            }
        }
        Properties p = new Properties();
        p.setProperty(props.getProperty(BridgeBaseContext.PROP_PREFIX)+".activelist",
                      sbuf.toString());
        _bc.updateBridgeConfig(p);

        _bc.logInfo("Unloaded bridge "+name, null);
    }

    /**
     *
     */
    private void stopBridge(Bridge b, String[] args) throws Exception {

        if (b.getState() == Bridge.State.STOPPED && args == null) {
             _bc.logDebug(_bmr.getString(_bmr.I_BRIDGE_ALREADY_STOPPED, b.getName()), null);
             return;
         }

        _bc.logInfo(_bmr.getString(_bmr.I_STOPPING_BRIDGE, b.getName()), null);

        b.stop(new BridgeContextImpl(_bc, b.getName()), args);
        _bc.logInfo(_bmr.getString(_bmr.I_STOPPED_BRIDGE, b.getName()), null);
    }

    /**
     */
    public synchronized ArrayList<BridgeCmdSharedReplyData> listBridge(
                               String name, String[] args, String type,
                               BridgeManagerResources bmr)
                               throws Exception {

        if (type != null && 
            !type.equals(Bridge.JMS_TYPE) && !type.equals(Bridge.STOMP_TYPE)) { 
            throw new IllegalArgumentException(_bmr.getString(_bmr.X_BRIDGE_INVALID_TYPE, type));
        }

        if (name == null) { 
            _bc.logDebug("Listing all bridges (type="+type+")" , null);

            BridgeCmdSharedReplyData reply = new BridgeCmdSharedReplyData(3, 4, "-");
            String   oneRow[] = new String [3]; 
            oneRow[0] =  bmr.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_BRIDGE_NAME);
            oneRow[1] =  bmr.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_BRIDGE_TYPE);
            oneRow[2] =  bmr.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_BRIDGE_STATE);
            reply.addTitle(oneRow);

            Bridge b = null;
            for (Map.Entry<String, Bridge> pair: _bridges.entrySet()) {
                b = pair.getValue();
                if (type != null && !b.getType().equals(type)) continue;
                oneRow[0] = b.getName(); 
                oneRow[1] = b.getType();
                oneRow[2] = b.getState().toString(bmr); 
                reply.add(oneRow);
            }
            _bc.logDebug("Listed all bridges (type="+type+")", null);
            ArrayList<BridgeCmdSharedReplyData> replys = new ArrayList<BridgeCmdSharedReplyData>();
            replys.add(reply);
            return replys;
        }                 

        if (args == null) {
            _bc.logInfo(_bmr.getString(_bmr.I_LISTING_BRIDGE, name), null);
        } else {
            _bc.logInfo(_bmr.getString(_bmr.I_LISTING_BRIDGE_WITH, name, BridgeUtil.toString(args)), null);
        }

        Bridge b = _bridges.get(name);
        if (b == null) { 
            String emsg = _bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name);
            _bc.logError(emsg, null);
            throw new BridgeException(emsg);
        }

        BridgeContext bc = new BridgeContextImpl(_bc, b.getName());
        return b.list(bc, args, bmr);
    }

    /**
     * Stop the bridge service manager
     */
    public void stop() throws Exception {
        if (_state == State.STOPPING || _state == State.STOPPED) {
            return;
        }
        synchronized(this) {

        if (_bc == null) {
            throw new BridgeException(_bmr.getString(_bmr.X_BRIDGE_SERVICE_MANAGER_NOT_INITED));
        }
        _state = State.STOPPING;
        try {
            stopBridge(null, null, null);
        } catch (Throwable t) {
            if (t instanceof java.util.concurrent.RejectedExecutionException) {
            _bc.logDebug(_bmr.getKString(_bmr.W_EXCEPTION_STOP_BRIDGES, t.getMessage()), null);
            } else {
            _bc.logWarn(_bmr.getKString(_bmr.W_EXCEPTION_STOP_BRIDGES, t.getMessage()), t);
            }
        }

        if (_connection != null) {
            try {
            _connection.close();
            } catch (Throwable t) {
            if (DEBUG) {
            _bc.logWarn(_bmr.getKString(_bmr.W_EXCEPTION_CLOSE_ADMIN_CONN, t.getMessage()), t);
            } else {
            _bc.logWarn(_bmr.getKString(_bmr.W_EXCEPTION_CLOSE_ADMIN_CONN, t.getMessage()), null);
            }
            }
        }
        _state = State.STOPPED;

        }
    }

    /**
     *
     */
    public Bridge getBridge(String name) throws Exception {
        Bridge b = _bridges.get(name);
        if (b == null) { 
            throw new BridgeException(_bmr.getKString(_bmr.X_BRIDGE_NAME_NOT_FOUND, name));
        }
        return b;
    }

    public BridgeBaseContext getBridgeBaseContext() {
        return _bc;
    }

    public void onException(JMSException e) {
        if (_bc.isEmbeded()) {
            _bc.logError(_bmr.getKString(_bmr.E_EXCEPTION_OCCURRED_ADMIN_CONN, e.getMessage()), e);
        } else {
            //not supported else do reconnect   
            _bc.logError("Not supported: bridge servie manager is not embeded!", null);
        }
    }

    public void onMessage(Message msg) {

        if (_state != State.STARTED) {
            String emsg = _bmr.getKString(_bmr.X_BRIDGE_SERVICE_MANAGER_NOT_RUNNING);
            _bc.logInfo(emsg, null);
            _adminHandler.sendReply(_session, msg, null, Status.UNAVAILABLE, emsg, _bmr);
        }
        if (!(msg instanceof ObjectMessage)) {
            String emsg = "Unexpected bridge admin message type: "+msg.getClass().getName();
            _bc.logError(emsg, null);
            _adminHandler.sendReply(_session, msg, null, Status.BAD_REQUEST, emsg, _bmr);
            return;
        }
        _adminHandler.handle(_session, (ObjectMessage)msg);
    }

    public static synchronized BridgeManagerResources getBridgeManagerResources() {
        if (_bmr == null) {
            _bmr = BridgeManagerResources.getResources(Locale.getDefault());
        }
        return _bmr;
    }

    public static BridgeManagerResources getBridgeManagerResources(Locale l) {
        if (l == null) return getBridgeManagerResources();

        BridgeManagerResources bmr = _bmrs.get(l);
        if (bmr != null) return bmr;

        synchronized(_bmrs) {
            bmr = _bmrs.get(l);
            if (bmr == null) {
                bmr = BridgeManagerResources.getResources(l);
                _bmrs.put(l, bmr);
            }
            return bmr;
        }
    }

    public boolean getDEBUG() {
        return DEBUG;
    }

    public static void main(String[] args) {
    }
}
