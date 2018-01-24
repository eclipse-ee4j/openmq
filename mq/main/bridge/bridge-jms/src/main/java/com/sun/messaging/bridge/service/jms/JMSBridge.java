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

package com.sun.messaging.bridge.service.jms;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Properties;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.jms.Message;
import javax.jms.Destination;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.XASession;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.JMSException;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSSecurityException;
import javax.naming.InitialContext;
import com.sun.messaging.bridge.api.Bridge;
import com.sun.messaging.bridge.api.BridgeContext;
import com.sun.messaging.bridge.api.BridgeException;
import com.sun.messaging.bridge.api.LogSimpleFormatter;
import com.sun.messaging.bridge.service.jms.xml.*;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import com.sun.messaging.bridge.service.jms.tx.TransactionManagerAdapter;
import com.sun.messaging.bridge.service.jms.tx.TransactionManagerImpl;
import com.sun.messaging.bridge.service.jms.tx.log.TxLog;
import com.sun.messaging.bridge.service.jms.tx.log.FileTxLogImpl;
import com.sun.messaging.bridge.api.JMSBridgeStore;
import com.sun.messaging.bridge.api.BridgeCmdSharedReplyData;
import com.sun.messaging.bridge.api.BridgeCmdSharedResources;
import com.sun.messaging.bridge.service.jms.resources.JMSBridgeResources;

/**
 * 
 * @author amyk
 *
 */
public class JMSBridge {

    private final static String PROP_XMLURL_SUFFIX = ".xmlurl";

    private final static String PROP_LOGFILE_LIMIT_SUFFIX = ".logfile.limit";
    private final static String PROP_LOGFILE_COUNT_SUFFIX = ".logfile.count";

    private static JMSBridgeResources _jbr = getJMSBridgeResources();
    /*
     * wrapped connection factory to Pooled/SharedConnection factory mapping
     */
    Map<Object, Object> _spcfs = 
        Collections.synchronizedMap(new LinkedHashMap<Object, Object>()); 

    /**
     * connection-factory-ref to wrapped connection factory mapping
     */
    LinkedHashMap<String, Object> _allCF = new LinkedHashMap<String, Object>();

    LinkedHashMap<String, Object> _localCFs = new LinkedHashMap<String, Object>();
    LinkedHashMap<String, Object> _localXACFs = new LinkedHashMap<String, Object>();

    LinkedHashMap<String, Link> _links = new LinkedHashMap<String, Link>(); 

    //first one is built-in
    LinkedHashMap<String, DMQ> _dmqs = new LinkedHashMap<String, DMQ>(); 

    private static Logger _logger = null;

    private String _name = null;
    private BridgeContext _bc = null;
    private String _xmlurl = null;
    private JMSBridgeElement _jmsbridge = null;
    protected final EventNotifier _notifier = new EventNotifier();

    private TransactionManagerAdapter _tma = null;
    private TransactionManager _tm = null;

    private int _transactionTimeout = 180; //seconds
    private boolean _supportTransactionTimeout = false;
    private boolean _reset = false;

    private Object _initLock =  new Object();

    public static final String  BRIDGE_NAME_PROPERTY = "JMS_SUN_JMSBRIDGE_NAME";

	private ExecutorService _asyncStartExecutor = null;

    private Object _startFutureLock = new Object();
    private LinkedHashMap _startFutures = new LinkedHashMap();
    private final CountDownLatch _startLatch = new CountDownLatch(1);

    public void init(BridgeContext bc, String name, boolean reset) throws Exception {

        _bc = bc;
        _name = name;
        _reset = reset;

        Properties props = bc.getConfig();

        String domain = props.getProperty(BridgeContext.BRIDGE_PROP_PREFIX);

        _xmlurl = props.getProperty(domain+PROP_XMLURL_SUFFIX);
        if (_xmlurl == null) {
            throw new IllegalArgumentException(
                _jbr.getKString(_jbr.X_NOT_SPECIFIED, _name, domain+PROP_XMLURL_SUFFIX));
        }

        _logger = Logger.getLogger(domain);
        if (bc.isSilentMode()) {
            _logger.setUseParentHandlers(false);
        }

        String var = bc.getRootDir();
        File dir =  new File(var);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String logfile = var+File.separator+"jms%g.log";
        int limit = 0, count = 1;
        String limits = props.getProperty(domain+PROP_LOGFILE_LIMIT_SUFFIX);
        if (limits != null) {
            limit = Integer.parseInt(limits);
        }
        String counts = props.getProperty(domain+PROP_LOGFILE_COUNT_SUFFIX);
        if (counts != null) {
            count = Integer.parseInt(counts);
        }

        FileHandler h = new FileHandler(logfile, limit, count, true);
        h.setFormatter(new LogSimpleFormatter(_logger));
        _logger.addHandler(h);

        _logger.log(Level.INFO, _jbr.getString(_jbr.I_LOG_DOMAIN, _name, domain));
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_LOG_FILE, _name, logfile)+"["+limit+","+count+"]");

        String lib = bc.getLibDir();
        if (lib == null) {
            throw new IllegalArgumentException("JMS bridge "+_name+" lib dir not specified");
        }
        String dtdd = lib+File.separator+"dtd"+File.separator;
        File dtddir = new File(dtdd);
        if (!dtddir.exists()) {
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_NOT_EXIST, _name, dtdd));
        }
        String sysid = dtddir.toURI().toURL().toString();

        String[] param = {_name, _xmlurl, sysid}; 
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_INIT_JMSBRIDGE_WITH, param));
        
        JMSBridgeReader reader = new JMSBridgeReader(_xmlurl, sysid, _logger);
        _jmsbridge = reader.getJMSBridgeElement();
        if (!_name.equals(_jmsbridge.getName())) {
            String[] eparam = {_name, _jmsbridge.getName(), _xmlurl};
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_JMSBRIDGE_NAME_MISMATCH, eparam));
        }

        createBuiltInDMQ(_jmsbridge);

        Map<String, DMQElement> edmqs = _jmsbridge.getDMQs();
        for (Map.Entry<String, DMQElement> pair: edmqs.entrySet()) {
            if (pair.getKey().equals(DMQElement.BUILTIN_DMQ_NAME)) {
                continue; 
            }
            createDMQ(pair.getValue(), _jmsbridge);
        }

        boolean xa = false;
        Map<String, LinkElement> elinks = _jmsbridge.getLinks();
        for (Map.Entry<String, LinkElement> pair: elinks.entrySet()) {
            xa |= createLink(pair.getValue(), _jmsbridge);
        }
        if (xa) {
            TransactionManagerAdapter tma = getTransactionManagerAdapter();

            if (tma.registerRM()) {

                Map<String, XAConnectionFactoryImpl> xacfs = 
                    new LinkedHashMap<String, XAConnectionFactoryImpl>((Map)_localXACFs);

                for (Map.Entry<String, Object> pair: _allCF.entrySet()) {
                    if (xacfs.get(pair.getKey()) != null) continue; 
                    if (pair.getValue() instanceof XAConnectionFactory) {
                        xacfs.put(pair.getKey(), (XAConnectionFactoryImpl)pair.getValue());
                    }
                }

                String cfref = null;
                Object cf = null;
                Map<String, ConnectionFactoryElement> cfs = _jmsbridge.getAllCF();
                for (Map.Entry<String, ConnectionFactoryElement> pair: cfs.entrySet()) {
                    cfref = pair.getKey(); 
                    if (xacfs.get(cfref) != null) continue;

                    try {
                        cf = createConnectionFactory(pair.getValue(), true);
                    } catch (NotXAConnectionFactoryException e) {
                        _logger.log(Level.INFO, _jbr.getString(
                            _jbr.I_CF_NOT_XA_NO_REGISTER, pair.getKey(), "XAConnectionFactory"));
                        continue;
                    }
                    if (cf == null) {
                        cf = new XAConnectionFactoryImpl(_bc, _jmsbridge.getCF(cfref).getProperties(),
                                            _bc.isEmbeded(), cfref, pair.getValue().isMultiRM());
                    }
                    xacfs.put(cfref, (XAConnectionFactoryImpl)cf);
                }
                registerXAResources(xacfs);

            } else {
                Link link = null;
                for (Map.Entry<String, Link> pair: _links.entrySet()) {
                     link = pair.getValue();
                     if (link.isTransacted()) {
                         link.registerXAResources();
                     }
                } 
            }
        }
        _asyncStartExecutor = Executors.newSingleThreadExecutor();
    }

    protected String getBridgeName() {
        return _name;
    }

    protected boolean needTagBridgeName() {
        return _jmsbridge.tagBridgeName();
    }

    protected boolean logMessageTransfer() {
        return _jmsbridge.logMessageTransfer();
    }

    protected void tagBridgeName(Message msg, Message holder) throws Exception {

        Enumeration en = msg.getPropertyNames();
        String key = null;
        Object value = null;
        while (en != null && en.hasMoreElements()) {
            key = (String)en.nextElement();
            value = msg.getObjectProperty(key);
            holder.setObjectProperty(key, value);
        }

        msg.clearProperties();

        Exception ex = null;
        en = holder.getPropertyNames();
        while (en != null && en.hasMoreElements()) {
            key = (String)en.nextElement();
            try {
                 value = holder.getObjectProperty(key);
                 msg.setObjectProperty(key, value);
            } catch (Exception e) {
                 if (ex != null) ex = e;
            }
        }
        if (ex != null)  {
            throw ex;
        }
        msg.setStringProperty(BRIDGE_NAME_PROPERTY, _name);
    }

    private boolean createLink(LinkElement el, JMSBridgeElement eb) throws Exception {
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_LINK_FOR_JMSBRIDGE, el.getName(), eb.getName()));

        if (_links.get(el.getName()) != null) {
            throw new IllegalArgumentException(_jbr.getKString(
            _jbr.X_LINK_ALREADY_EXIST, el.getName(), eb.getName()));
        }

        Properties esource = el.getSource();
        String scfref = esource.getProperty(JMSBridgeXMLConstant.Source.CFREF);
        Object scf = _allCF.get(scfref);
        if (scf == null) {
            scf = createConnectionFactory(eb.getCF(scfref), el.isTransacted());
            if (scf != null) {
                _allCF.put(scfref, scf);
            }
        }
        String dref = esource.getProperty(JMSBridgeXMLConstant.Source.DESTINATIONREF);
        Object sd  = createDestination(eb.getDestination(dref));
    

        Properties etarget = el.getTarget().getAttributes();
        String tcfref = etarget.getProperty(JMSBridgeXMLConstant.Target.CFREF);
        Object tcf = _allCF.get(tcfref);
        if (tcf == null) {
            tcf = createConnectionFactory(eb.getCF(tcfref), el.isTransacted());
            if (tcf != null) {
                _allCF.put(tcfref, tcf);
            }
        }
        dref = etarget.getProperty(JMSBridgeXMLConstant.Target.DESTINATIONREF);

        Object td = null;
        if (!dref.equals(JMSBridgeXMLConstant.Target.DESTINATIONREF_AS_SOURCE)) {
            td  = createDestination(eb.getDestination(dref));
        } else {
            td = JMSBridgeXMLConstant.Target.DESTINATIONREF_AS_SOURCE;
        }

        if (scf == null) {
            if (el.isTransacted()) {
                scf = _localXACFs.get(scfref);
                if (scf == null) {
                    scf = new XAConnectionFactoryImpl(_bc, eb.getCF(scfref).getProperties(),
                                          _bc.isEmbeded(), scfref, eb.getCF(scfref).isMultiRM());
                    _localXACFs.put(scfref, scf);
                }
            } else {
                scf = _localCFs.get(scfref);
                if (scf == null) {
                    scf = new ConnectionFactoryImpl(_bc, eb.getCF(scfref).getProperties(),
                                                    _bc.isEmbeded(), scfref);
                    _localCFs.put(scfref, scf);
                }
            }
            _allCF.put(scfref, scf);
        }
        if (tcf == null) {
            if (el.isTransacted()) {
                tcf = _localXACFs.get(tcfref);
                if (tcf == null) {
                    tcf = new XAConnectionFactoryImpl(_bc, eb.getCF(tcfref).getProperties(),
                                          _bc.isEmbeded(), tcfref, eb.getCF(tcfref).isMultiRM());
                    _localXACFs.put(tcfref, tcf);
                }
            } else {
                tcf = _localCFs.get(tcfref);
                if (tcf == null) {
                    tcf = new ConnectionFactoryImpl(_bc, eb.getCF(tcfref).getProperties(),
                                                    _bc.isEmbeded(), tcfref);
                    _localCFs.put(tcfref, tcf);
                }
            }
            _allCF.put(tcfref, tcf);
        }

        if (!(((Refable)scf).getRefed().getClass().getName().startsWith("com.sun.messaging") ||
             ((Refable)tcf).getRefed().getClass().getName().startsWith("com.sun.messaging"))) { 
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_FOREIGNERS_NO_SUPPORT,
                                               ((Refable)scf).getRefed().getClass().getName(),
                                               ((Refable)tcf).getRefed().getClass().getName()));                      
        }
        if ((scf instanceof ConnectionFactory) &&
            (tcf instanceof XAConnectionFactory)) {
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_SOURCE_NONXA_TARGET_XA, scfref, tcfref)); 
        }
        if ((scf instanceof XAConnectionFactory) &&
            (tcf instanceof ConnectionFactory)) {
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_SOURCE_XA_TARGET_NONXA, scfref, tcfref)); 
        }

        Properties srcAttrs = el.getSource();
        Properties tgtAttrs = el.getTarget().getAttributes();
        Properties tgtProps = el.getTarget().getProperties();
        
        Link l = new Link();
        l.setName(el.getName());
        l.setLogger(_logger);
        l.setSourceConnectionFactory(scf);
        l.setTargetConnectionFactory(tcf);
        l.setSourceDestination(sd);
        l.setTargetDestination(td);
        l.init(el.getAttributes(),  srcAttrs, tgtAttrs, tgtProps, this);
        _links.put(el.getName(), l);

        return (scf instanceof XAConnectionFactory);
        
    }

    private void createDMQ(DMQElement edmq, JMSBridgeElement eb) throws Exception {
        _logger.log(Level.INFO, _jbr.getKString(
            _jbr.I_CREATE_DMQ_FOR_JMSBRIDGE, edmq.getName(), eb.getName()));

        if (_dmqs.get(edmq.getName()) != null) {
            throw new IllegalArgumentException(
            _jbr.getKString(_jbr.X_DMQ_ALREADY_EXIST, edmq.getName(), eb.getName()));
        }

        String cfref = edmq.getCFRef();
        Object cf = _allCF.get(cfref);
        if (cf ==  null) {
            cf = createConnectionFactory(eb.getCF(cfref), false);
        }
        Object dest  = createDestination(eb.getDestination(edmq.getDestinationRef()));
    
        if (cf == null) {
            cf = _localCFs.get(cfref);
            if (cf == null) {
                cf = new ConnectionFactoryImpl(_bc, eb.getCF(cfref).getProperties(),
                                               _bc.isEmbeded(), cfref);
                _localCFs.put(cfref, cf);
            }
        }
        if (cf instanceof XAConnectionFactory) { 
            String[] eparam = {"XAConnectionFactory", cf.getClass().getName(), cfref, edmq.getName()};
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_DMQ_XACF_NOT_SUPPORT, eparam));
        }

        //Properties dmqAttrs = edmq.getAttributes();
        DMQ dmq = new DMQ();
        dmq.setName(edmq.getName());
        dmq.setLogger(_logger);
        dmq.setConnectionFactory(cf);
        dmq.setDestination(dest);
        dmq.init(edmq.getAttributes(), edmq.getProperties(), this);
        _dmqs.put(edmq.getName(), dmq);
        
    }

    private void createBuiltInDMQ(JMSBridgeElement eb) throws Exception {
        DMQElement edmq = eb.getBuiltInDMQ();
        String name = edmq.getName();
        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_BUILTIN_DMQ, name, eb.getName()));

        Object dest = new AutoDestination(DMQElement.BUILTIN_DMQ_DESTNAME, true); 
        Object cf = new ConnectionFactoryImpl(_bc, edmq.getProperties(), true, 
                                              _bc.isEmbeded(),
                                              DMQElement.BUILTIN_DMQ_NAME);
        if (cf instanceof XAConnectionFactory) { 
            String[] eparam = {"XAConnectionFactory", cf.getClass().getName(), name, name}; 
            throw new IllegalArgumentException(_jbr.getKString(_jbr.X_DMQ_XACF_NOT_SUPPORT, eparam));
        }
        DMQ dmq = new DMQ();
        dmq.setName(name);
        dmq.setLogger(_logger);
        dmq.setConnectionFactory(cf);
        dmq.setDestination(dest);
        dmq.init(edmq.getAttributes(), edmq.getProperties(), this);
        _dmqs.put(name, dmq);
    }

    private Object createConnectionFactory(ConnectionFactoryElement ecf,
                                           boolean transacted) 
                                           throws Exception {
        Properties props = ecf.getProperties();
        if (props == null) props = new Properties();

        String lookup = ecf.getLookupName();
        if (lookup == null) { 
            return null;
        }

        _logger.log(Level.INFO,  _jbr.getString(_jbr.I_JNDI_LOOKUP_CF, lookup, ecf.getRefName()));
        InitialContext cxt = new InitialContext(props);
        Object o = cxt.lookup(lookup);
        if (o == null) {
            String[] eparam = {lookup, JMSBridgeXMLConstant.Element.CF, ecf.getRefName()};
            throw new javax.naming.NamingException(_jbr.getKString(_jbr.X_LOOKUP_RETURN_NULL, eparam));
        }
        if (o instanceof XAConnectionFactory) {
            if (!transacted) {
                String[] eparam = {"ConnectionFactory", ecf.getRefName(), o.getClass().getName()};
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_CF_TYPE_LOOKUP_MISMATCH, eparam));
            }
            return new XAConnectionFactoryImpl((XAConnectionFactory)o, 
                                               ecf.getRefName(), ecf.isMultiRM()); 
        } else if (o instanceof ConnectionFactory) {
            if (transacted) {
                String[] eparam = {"XAConnectionFactory", ecf.getRefName(), o.getClass().getName()};
                throw new NotXAConnectionFactoryException(_jbr.getKString(_jbr.X_CF_TYPE_LOOKUP_MISMATCH, eparam));
            }
            return new ConnectionFactoryImpl((ConnectionFactory)o, ecf.getRefName()); 
        }
        if (transacted) {
            String[] eparam = {"XAConnectionFactory", ecf.getRefName(), o.getClass().getName()};
            throw new NotXAConnectionFactoryException(_jbr.getKString(_jbr.X_CF_TYPE_LOOKUP_MISMATCH, eparam));
        }
        String[] eparam = {"ConnectionFactory", ecf.getRefName(), o.getClass().getName()};
        throw new IllegalArgumentException(_jbr.getKString(_jbr.X_CF_TYPE_LOOKUP_MISMATCH, eparam));
    }

    public Object createDestination(String dref) throws Exception {
        return createDestination(_jmsbridge.getDestination(dref));
    }

    private Object createDestination(DestinationElement ed) throws Exception {
        Properties props = ed.getProperties();
        if (props == null) props = new Properties();

        String lookup = ed.getLookupName();
        if (lookup == null) { 
            String name = ed.getName();
            if (name == null) {
                String[] eparam = { JMSBridgeXMLConstant.Destination.LOOKUPNAME,
                                    JMSBridgeXMLConstant.Destination.NAME,
                                    ed.getRefName(), _xmlurl};
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_DEST_NO_NAME_NO_LOOKUP, eparam));
            }
            return new AutoDestination(name, ed.isQueue());
        }
        _logger.log(Level.INFO,  _jbr.getKString(_jbr.I_JNDI_LOOKUP_DEST, lookup, ed.getRefName()));
        InitialContext cxt = new InitialContext(props);
        Destination o = (Destination)cxt.lookup(lookup);
        if (o == null) {
            String[] eparam = {lookup, JMSBridgeXMLConstant.Element.DESTINATION, ed.getRefName()};
            throw new javax.naming.NamingException(_jbr.getKString(_jbr.X_LOOKUP_RETURN_NULL, eparam));
        }
        return o;
    }

    /**
     * @param cmd I18Ned string
     */
    private void checkStartFuture(String cmd,  String linkName, boolean cancelWait) throws Exception {
        synchronized (_startFutureLock) {
            if (_startFutures.size() > 0) {
                
                String[] keys = (String[])_startFutures.keySet().toArray(
                                    new String[_startFutures.size()]);
                Future future = (Future)_startFutures.get(keys[0]);
                if (future.isDone()) {
                    _startFutures.remove(keys[0]);
                    checkStartFuture(cmd, linkName, cancelWait);
                }

            
                String oldreq = _jbr.getString(_jbr.M_START)+((keys[0].equals(BRIDGE_NAME_PROPERTY) ? 
                                                              (" "+_jbr.getString(_jbr.M_BRIDGE)+" "+_name):
                                                              (" "+_jbr.getString(_jbr.M_LINK)+" "+keys[0])));
                String newreq = cmd+(linkName == null ? 
                                     (" "+_jbr.getString(_jbr.M_BRIDGE)+" "+_name):
                                     (" "+_jbr.getString(_jbr.M_LINK)+" "+linkName));
                if (cancelWait) {
                   if (future.cancel(true)) {
                       _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_ASYNC_CMD_CANCELED, oldreq)); 
                   }
                }

                String emsg = _jbr.getKString(_jbr.W_ASYNC_CMD_IN_PROCESS, newreq, oldreq);
                _logger.log(Level.WARNING, emsg); 
                throw new RejectedExecutionException(emsg);
            }
            return;
        }
    }

    /**
     *
     * @return true if successfully started; false if started asynchronously
     *
     * @throws Exception if start failed
     */
    public boolean start(String linkName, AsyncStartListener asl) throws Exception {
        synchronized (_startFutureLock) {
            String cmd = _jbr.getString(_jbr.M_START);
            checkStartFuture(cmd, linkName, false);

            Starter starter = new Starter(linkName, this, asl);
            try {
                starter.setAsync(false); 
                starter.call();
                return true;
            } catch (Exception e) {
                if (!(e.getCause() instanceof ProviderConnectException ||
                      e instanceof ProviderConnectException)) {
                    throw e;
                }
            }

            String req = cmd+(linkName == null ? 
                              (" "+_jbr.getString(_jbr.M_BRIDGE)+" "+_name):
                              (" "+_jbr.getString(_jbr.M_LINK)+" "+linkName));
            _logger.log(Level.INFO, _jbr.getKString(_jbr.I_START_ASYNC, req, "ProviderConnectException"));

            starter.setAsync(true); 
            Future future = _asyncStartExecutor.submit(starter);
            if (linkName != null) {
                _startFutures.put(linkName, future);
            } else {
                _startFutures.put(BRIDGE_NAME_PROPERTY, future);
            }
            try {
                 _startLatch.await();
            } catch (InterruptedException e) {
                 _logger.log(Level.WARNING, "Waiting for async start task to run interrupted ");
                throw e;
            }
            return false;
        }
    }

    class Starter implements Callable <Void> {
        private String linkName = null;
        private boolean async = true;
        private JMSBridge parent = null;
        private AsyncStartListener asl = null;

        public Starter(String linkName, JMSBridge parent, AsyncStartListener asl) {
            this.linkName = linkName; 
            this.parent = parent;
            this.asl = asl;
        }

        public void setAsync(boolean b) {
            async = b;
        }

        public Void call() throws Exception {

        try {

        synchronized (parent) {

        if (async) {
            _startLatch.countDown();
        }

        if (linkName != null)  {
            Link l = _links.get(linkName);
            if (l == null) {
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_NOT_FOUND, linkName, _name));
            }
            l.start(async);
            l.postStart();
            return null;
        }

        DMQ dmq = null;
        for (Map.Entry<String, DMQ> pair: _dmqs.entrySet()) {
            dmq = pair.getValue();
            try {
                if (dmq.isEnabled()) {
                    dmq.start(async);
                }
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_START_DMQ, dmq.toString(), _name), e);
                try {
                internalStop(null, (!async && (e instanceof ProviderConnectException)));
                } catch (Exception e1) {
                _logger.log(Level.WARNING,
                        _jbr.getKString(_jbr.W_STOP_BRIDGE_FAILED_AFTER_START_DMQ_FAILURE,_name), e1);
                }
                throw e;
            }
        }

        ArrayList<Link> list = new ArrayList<Link>();
        Link link = null;
        for (Map.Entry<String, Link> pair: _links.entrySet()) {
            link = pair.getValue();
            try {
                if (link.isEnabled()) {
                    link.start(async);
                    list.add(link);
                }
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_START_LINK, link.toString(), _name), e);        
                try {
                internalStop(null, (!async && (e.getCause() instanceof ProviderConnectException ||
                                               e instanceof ProviderConnectException)));
                } catch (Exception e1) {
                _logger.log(Level.WARNING,
                        _jbr.getKString(_jbr.W_STOP_BRIDGE_FAILED_AFTER_START_LINK_FAILURE,_name), e1);
                }
                throw e;
            }
        }

        for (Link l: list) {
            try {
                l.postStart();
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_POSTSTART_LINK, l.toString()), e);
                try {
                internalStop(null, (!async && (e.getCause() instanceof ProviderConnectException ||
                                               e instanceof ProviderConnectException)));
                } catch (Exception e1) {
                _logger.log(Level.WARNING, 
                        _jbr.getKString(_jbr.W_STOP_BRIDGE_FAILED_AFTER_POSTSTART_LINK_FAILURE, _name), e1);
                }
                throw e;
            } 
        }

        } //synchronized 

        try {
            if (async) {
                asl.asyncStartCompleted();
            }
        } catch (Exception e) {
            _logger.log(Level.SEVERE, e.getMessage());
            try {
            internalStop(null, false);
            } catch (Exception e1) {
            _logger.log(Level.WARNING, "Stop bridge "+_name+" failed: "+e1.getMessage(), e1);
            }
            throw e;
        }

        } catch (Exception e) {
            if (async) {
                asl.asyncStartFailed();
            }
            throw e;
        } finally {
            synchronized (_startFutureLock) {
                if (linkName != null) {
                   _startFutures.remove(linkName);
                } else {
                   _startFutures.remove(BRIDGE_NAME_PROPERTY);
                }
            }
        }
        return null;
        } //call()
    }

    public  ArrayList<BridgeCmdSharedReplyData> list(String linkName, ResourceBundle rb, boolean all) throws Exception {

        ArrayList<BridgeCmdSharedReplyData> replys = new ArrayList<BridgeCmdSharedReplyData>();

        BridgeCmdSharedReplyData reply = new BridgeCmdSharedReplyData(5, 3, "-");
        String oneRow[] = new String [5];
        oneRow[0] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_LINK_NAME);
        oneRow[1] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_LINK_STATE);
        oneRow[2] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_SOURCE);
        oneRow[3] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_TARGET);
        oneRow[4] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_TRANSACTED);
        reply.addTitle(oneRow);

        Link l = null;
        if (linkName != null) {
            l = _links.get(linkName);
            if (l == null || !l.isEnabled()) {
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_NOT_FOUND, linkName, _name));
            }
            oneRow[0] = l.getName();
            oneRow[1] = l.getState().toString();
            oneRow[2] = l.getSourceString();
            oneRow[3] = l.getTargetString();
            oneRow[4] = String.valueOf(l.isTransacted());
            reply.add(oneRow);
            replys.add(reply);

            if (!all) {
                return replys;
            }

            if (l.isTransacted()) {
                BridgeCmdSharedReplyData rep = new BridgeCmdSharedReplyData(1, 3, "-");
                String toneRow[] = new String [1];
                toneRow[0] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_TRANSACTIONS);
                rep.addTitle(toneRow);
                if (_tma != null) { 
                    try {

                    String[] txns = _tma.getAllTransactions();
                    if (txns != null) {
                        for (int i = 0; i <  txns.length; i++) {
                            toneRow[0] = txns[i];
                            rep.add(toneRow);
                        }
                    }

                    } catch (Exception e) {
                    _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_FAILED_GET_ALL_TXNS, _name));
                    }
                }
                replys.add(rep);
            }
            return replys;
        }

        Link[] ls = null;
        synchronized(_links) {
            ls = _links.values().toArray(new Link[_links.size()]);
        }

        for (int i = 0; i < ls.length; i++) {
            l = ls[i];
            if (!l.isEnabled()) continue;
            oneRow[0] = l.getName();
            oneRow[1] = l.getState().toString();
            oneRow[2] = l.getSourceString();
            oneRow[3] = l.getTargetString();
            oneRow[4] = String.valueOf(l.isTransacted());
            reply.add(oneRow);
        }
        replys.add(reply);

        if (!all) {
            return replys;
        }

        BridgeCmdSharedReplyData pcfreply = new BridgeCmdSharedReplyData(7, 3, "-", BridgeCmdSharedReplyData.CENTER);
        pcfreply.setTitleAlign(BridgeCmdSharedReplyData.CENTER);
        String poneRow[] = new String [7];
        poneRow[0] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_POOLED);
        poneRow[1] = "XA";
        poneRow[2] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_NUM_INUSE);
        poneRow[3] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_NUM_IDLE);
        poneRow[4] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_IDLE);
        poneRow[5] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_MAX);
        poneRow[6] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_RETRY);
        pcfreply.addTitle(poneRow);
        poneRow[0] = "ConnectionFactory";
        poneRow[1] = "";
        poneRow[2] = "";
        poneRow[3] = "";
        poneRow[4] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_TIMEOUT);
        poneRow[5] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_RETRIES);
        poneRow[6] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_INTERVAL);
        pcfreply.addTitle(poneRow);

        BridgeCmdSharedReplyData scfreply = new BridgeCmdSharedReplyData(6, 3, "-", BridgeCmdSharedReplyData.CENTER);
        scfreply.setTitleAlign(BridgeCmdSharedReplyData.CENTER);
        String soneRow[] = new String [6];
        soneRow[0] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_SHARED);
        soneRow[1] = "XA";
        soneRow[2] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_REF);
        soneRow[3] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_IDLE);
        soneRow[4] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_MAX);
        soneRow[5] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_RETRY);
        scfreply.addTitle(soneRow);
        soneRow[0] = "ConnectionFactory";
        soneRow[1] = "";
        soneRow[2] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_COUNT);
        soneRow[3] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_TIMEOUT);
        soneRow[4] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_RETRIES);
        soneRow[5] = rb.getString(BridgeCmdSharedResources.I_BGMGR_TITLE_INTERVAL);
        scfreply.addTitle(soneRow);

        Object[] spcfs = null;
        synchronized(_spcfs) {
            spcfs = _spcfs.values().toArray();
        }
        PooledConnectionFactory pcf = null;
        SharedConnectionFactory scf = null;
        for (int i = 0; i < spcfs.length; i++) {
            if (spcfs[i] instanceof PooledConnectionFactory) {
                pcf = (PooledConnectionFactory)spcfs[i];
                poneRow[0] = ((Refable)pcf.getCF()).getRef();
                poneRow[1] = String.valueOf(pcf.getCF() instanceof XAConnectionFactory);
                poneRow[2] = String.valueOf(pcf.getNumInUseConns());
                poneRow[3] = String.valueOf(pcf.getNumIdleConns());
                poneRow[4] = String.valueOf(pcf.getIdleTimeout());
                poneRow[5] = String.valueOf(pcf.getMaxRetries());
                poneRow[6] = String.valueOf(pcf.getRetryInterval());
                pcfreply.add(poneRow);
                
            } else if (spcfs[i] instanceof SharedConnectionFactory) {
                scf = (SharedConnectionFactory)spcfs[i];
                soneRow[0] = ((Refable)scf.getCF()).getRef();
                soneRow[1] = String.valueOf(scf.getCF() instanceof XAConnectionFactory);
                soneRow[2] = String.valueOf(scf.getRefCount());
                soneRow[3] = String.valueOf(scf.getIdleTimeout());
                soneRow[4] = String.valueOf(scf.getMaxRetries());
                soneRow[5] = String.valueOf(scf.getRetryInterval());
                scfreply.add(soneRow);
            }
        }
        
        replys.add(pcfreply);
        replys.add(scfreply);
        return replys;
    }

    public int getNumLinks() {

        Link[] ls = null;
        synchronized(_links) {
            ls = _links.values().toArray(new Link[_links.size()]);
        }

        int n = 0;
        for (int i = 0; i < ls.length; i++) {
            if (!ls[i].isEnabled()) continue;

            n++; 
        }
        return n;
    }

    public void pause(String linkName) throws Exception {
        String cmd = _jbr.getString(_jbr.M_PAUSE);
        checkStartFuture(cmd, linkName, false);
        internalPause(linkName);
    }

    private synchronized void internalPause(String linkName) throws Exception {
        if (linkName != null)  {
            Link l = _links.get(linkName);
            if (l == null) {
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_NOT_FOUND, linkName, _name));
            }
            l.pause();
            return;
        }

        for (Map.Entry<String, Link> pair: _links.entrySet()) {
            try {
                pair.getValue().pause();
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_PAUSE_LINK, pair.getKey(), _name), e);
                throw e;
            }
        }
    }

    public void resume(String linkName) throws Exception {
        String cmd = _jbr.getString(_jbr.M_RESUME);
        checkStartFuture(cmd, linkName, false);
        internalResume(linkName);
    }

    private synchronized void internalResume(String linkName) throws Exception {

        if (linkName != null)  {
            Link l = _links.get(linkName);
            if (l == null) {
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_NOT_FOUND, linkName, _name));
            }
            l.resume(true);
            return;
        }

        for (Map.Entry<String, Link> pair: _links.entrySet()) {
            try {
                pair.getValue().resume(true);
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_RESUME_LINK, pair.getKey(), _name), e);
                throw e;
            }
        }
    }


    public void stop(String linkName) throws Exception {
        String cmd = _jbr.getString(_jbr.M_STOP);
        checkStartFuture(cmd, linkName, true);
        internalStop(linkName, false);
    }

    public synchronized void internalStop(String linkName, boolean stayInited) throws Exception {

        if (linkName != null)  {
            Link l = _links.get(linkName);
            if (l == null) {
                throw new IllegalArgumentException(_jbr.getKString(_jbr.X_LINK_NOT_FOUND, linkName, _name));
            }
            l.stop(stayInited);
            return;
        }

        _notifier.notifyEvent(EventListener.EventType.BRIDGE_STOP, this);

        for (Map.Entry<String, Link> pair: _links.entrySet()) {
            try {
                _notifier.notifyEvent(EventListener.EventType.BRIDGE_STOP, this);
                pair.getValue().stop();
            } catch (Exception e) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_STOP_LINK, pair.getKey(), _name), e);
            }
        }

        for (Map.Entry<String, DMQ> pair: _dmqs.entrySet()) {
            try {
                _notifier.notifyEvent(EventListener.EventType.BRIDGE_STOP, this);
                pair.getValue().stop();
            } catch (Throwable t) {
                _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_STOP_DMQ, pair.getKey(), _name), t);
            }
        }

        for (Map.Entry<Object, Object> pair: _spcfs.entrySet()) {
            try {
                Object o = pair.getValue();
                if (o instanceof PooledConnectionFactory) {
                    ((PooledConnectionFactory)o).close();
                } else if (o instanceof SharedConnectionFactory) {
                    ((SharedConnectionFactory)o).close();
                }
            } catch (Exception e) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_FAILED_CLOSE_CF, pair.getKey()), e);
            }
        }

        _spcfs.clear();

        if (stayInited) return;

        synchronized(_initLock) {
            if (_tma != null) {
                try { 
                _tma.shutdown();
                } catch (Throwable t) {
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_EXCEPTION_SHUTDOWN_TM, t.getMessage()), t);
                }
            }
        }
    }

    public Connection obtainConnection(Object cf, 
                                       String logstr,
                                       Object caller)
                                       throws Exception {
        return obtainConnection(cf, logstr, caller, false);
    }

    public Connection obtainConnection(Object cf, 
                                       String logstr,
                                       Object caller, boolean doReconnect)
                                       throws Exception {
        Object spcf = null;
        Connection c = null;
        synchronized(_spcfs) {
            spcf = _spcfs.get(cf);
            if (spcf == null) {
                Properties attrs = _jmsbridge.getCF(((Refable)cf).getRef()).getAttributes();
                EventListener l = new EventListener(caller);
                try {
                _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, l);
                _notifier.addEventListener(EventListener.EventType.LINK_STOP, l);
                c = openConnection(cf, attrs, logstr, caller, l, _logger);
                } finally {
                _notifier.removeEventListener(l);
                }
                try {
                    if (c.getClientID() == null) {
                        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_POOLED_CF, ((Refable)cf).getRef()));
                        spcf = new PooledConnectionFactory(cf, attrs, _logger); 
                    } else {
                        _logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATE_SHARED_CF, ((Refable)cf).getRef()));
                        spcf = new SharedConnectionFactory(cf, attrs, _logger);
                    }
                    _spcfs.put(cf, spcf);
                } catch (Exception e) {
                    _logger.log(Level.SEVERE, _jbr.getKString(_jbr.E_EXCEPTION_CREATE_CF, ((Refable)cf).getRef(), e.getMessage())); 
                    try {
                    c.close();
                    c = null;
                    } catch (Exception e1) {
                    _logger.log(Level.FINE, 
                    "Exception in closing connection from connection factory"+ 
                     ((Refable)cf).getRef()+": "+e1.getMessage()); 
                    }
                    throw e;
                }
            }
        }
        if (spcf instanceof PooledConnectionFactory) {
            String[] param = {spcf.toString(), logstr, caller.toString()};
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_GET_POOLED_CONN, param));
            return ((PooledConnectionFactory)spcf).obtainConnection(c, logstr, caller, doReconnect);
        } else {
            String[] param = {spcf.toString(), logstr, caller.toString()};
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_GET_SHARED_CONN, param));
            return ((SharedConnectionFactory)spcf).obtainConnection(c, logstr, caller, doReconnect);
        }
    }

    public void returnConnection(Connection c, Object cf) throws Exception {
        Object spcf = _spcfs.get(cf);
        if (spcf == null) {
            throw new IllegalStateException(
            "Nowhere to return connection "+c+" from connection factory "+cf);
        }
        if (spcf instanceof PooledConnectionFactory) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_RETURN_POOLED_CONN, spcf.toString()));
            ((PooledConnectionFactory)spcf).returnConnection(c);
        } else if (spcf instanceof SharedConnectionFactory) {
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_RETURN_SHARED_CONN, spcf.toString()));
            ((SharedConnectionFactory)spcf).returnConnection(c);
        }
    }

    /**
     *
     * @param mid the JMSMessageID from msg, for convenient logging purpose
     */
    public void toDMQ(Message msg, String mid,
                      DMQ.DMQReason reason, 
                      Throwable ex, Link l) throws Exception {

        DMQ.logMessage(msg, mid, l, _logger);

        DMQ[] dmqs = null;
        synchronized(_dmqs) {
            dmqs =  _dmqs.values().toArray(new DMQ[_dmqs.size()]);
        }

        Exception ee = null;
        boolean sent = false;
        for (int i = 0; i < dmqs.length; i++) {
             String[] param = {mid, dmqs[i].toString(), l.toString()};
             try {
                 _logger.log(Level.INFO, _jbr.getString(_jbr.I_SEND_MSG_TO_DMQ, param)); 
                 dmqs[i].sendMessage(msg, mid, reason, ex, l);
                 sent = true;
                 _logger.log(Level.INFO, _jbr.getString(_jbr.I_SENT_MSG_TO_DMQ, param));
                 if (dmqs[i].getName().equals(DMQElement.BUILTIN_DMQ_NAME)) {
                     continue;
                 } else {
                     break;
                 }

             } catch (Exception e) {
                  ee = e;
                 _logger.log(Level.WARNING, _jbr.getString(_jbr.W_SEND_MSG_TO_DMQ_FAILED, param), e);
                 continue;
             }
        }
        if (sent) return;
        throw ee;
    }

    protected Properties getCFAttributes(Object cf) throws Exception {
        if (((Refable)cf).getRef().equals(DMQElement.BUILTIN_DMQ_NAME)) {
            return new Properties();
        }
        return _jmsbridge.getCF(((Refable)cf).getRef()).getAttributes();
    }

    public static Connection openConnection(Object cf,
                                            Properties attrs,
                                            String logstr, Object caller,
                                            EventListener l, Logger logger)
                                            throws Exception {
        return openConnection(cf, attrs, logstr, caller, l, logger, false);
    }

    /**
     * @param cf wrapped connection factory
     * @param logstr for logging purpose
     * @param caller for logging purpose
     */
    public static Connection openConnection(Object cf,
                                            Properties attrs,
                                            String logstr, Object caller,
                                            EventListener l, Logger logger, boolean doReconnect)
                                            throws Exception {
        int maxAttempts = 0;
        long attemptInterval = 0;

        String val = attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTS,
                                       JMSBridgeXMLConstant.CF.CONNECTATTEMPTS_DEFAULT);
        if (val != null) {
            maxAttempts = Integer.parseInt(val);
        }
        val = attrs.getProperty(JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL,
                                JMSBridgeXMLConstant.CF.CONNECTATTEMPTINTERVAL_DEFAULT);
        if (val != null) {
            attemptInterval = Long.parseLong(val);
        }
        if (attemptInterval < 0) attemptInterval = 0;
        attemptInterval = attemptInterval*1000;

        String username = null, password = null;
        val = attrs.getProperty(JMSBridgeXMLConstant.CF.USERNAME);
        if (val != null) { 
            username = val.trim();
            password = attrs.getProperty(JMSBridgeXMLConstant.CF.PASSWORD);
        }

        return openConnection(cf, maxAttempts, attemptInterval, username, password, 
                              logstr, caller, l, logger, doReconnect);
    }

    public static Connection openConnection(Object cf, 
                                            int maxAttempts, 
                                            long attemptInterval, 
                                            String username, String password,
                                            String logstr, Object caller,
                                            EventListener l, Logger logger) 
                                            throws Exception {
        return openConnection(cf, maxAttempts, attemptInterval, 
                              username, password, logstr, caller, l, logger, false);
    }

    public static Connection openConnection(Object cf, 
                                            int maximumAttempts, 
                                            long attemptInterval, 
                                            String username, String password,
                                            String logstr, Object caller,
                                            EventListener l, Logger logger, boolean doReconnect) 
                                            throws Exception {
        if (l.hasEventOccurred()) {
            throw new JMSException(""+l.occurredEvent());
        }
        int maxAttempts = maximumAttempts;

        Connection conn = null;
        int attempts = 0;
        Exception ee = null;
        int invalidClientIDExceptionCnt = 0;
        do {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(_jbr.getKString(
                _jbr.X_OPENCONNECTION_INTERRUPTED, cf.toString(), caller.toString()));
            }
            if (attempts > 0 && attemptInterval > 0) {
                Thread.sleep(attemptInterval);
            }

            try {

                String[] param = { (username == null ? "":"["+username+"]"),
                                   cf.toString(), caller.toString() };

                if (cf instanceof XAConnectionFactory) {
                    logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATING_XA_CONN, param));
                    if (username == null) {
                        conn = ((XAConnectionFactory)cf).createXAConnection();
                    } else {
                        conn = ((XAConnectionFactory)cf).createXAConnection(username, password);
                    }
                } else {
                    logger.log(Level.INFO, _jbr.getString(_jbr.I_CREATING_CONN, param));
                    if (username == null) {
                        conn = ((ConnectionFactory)cf).createConnection();
                    } else {
                        conn = ((ConnectionFactory)cf).createConnection(username, password);
                    }
                }
                return conn;

            } catch (JMSException e) {
                attempts++;
                ee = e;
                Exception le = e.getLinkedException();

                if (e instanceof InvalidClientIDException) {
                    invalidClientIDExceptionCnt++;
                }
                if (e instanceof JMSSecurityException || le instanceof JMSSecurityException ||
                    (e instanceof InvalidClientIDException && invalidClientIDExceptionCnt > 1)) {
                    String[] eparam = {logstr, caller.toString(), attempts+"("+attemptInterval+")"};
                    _logger.log(Level.SEVERE, _jbr.getKString(_jbr.W_EXCEPTION_CREATING_CONN, eparam), e);
                    throw e;
                }
                
                String[] eparam = { logstr, caller.toString(), 
                                     attempts+"("+attemptInterval+"): "+e.getMessage()+(le == null ? "": " - "+le.getMessage()) };
                _logger.log(Level.WARNING, _jbr.getKString(_jbr.W_EXCEPTION_CREATING_CONN, eparam));
                if (!doReconnect && (maxAttempts > 1 || maxAttempts < 0)) {
                    throw new ProviderConnectException(
                    "Failed to connect to "+cf+": "+e.getMessage()+(le == null ? "": " - "+le.getMessage()));
                }
            }

        } while ((maxAttempts < 0 || attempts < maxAttempts) && !l.hasEventOccurred()) ;

        if (l.hasEventOccurred()) {
            throw new JMSException(""+l.occurredEvent());
        }

        throw ee;
    }
 
    public TransactionManager getTransactionManager() throws Exception {
        synchronized(_initLock) {
            if (_tm != null) return _tm;
            initTransactionManager();
            return _tm;
        }
    }

    public TransactionManagerAdapter getTransactionManagerAdapter() throws Exception {
        synchronized(_initLock) {
            if (_tma != null) return _tma;
            initTransactionManager();
            return _tma;
        }
    }

    private void initTransactionManager() throws Exception {

        synchronized(_initLock) {
            if (_tma != null) return;

            String c = _bc.getTransactionManagerClass();

            if (c == null) {
                c = "com.sun.messaging.bridge.service.jms.tx.TransactionManagerImpl";
            }

            _logger.log(Level.INFO, _jbr.getString(_jbr.I_USE_TM_ADAPTER_CLASS, c));
            Class cs = Class.forName(c);
            _tma = (TransactionManagerAdapter)cs.newInstance();
            _tma.setLogger(_logger);
            Properties props = _bc.getTransactionManagerProps();
            if (props == null) props = new Properties();
            if (_tma instanceof TransactionManagerImpl) {
                props.setProperty("tmname", _bc.getIdentityName()+":"+_name);
                props.setProperty("txlogDir", _bc.getRootDir());
                props.setProperty("txlogSuffix", _name);
                props.setProperty("jmsbridge", _name);
                if (_bc.isJDBCStoreType()) {
                    props.setProperty("txlogType", TxLog.JDBCTYPE);
                    ((TransactionManagerImpl)_tma).setJDBCStore(
                               (JMSBridgeStore)_bc.getJDBCStore(Bridge.JMS_TYPE));
                }
                _supportTransactionTimeout = false;
            }
            _logger.log(Level.INFO, _jbr.getString(_jbr.I_INIT_TM_WITH_PROPS, props.toString()));
            _tma.init(props, _reset);
            _tm = _tma.getTransactionManager();
            if (!(_tma instanceof TransactionManagerImpl)) {
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_SET_TM_TIMEOUT, _transactionTimeout));
                _tm.setTransactionTimeout(_transactionTimeout);
                _supportTransactionTimeout = true;
            }
        }
    }

    public int getTransactionTimeout() { 
        return _transactionTimeout; 
    }

    public boolean supportTransactionTimeout() {
        return _supportTransactionTimeout;
    }

    private void registerXAResources(Map<String, ? extends Object> cfs) throws Exception {

        TransactionManagerAdapter tma = getTransactionManagerAdapter();
        if (!tma.registerRM()) return;

        String cfref = null;
        Object cf = null;
        XAConnection conn = null;
        for (Map.Entry<String, ? extends Object> pair: cfs.entrySet()) {
            cfref = pair.getKey();
            cf = pair.getValue(); 
            if (!(cf instanceof XAConnectionFactory)) continue; 
            if (((Refable)cf).isMultiRM()) {
                _logger.log(Level.INFO,  _jbr.getString(_jbr.I_SKIP_REGISTER_MULTIRM_CF, 
                                         JMSBridgeXMLConstant.CF.MULTIRM, cfref)); 
                continue;
            }

            XAResource xar = null;
            EventListener l = new EventListener(this);
            try {
                 String username = _jmsbridge.getCF(((Refable)cf).getRef()).getUsername(); 
                 String password = _jmsbridge.getCF(((Refable)cf).getRef()).getPassword(); 
                _notifier.addEventListener(EventListener.EventType.BRIDGE_STOP, l);
                conn = (XAConnection)openConnection(cf, 1, 0, username, password, "", this, l, _logger);
                XASession ss = conn.createXASession();
                xar = ss.getXAResource();
                _logger.log(Level.INFO, _jbr.getString(_jbr.I_REGISTER_RM, cfref, xar.toString()));
                tma.registerRM(cfref, xar);
            } catch (Throwable  t) {
                _logger.log(Level.WARNING, _jbr.getKString(
                        _jbr.W_REGISTER_RM_ATTEMPT_FAILED, cfref, (xar == null ? "":xar.toString())), t);
            } finally {
                _notifier.removeEventListener(l);
                try {
                conn.close();
                } catch (Exception e) {};
            }
        }
    }

    public String toString() {
        return JMSBridgeXMLConstant.Element.JMSBRIDGE+"("+_name+")";
    }

    public static Object exportJMSBridgeStoreService(Properties props) throws Exception {
        String bname = props.getProperty("jmsbridge");
        String instanceRootDir = props.getProperty("instanceRootDir");
        String reset = props.getProperty("reset", "true");
        String logdomain = props.getProperty("logdomain");
        if (instanceRootDir == null) {
            throw new IllegalArgumentException("instanceRootDir not found in "+props);
        }
        if (logdomain == null) {
            throw new IllegalArgumentException("logdomain property not found in "+props);
        }
        String rootDir = instanceRootDir+File.separator+"bridges";
        props.setProperty("txlogDirParent", rootDir);

        boolean doreset = Boolean.valueOf(reset).booleanValue();

        File dir =  new File(rootDir);
        if (doreset && bname == null) {
            if (dir.exists()) {
                if (!dir.renameTo(new File(rootDir+".save"))) {
                    throw new IOException(
                    "Unable to rename existing directory "+rootDir+" to "+rootDir+".save");
                }
            }
            return null;
        }

        if (bname == null) {
            if (!dir.exists()) {
                return null;
            }
            File[] files = dir.listFiles();
            if (files == null) {
                throw new IOException("Can't list files in "+rootDir);
            }
            if (files.length == 0) return null;
            for (int i = 0; i < files.length; i++) { 
                if (files[i].isDirectory()) {     
                    bname = files[i].getName();
                    break;
                }
            }
            if (bname == null) return null;
            
            props.setProperty("jmsbridge", bname);
        }

        if (!dir.exists()) dir.mkdirs();

        Logger logger = Logger.getLogger(logdomain);
        props.setProperty("txlogDir", rootDir);

        props.setProperty("tmname", props.getProperty("identityName")+":"+bname);
        props.setProperty("txlogSuffix", bname);
        String txlogDir = rootDir+File.separator+bname; 
        props.setProperty("txlogDir", txlogDir);

        dir =  new File(txlogDir);
        if (!dir.exists())  dir.mkdirs();


        String logfile = dir+File.separator+"jms%g.log";
        FileHandler h =  new FileHandler(logfile, true);
        h.setFormatter(new LogSimpleFormatter(logger));
        logger.addHandler(h);

        logger.log(Level.INFO, "Exported JMSBridgeStore txlogDir is "+txlogDir);
        logger.log(Level.INFO, "Exported JMSBridgeStore uses log domain: "+logdomain);
        logger.log(Level.INFO, "Exported JMSBridgeStore uses log file: "+logfile);
        
        FileTxLogImpl txlog = new FileTxLogImpl();
        txlog.setLogger(logger);
        txlog.init(props, doreset);
        return txlog;
    }

    public static JMSBridgeResources getJMSBridgeResources() {
        if (_jbr == null) {
            synchronized(JMSBridge.class) {
                if (_jbr == null) {
                    _jbr = JMSBridgeResources.getResources(Locale.getDefault());
                }
            }
        }
        return _jbr;
    }

}
