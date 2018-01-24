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
 * @(#)Agent.java	1.63 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.agent;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Iterator;
import java.util.Vector;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.lang.reflect.Method;
import java.lang.management.MemoryUsage;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import javax.management.*;
import javax.management.remote.*;
import javax.management.loading.MLet;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.management.mbeans.*;
import com.sun.messaging.jmq.jmsserver.management.util.*;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.cluster.api.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.util.UID;
import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.jmsserver.data.handlers.admin.GetClusterHandler;

import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.jmq.jmsserver.management.mbeans.*;

/**
 * The Main class for the broker JMX Agent.
 *
 */
public class Agent {
    
    private static boolean DEBUG = Globals.getConfig().getBooleanProperty(
	    Globals.IMQ + ".jmx.debug.all");

    private static String	JMX_PROPBASE
				    = Globals.IMQ + ".jmx";
    private static String	ENABLED
				    = JMX_PROPBASE + ".enabled";
    private static String	USE_PLATFORM_MBEANSERVER
				    = JMX_PROPBASE + ".usePlatformMBeanServer";
    private static String	MSG_MBEANS_ENABLED
				    = JMX_PROPBASE + ".mbeans.msg.enabled";
    private static String	MLET_ENABLED
				    = JMX_PROPBASE + ".mlet.enabled";
    private static String	MLET_FILE_URL
				    = JMX_PROPBASE + ".mlet.file.url";
    private static String	RMIREGISTRY_PROPBASE
				    = JMX_PROPBASE + ".rmiregistry";
    private static String	RMIREGISTRY_START 
				    = RMIREGISTRY_PROPBASE
				    + ".start";
    private static String	RMIREGISTRY_USE 
				    = RMIREGISTRY_PROPBASE
				    + ".use";
    private static String	RMIREGISTRY_PORT 
				    = RMIREGISTRY_PROPBASE
				    + ".port";

    private MBeanServer				mbs = null;
    private ConnectorServerManager		csm = null;
    private Logger 				logger = null;
    private boolean				active = false;
    private com.sun.messaging.jmq.jmsserver.config.BrokerConfig	config 
					= Globals.getConfig();
    private BrokerResources			rb = null;


    private String	mbeansPkgName 
	= "com.sun.messaging.jmq.jmsserver.management.mbeans";

    private String[][]	oneOnlyMBeans =
	{
	  {"JVMMonitor", MQObjectName.JVM_MONITOR_MBEAN_NAME},
	  {"BrokerConfig", MQObjectName.BROKER_CONFIG_MBEAN_NAME},
	  {"ServiceManagerConfig", MQObjectName.SERVICE_MANAGER_CONFIG_MBEAN_NAME},
	  {"DestinationManagerConfig", MQObjectName.DESTINATION_MANAGER_CONFIG_MBEAN_NAME},
	  {"ConnectionManagerConfig", MQObjectName.CONNECTION_MANAGER_CONFIG_MBEAN_NAME},
	  {"ConsumerManagerConfig", MQObjectName.CONSUMER_MANAGER_CONFIG_MBEAN_NAME},
	  {"ProducerManagerConfig", MQObjectName.PRODUCER_MANAGER_CONFIG_MBEAN_NAME},
	  {"TransactionManagerConfig", MQObjectName.TRANSACTION_MANAGER_CONFIG_MBEAN_NAME},
	  {"ClusterConfig", MQObjectName.CLUSTER_CONFIG_MBEAN_NAME},
	  {"LogConfig", MQObjectName.LOG_CONFIG_MBEAN_NAME}
	};

    /*
     * HashMap containing references to all MBeans
     */
    private Map			mbeans = 
		Collections.synchronizedMap(new HashMap());

    /*
     * References to MBeans
     * This is just for convenience since they are also stored in the
     * 'mbeans' HashMap.
     */

    private BrokerMonitor		bkrMon;
    private ServiceManagerMonitor	svcMgrMon;
    private ConnectionManagerMonitor	cxnMgrMon;
    private DestinationManagerMonitor	dstMgrMon;
    private ConsumerManagerMonitor	conMgrMon;
    private ProducerManagerMonitor	prdMgrMon;
    private TransactionManagerMonitor	txnMgrMon;
    private ClusterMonitor		clsMon;
    private LogMonitor			logMon;
    private MessageManagerConfig	msgMgrCon;
    private MessageManagerMonitor	msgMgrMon;

    private MLet			mqMLet = null;
    private String			MQMLET_MBEAN_NAME
		= "com.sun.messaging.jms.server:type=MQMLet";

    private String			MESSAGE_MANAGER_CONFIG_MBEAN_NAME
		= "com.sun.messaging.jms.server:type=MessageManager,subtype=Config";
    private String			MESSAGE_MANAGER_MONITOR_MBEAN_NAME
		= "com.sun.messaging.jms.server:type=MessageManager,subtype=Monitor";

    ClusterListener cl = new ClusterStateListener();

    public Agent()  {
        init();
    }

    public static boolean getDEBUG() {
        return DEBUG;
    }

    public void destroy() {
        Globals.getClusterManager().removeEventListener(cl);
    }

    public boolean isActive()  {
	return (active);
    }

    public void start()  {
	if (!isActive())  {
	    return;
	}

	try  {
	    csm.start();
	} catch (Exception e)  {
	    logger.logStack(Logger.WARNING, 
		rb.W_JMX_AGENT_STARTUP_FAILED, e);
	}
    }

    public void stop()  {
	if (!isActive())  {
	    return;
	}

	try  {
	    csm.stop();
	} catch (Exception e)  {
	    logger.log(Logger.WARNING, 
		    rb.W_JMX_AGENT_STOP_EXCEPTION,
		    e);
	}
    }

    public MBeanServer getMBeanServer()  {
	return (mbs);
    }

    public boolean useRmiRegistry()  {
        return (config.getBooleanProperty(RMIREGISTRY_USE));
    }

    public boolean msgMBeansEnabled()  {
        return (config.getBooleanProperty(MSG_MBEANS_ENABLED));
    }

    public boolean mletEnabled()  {
        return (config.getBooleanProperty(MLET_ENABLED));
    }

    public String getMLetFileURL()  {
        return (config.getProperty(MLET_FILE_URL));
    }

    public boolean startRmiRegistry()  {
        return (config.getBooleanProperty(RMIREGISTRY_START));
    }

    public int getRmiRegistryPort()  {
	int port = config.getIntProperty(RMIREGISTRY_PORT, 0);

	if (port == 0)  {
	    port = Registry.REGISTRY_PORT;
	}

	return (port);
    }

    public ConnectorServerManager getConnectorServerManager()  {
	return (csm);
    }

    public void registerDestination(Destination d) {
        ObjectName o;
        DestinationMonitor dm;
        DestinationConfig dc;

	if (!isActive())  {
	    return;
	}

	if (!DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

        try  {
            dm = new DestinationMonitor(d);
            o = MQObjectName.createDestinationMonitor(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());
            agentRegisterMBean(dm, o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Destination Monitor"),
                    e);
        }

        try  {
            dc = new DestinationConfig(d);
            o = MQObjectName.createDestinationConfig(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());
            agentRegisterMBean(dc, o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Destination Config"),
                    e);
        }

    }
    public void unregisterDestination(Destination d) {
        ObjectName o;

	if (!isActive())  {
	    return;
	}

	if (!DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

        try  {
            o = MQObjectName.createDestinationMonitor(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());
            agentUnregisterMBean(o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_UNREGISTER_MBEAN_EXCEPTION, "Destination Monitor"),
                    e);
        }

        try  {
            o = MQObjectName.createDestinationConfig(
                    d.isQueue() ? 
			DestinationType.QUEUE : DestinationType.TOPIC,
                    d.getDestinationName());
            agentUnregisterMBean(o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_UNREGISTER_MBEAN_EXCEPTION, "Destination Config"),
                    e);
        }
    }

    public void registerService(String service) {
        ObjectName o;
        ServiceMonitor sm;
        ServiceConfig sc;

	if (!isActive())  {
	    return;
	}

        try  {
            sm = new ServiceMonitor(service);
            o = MQObjectName.createServiceMonitor(service);
            agentRegisterMBean(sm, o);
        } catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Service Monitor"),
                    e);
        }

        try  {
            sc = new ServiceConfig(service);
            o = MQObjectName.createServiceConfig(service);
            agentRegisterMBean(sc, o);
        } catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Service Config"),
                    e);
        }

    }

    public void unregisterService(String service) {
	if (!isActive())  {
	    return;
	}

    }

    public void registerConnection(long id)  {
        ObjectName o;
        ConnectionMonitor cm;
        ConnectionConfig cc;

	if (!isActive())  {
	    return;
	}

        try  {
            cm = new ConnectionMonitor(id);
            o = MQObjectName.createConnectionMonitor(Long.toString(id));
            agentRegisterMBean(cm, o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Connection Monitor"),
                    e);
        }

        try  {
            cc = new ConnectionConfig(id);
            o = MQObjectName.createConnectionConfig(Long.toString(id));
            agentRegisterMBean(cc, o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, "Connection Config"),
                    e);
        }
    }
    public void unregisterConnection(long id)  {
        ObjectName o;

	if (!isActive())  {
	    return;
	}

        try  {
            o = MQObjectName.createConnectionMonitor(Long.toString(id));
            agentUnregisterMBean(o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_UNREGISTER_MBEAN_EXCEPTION, "Connection Monitor"),
                    e);
        }

        try  {
            o = MQObjectName.createConnectionConfig(Long.toString(id));
            agentUnregisterMBean(o);
        } catch (Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_UNREGISTER_MBEAN_EXCEPTION, "Connection Config"),
                    e);
        }
    }

    private void init()  {
        logger = Globals.getLogger();
	rb = Globals.getBrokerResources();

	if (!jmxSupportEnabled())  {
            logger.log(Logger.WARNING, rb.W_JMX_DISABLED);
	    return;
	}

	try {
	    Class.forName("javax.management.MBeanServer");
	} catch (Exception e)  {
            logger.log(Logger.WARNING, rb.W_JMX_CLASSES_NOT_FOUND);
	    return;
	}

	try  {
	    /*
	     * Use JDK 1.5 platform MBeanServer or create a new one.
	     */
	    if (platformMBeanServerAvailable())  {
	        if (usePlatformMBeanServer())  {
		    /*
		     * Platform MBeanServer is available (ie JDK 1.5 is used)
		     */
	            mbs = getPlatformMBeanServer();
                    logger.log(Logger.INFO, rb.I_JMX_USING_PLATFORM_MBEANSERVER);
		} else  {
		    /*
		     * Platform MBeanServer is available (ie JDK 1.5 is used)
		     * but the conifguration indicates that it should not
		     * be used.
		     */
	            mbs = createMBeanServer();
                    logger.log(Logger.INFO, rb.I_JMX_CREATE_MBEANSERVER);
		}
	    } else  {
		/*
		 * Platform MBeanServer is not available (i.e. JDK 1.4 or
		 * earlier is used)
		 */
	        mbs = createMBeanServer();
                logger.log(Logger.INFO, rb.I_JMX_CREATE_MBEANSERVER);
	    }

	    startRMIRegistry();

	    csm = new ConnectorServerManager(this);

	    csm.initConfiguredConnectorServers();


	} catch (Exception e)  {
            logger.log(Logger.WARNING, rb.W_JMX_AGENT_CREATE_EXCEPTION, e);
	    return;
	}

        Globals.getClusterManager().addEventListener(cl);

	active = true;
    }

    private MBeanServer getPlatformMBeanServer() throws BrokerException {
	MBeanServer mbeanServer = null;

	try  {
	    Class c = Class.forName("java.lang.management.ManagementFactory");
	    Method m = c.getMethod("getPlatformMBeanServer", null);
	    mbeanServer = (MBeanServer)m.invoke("getPlatformMBeanServer", null);
	} catch (Exception e)  {
	    throw new BrokerException(rb.getString(rb.W_JMX_GET_PLATFORM_MBEANSERVER_EXCEPTION, 
						e.toString()));
	}

        return(mbeanServer);
    }

    private void startRMIRegistry() throws BrokerException {
	String regPortStr = null;
	Registry registry = null;
	boolean registryExists = false;
	int port;

	if (!startRmiRegistry())  {
	    return;
	}

        port = getRmiRegistryPort();

	String jmxHostname = Globals.getJMXHostname();

	try  {
	    if (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL))  {
	        registry = LocateRegistry.getRegistry(jmxHostname, port);
	    } else  {
	        registry = LocateRegistry.getRegistry(port);
	    }

	    /*
	     * Call list() to force a remote call - this
	     * confirms if the registry is up and running
	     */
	    registry.list();
	    registryExists = true;
	} catch(RemoteException re)  {
	    /*
	     * An exception will be caught if there is no registry running
	     * at the specified port. Not a problem since we are about
	     * to create a registry.
	     */
	}

	if (registryExists)  {
	    throw new BrokerException(rb.getString(rb.W_JMX_RMI_REGISTRY_EXISTS, 
						Integer.toString(port)));
	}

	try  {
	    if (jmxHostname != null && !jmxHostname.equals(Globals.HOSTNAME_ALL))  {
	        MQRMIServerSocketFactory ssf = 
		    new MQRMIServerSocketFactory(jmxHostname, 0, false);

	        registry = LocateRegistry.createRegistry(port, null, ssf);
	    } else  {
	        registry = LocateRegistry.createRegistry(port);
	    }

	    /*
	     * Call list() to force a remote call - this
	     * confirms if the registry is up and running
	     */
	    registry.list();
            logger.log(Logger.INFO, 
		rb.getString(rb.I_JMX_RMI_REGISTRY_STARTED, Integer.toString(port)));
	} catch(RemoteException re)  {
	    throw new BrokerException(
		rb.getString(rb.W_JMX_RMI_REGISTRY_STARTED_EXCEPTION, 
					Integer.toString(port), re.toString()));
	}
    }

    public void loadMBeans()  {
	try  {
            loadAllMBeans();
	} catch(Exception e)  {
	    logger.log(Logger.WARNING,
		rb.getString(rb.W_JMX_LOADING_MBEANS_EXCEPTION, e.toString()));

	}
    }

    private void loadAllMBeans() throws MalformedObjectNameException, 
				ReflectionException, 
    				InstanceAlreadyExistsException, 
				MBeanRegistrationException, 
				MBeanException,
				NotCompliantMBeanException  {

	ObjectName objName;

	/*
	 * Create 'one-only' MBeans in MQ as defined in 'oneOnlyMBeans' table.
	 */
        for (int i=0; i < oneOnlyMBeans.length; ++i)  {
            ObjectName mbeanName = new ObjectName(oneOnlyMBeans[i][1]);
            String mbeanClassName = mbeansPkgName + "." + oneOnlyMBeans[i][0];
	    Object mbean = null;

	    try  {
	        mbean = Class.forName(mbeanClassName).newInstance();
	        agentRegisterMBean(mbean, mbeanName);
	    } catch (Exception e)  {
		String name;

		if ((mbean != null) && (mbean instanceof MQMBeanReadOnly))  {
		    MQMBeanReadOnly mqmb = (MQMBeanReadOnly)mbean;
		    name = mqmb.getMBeanName();
		} else  {
		    name = mbeanName.toString();
		}

                logger.log(Logger.WARNING, 
		        rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, name), e);
	    }
        }

	bkrMon = new BrokerMonitor();
        objName = new ObjectName(MQObjectName.BROKER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(bkrMon, objName);

	svcMgrMon = new ServiceManagerMonitor();
        objName = new ObjectName(MQObjectName.SERVICE_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(svcMgrMon, objName);

	dstMgrMon = new DestinationManagerMonitor();
        objName = new ObjectName(MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(dstMgrMon, objName);

	cxnMgrMon = new ConnectionManagerMonitor();
        objName = new ObjectName(MQObjectName.CONNECTION_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(cxnMgrMon, objName);

	conMgrMon = new ConsumerManagerMonitor();
        objName = new ObjectName(MQObjectName.CONSUMER_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(conMgrMon, objName);

	prdMgrMon = new ProducerManagerMonitor();
        objName = new ObjectName(MQObjectName.PRODUCER_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(prdMgrMon, objName);

	txnMgrMon = new TransactionManagerMonitor();
        objName = new ObjectName(MQObjectName.TRANSACTION_MANAGER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(txnMgrMon, objName);

	clsMon = new ClusterMonitor();
        objName = new ObjectName(MQObjectName.CLUSTER_MONITOR_MBEAN_NAME);
	agentRegisterMBean(clsMon, objName);

	logMon = new LogMonitor();
        objName = new ObjectName(MQObjectName.LOG_MONITOR_MBEAN_NAME);
	agentRegisterMBean(logMon, objName);

	if (msgMBeansEnabled())  {
	    msgMgrMon = new MessageManagerMonitor();
            objName = new ObjectName(MESSAGE_MANAGER_MONITOR_MBEAN_NAME);
	    agentRegisterMBean(msgMgrMon, objName);

	    msgMgrCon = new MessageManagerConfig();
            objName = new ObjectName(MESSAGE_MANAGER_CONFIG_MBEAN_NAME);
	    agentRegisterMBean(msgMgrCon, objName);
	}

	/*
	 * Create DestinationMonitor MBeans
	 */
	List dests = DestinationUtil.getVisibleDestinations();
	if (dests.size() != 0)  {
            for (int i =0; i < dests.size(); i ++) {
                Destination d = (Destination)dests.get(i);

		registerDestination(d);
            }
	}

	/*
	 * Create ServiceMonitor MBeans
	 */
	List svcs = ServiceUtil.getVisibleServiceNames();
	Iterator iter = svcs.iterator();
	while (iter.hasNext())  {
	    String service = (String)iter.next();

	    registerService(service);
	}

	/*
	 * Create MLet MBean if broker is imq.jmx.mlet.enabled is set
	 */
	if (mletEnabled())  {
	    try  {
                ObjectName mletName = new ObjectName(MQMLET_MBEAN_NAME);

	        mqMLet = new MLet();
	        agentRegisterMBean(mqMLet, mletName);
                logger.log(Logger.INFO, 
		        "MLET: Registering MLet MBean");
	    } catch (Exception e)  {
	        String name = "MQMLet";

                logger.log(Logger.WARNING, 
		        rb.getString(rb.W_JMX_REGISTER_MBEAN_EXCEPTION, name), e);
            }

	    if (mqMLet != null)  {
	        String url = getMLetFileURL();
	        if ((url != null) && (!url.equals("")))  {
	            try  {
                        logger.log(Logger.INFO, 
		              "MLET: Loading MBeans from MLet file: " + url);

	                Set loadedMBeans = mqMLet.getMBeansFromURL(url);

			if (loadedMBeans != null)  {
			    Iterator mb = loadedMBeans.iterator();
			    while (mb.hasNext())  {
				Object obj = mb.next();

				if (obj instanceof ObjectInstance)  {
				    ObjectInstance objInst = (ObjectInstance)obj;
                                    logger.log(Logger.INFO, 
		                        "MLET: Loaded MBean [objectname=" 
					    + objInst.getObjectName().toString()
					    + ", class="
					    + objInst.getClassName()
					    + "]");
				} else if (obj instanceof Throwable)  {
				    Throwable thr = (Throwable)obj;
                                    logger.log(Logger.WARNING, 
		                        "MLET: Failed to load MBean: " + thr);
				} else  {
                                    logger.log(Logger.WARNING, 
		                        "MLET: Unknown object type returned by MLet MBean creation: " 
						+ obj);
				}
			    }
			}
	            } catch (Exception e)  {
	                String name = "MQMLet";

                        logger.log(Logger.WARNING, 
		                "Exception caught while loading MBeans via MQMLet", e);
                    }
	        }
	    }
	}

    }

    public void unloadMBeans() {
	if (!isActive())  {
	    return;
	}

	/*
	 * Unregister ServiceMonitor MBeans
	 */
	List svcs = ServiceUtil.getVisibleServiceNames();
	Iterator iter = svcs.iterator();
	while (iter.hasNext())  {
	    String service = (String)iter.next();

	    unregisterService(service);
	}

	try  {
	    agentUnregisterAllMBeans();
	} catch (Exception e)  {
	    logger.log(Logger.WARNING,
		rb.getString(rb.W_JMX_UNLOADING_MBEANS_EXCEPTION, e.toString()));
	}

    }

    /*
     * BEGIN: Methods related to MBean notifications
     */

    public void notifyQuiesceStart()  {
	if (bkrMon != null)  {
	    bkrMon.notifyQuiesceStart();
	}
    }

    public void notifyQuiesceComplete()  {
	if (bkrMon != null)  {
	    bkrMon.notifyQuiesceComplete();
	}
    }

    public void notifyShutdownStart()  {
	if (bkrMon != null)  {
	    bkrMon.notifyShutdownStart();
	}
    }

    public void notifyTakeoverStart(String brokerID)  {
	if (bkrMon != null)  {
	    bkrMon.notifyTakeoverStart(brokerID);
	}

	if (clsMon != null)  {
	    clsMon.notifyTakeoverStart(brokerID);
	}
    }

    public void notifyTakeoverComplete(String brokerID)  {
	if (bkrMon != null)  {
	    bkrMon.notifyTakeoverComplete(brokerID);
	}

	if (clsMon != null)  {
	    clsMon.notifyTakeoverComplete(brokerID);
	}
    }

    public void notifyTakeoverFail(String brokerID)  {
	if (bkrMon != null)  {
	    bkrMon.notifyTakeoverFail(brokerID);
	}

	if (clsMon != null)  {
	    clsMon.notifyTakeoverFail(brokerID);
	}
    }

    public void notifyClusterBrokerDown(String brokerID)  {
	if (clsMon != null)  {
	    clsMon.notifyClusterBrokerDown(brokerID);
	}
    }

    public void notifyClusterBrokerJoin(String brokerID)  {
	if (clsMon != null)  {
	    clsMon.notifyClusterBrokerJoin(brokerID);
	}

	if (bkrMon != null)  {
	    bkrMon.notifyClusterBrokerJoin(brokerID);
	}
    }

    public void notifyServicePause(String serviceName)  {
	if (serviceName == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ServiceNotification.SERVICE_PAUSE));
            logger.log(Logger.WARNING, rb.W_JMX_SERVICE_NAME_NULL);

	    return;
	}

	if (svcMgrMon != null)  {
	    svcMgrMon.notifyServicePause(serviceName);
	}

	ObjectName o = null;
	try  {
            o = MQObjectName.createServiceMonitor(serviceName);
	    ServiceMonitor sm = (ServiceMonitor)getMBean(o);
	    if (sm != null)  {
	        sm.notifyServicePause();
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ServiceNotification.SERVICE_PAUSE,
					o+"["+serviceName+"]"), e);
	}

    }

    public void notifyServiceResume(String serviceName)  {
	if (serviceName == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ServiceNotification.SERVICE_RESUME));
            logger.log(Logger.WARNING, rb.W_JMX_SERVICE_NAME_NULL);

	    return;
	}

	if (svcMgrMon != null)  {
	    svcMgrMon.notifyServiceResume(serviceName);
	}

	ObjectName o = null;
	try  {
            o = MQObjectName.createServiceMonitor(serviceName);
	    ServiceMonitor sm = (ServiceMonitor)getMBean(o);
	    if (sm != null)  {
	        sm.notifyServiceResume();
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ServiceNotification.SERVICE_RESUME,
					o+"["+serviceName+"]"), e);
	}

    }

    public void notifyConnectionOpen(long id)  {
	if (cxnMgrMon != null)  {
	    cxnMgrMon.notifyConnectionOpen(id);
	}

	String serviceName = ConnectionUtil.getServiceOfConnection(id);

	if (serviceName == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_PROBLEM,
					ConnectionNotification.CONNECTION_OPEN,
					"Service Monitor"));

            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_CANNOT_GET_SVC_NAME_FROM_CXN_ID, Long.toString(id)));

	    return;
	}

	ObjectName o = null;
	try  {
            o = MQObjectName.createServiceMonitor(serviceName);
	    ServiceMonitor sm = (ServiceMonitor)getMBean(o);
	    if (sm != null)  {
	        sm.notifyConnectionOpen(id);
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConnectionNotification.CONNECTION_OPEN,
					o+"["+serviceName+"]"), e);
	}
    }

    public void notifyConnectionClose(long id)  {
	if (cxnMgrMon != null)  {
	    cxnMgrMon.notifyConnectionClose(id);
	}

	String serviceName = ConnectionUtil.getServiceOfConnection(id);

	if (serviceName == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_PROBLEM,
					ConnectionNotification.CONNECTION_CLOSE,
					"Service Monitor"));

            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_CANNOT_GET_SVC_NAME_FROM_CXN_ID, Long.toString(id)));

	    return;
	}

	ObjectName o = null;
	try  {
            o = MQObjectName.createServiceMonitor(serviceName);
	    ServiceMonitor sm = (ServiceMonitor)getMBean(o);
	    if (sm != null)  {
	        sm.notifyConnectionClose(id);
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConnectionNotification.CONNECTION_CLOSE,
					o+"["+serviceName+"]"), e);
	}
    }

    public void notifyConnectionReject(String serviceName, String userName, 
			String remoteHostString)  {
	if (serviceName == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ConnectionNotification.CONNECTION_REJECT));
            logger.log(Logger.WARNING, rb.W_JMX_SERVICE_NAME_NULL);

	    return;
	}

	if (cxnMgrMon != null)  {
	    cxnMgrMon.notifyConnectionReject(serviceName, userName, remoteHostString);
	}

	ObjectName o = null;
	try  {
            o = MQObjectName.createServiceMonitor(serviceName);
	    ServiceMonitor sm = (ServiceMonitor)getMBean(o);
	    if (sm != null)  {
	        sm.notifyConnectionReject(serviceName, userName, remoteHostString);
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConnectionNotification.CONNECTION_REJECT,
					o+"["+serviceName+"]"), e);
	}
    }

    public void notifyDestinationAttrUpdated(Destination d, int attr, Object oldVal, Object newVal)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	ObjectName o = null;
	try  {
	    o = DestinationUtil.getConfigObjectName(d);
	    DestinationConfig dc = (DestinationConfig)getMBean(o);
	    if (dc != null)  {
		Object tmp, tmp2;

		tmp = DestinationUtil.convertAttrValueInternaltoExternal(attr, oldVal);
		tmp2 = DestinationUtil.convertAttrValueInternaltoExternal(attr, newVal);
	        dc.notifyDestinationAttrUpdated(attr, tmp, tmp2);
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					AttributeChangeNotification.ATTRIBUTE_CHANGE,
					o+"["+d+"]"), e);
	}

    }

    public void notifyServiceAttrUpdated(String svcName, String attr, Object oldVal, Object newVal)  {
    }

    public void notifyDestinationCompact(Destination d)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationCompact(d);
	}

	ObjectName o = null;
	try  {
	    o = DestinationUtil.getMonitorObjectName(d);
	    DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	    if (dm != null)  {
	        dm.notifyDestinationCompact();
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					DestinationNotification.DESTINATION_COMPACT,
					o+"["+d+"]"), e);
	}
    }

    public void notifyDestinationCreate(Destination d)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationCreate(d);
	}
    }

    public void notifyDestinationDestroy(Destination d)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationDestroy(d);
	}
    }

    public void notifyDestinationPause(Destination d, int internalPauseType)  {
	String externalPauseType;

	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	externalPauseType 
		= DestinationUtil.toExternalPauseType(internalPauseType);

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationPause(d, externalPauseType);
	}

	ObjectName o = null;
	try  {
	    o = DestinationUtil.getMonitorObjectName(d);
	    DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	    if (dm != null)  {
	        dm.notifyDestinationPause(externalPauseType);
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					DestinationNotification.DESTINATION_PAUSE,
					o+"["+d+"]"), e);
	}

    }

    public void notifyDestinationPurge(Destination d)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationPurge(d);
	}

	ObjectName o = null;
	try  {
	    o = DestinationUtil.getMonitorObjectName(d);
	    DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	    if (dm != null)  {
	        dm.notifyDestinationPurge();
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					DestinationNotification.DESTINATION_PURGE,
					o+"["+d+"]"), e);
	}
    }

    public void notifyDestinationResume(Destination d)  {
	if ((d == null) || !DestinationUtil.isVisibleDestination(d))  {
	    return;
	}

	if (dstMgrMon != null)  {
	    dstMgrMon.notifyDestinationResume(d);
	}

	ObjectName o = null;
	try  {
	    o = DestinationUtil.getMonitorObjectName(d);
	    DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	    if (dm != null)  {
	        dm.notifyDestinationResume();
	    }
	} catch(Exception e)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					DestinationNotification.DESTINATION_RESUME,
					o+"["+d+"]"), e);
	}

    }

    /*
    public void notifyConsumerCreate(ConsumerUID cid)  {
        ObjectName o = null;
	long id;

	if (cid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ConsumerNotification.CONSUMER_CREATE));
            logger.log(Logger.WARNING, rb.W_JMX_CONSUMER_ID_NULL);
	    return;
	}

	id = cid.longValue();

	if (conMgrMon != null)  {
	    conMgrMon.notifyConsumerCreate(id);
	}

	ConnectionUID cxnId = ConsumerUtil.getConnectionUID(cid);
	if (cxnId != null)  {
	    try  {
                o = MQObjectName.createConnectionMonitor(Long.toString(cxnId.longValue()));
	        ConnectionMonitor cm = (ConnectionMonitor)getMBean(o);
	        if (cm != null)  {
	            cm.notifyConsumerCreate(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConsumerNotification.CONSUMER_CREATE,
					o+"["+cxnId+"]"), e);
	    }
	}

	Destination d = ConsumerUtil.getDestination(cid);
	if (d != null)  {
	    try  {
	        o = DestinationUtil.getMonitorObjectName(d);
	        DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	        if (dm != null)  {
	            dm.notifyConsumerCreate(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConsumerNotification.CONSUMER_CREATE,
					o+"["+d+"]"), e);
	    }
	}
    }

    public void notifyConsumerDestroy(ConsumerUID cid)  {
        ObjectName o = null;
	long id;

	if (cid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ConsumerNotification.CONSUMER_DESTROY));
            logger.log(Logger.WARNING, rb.W_JMX_CONSUMER_ID_NULL);
	    return;
	}

	id = cid.longValue();

	if (conMgrMon != null)  {
	    conMgrMon.notifyConsumerDestroy(id);
	}

	ConnectionUID cxnId = ConsumerUtil.getConnectionUID(cid);
	if (cxnId != null)  {
	    try  {
                o = MQObjectName.createConnectionMonitor(Long.toString(cxnId.longValue()));
	        ConnectionMonitor cm = (ConnectionMonitor)getMBean(o);
	        if (cm != null)  {
	            cm.notifyConsumerDestroy(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConsumerNotification.CONSUMER_DESTROY,
					o+"["+cxnId+"]"), e);
	    }
	}

	Destination d = ConsumerUtil.getDestination(cid);
	if (d != null)  {
	    try  {
	        o = DestinationUtil.getMonitorObjectName(d);
	        DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	        if (dm != null)  {
	            dm.notifyConsumerDestroy(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ConsumerNotification.CONSUMER_DESTROY,
					o+"["+d+"]"), e);
	    }
	}
    }

    public void notifyProducerCreate(ProducerUID pid)  {
        ObjectName o = null;
	long id;

	if (pid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ProducerNotification.PRODUCER_CREATE));
            logger.log(Logger.WARNING, rb.W_JMX_PRODUCER_ID_NULL);
	    return;
	}

	id = pid.longValue();

	if (prdMgrMon != null)  {
	    prdMgrMon.notifyProducerCreate(id);
	}

	ConnectionUID cxnId = ProducerUtil.getConnectionUID(pid);
	if (cxnId != null)  {
	    try  {
                o = MQObjectName.createConnectionMonitor(Long.toString(cxnId.longValue()));
	        ConnectionMonitor cm = (ConnectionMonitor)getMBean(o);
	        if (cm != null)  {
	            cm.notifyProducerCreate(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ProducerNotification.PRODUCER_CREATE,
					o+"["+cxnId+"]"), e);
	    }
	}

	Destination d = ProducerUtil.getDestination(pid);
	if (d != null)  {
	    try  {
	        o = DestinationUtil.getMonitorObjectName(d);
	        DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	        if (dm != null)  {
	            dm.notifyProducerCreate(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ProducerNotification.PRODUCER_CREATE,
					o+"["+cxnId+"]"), e);
	    }
	}
    }

    public void notifyProducerDestroy(ProducerUID pid)  {
        ObjectName o = null;
	long id;

	if (pid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					ProducerNotification.PRODUCER_DESTROY));
            logger.log(Logger.WARNING, rb.W_JMX_PRODUCER_ID_NULL);
	    return;
	}

	id = pid.longValue();

	if (prdMgrMon != null)  {
	    prdMgrMon.notifyProducerDestroy(id);
	}

	ConnectionUID cxnId = ProducerUtil.getConnectionUID(pid);
	if (cxnId != null)  {
	    try  {
                o = MQObjectName.createConnectionMonitor(Long.toString(cxnId.longValue()));
	        ConnectionMonitor cm = (ConnectionMonitor)getMBean(o);
	        if (cm != null)  {
	            cm.notifyProducerDestroy(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ProducerNotification.PRODUCER_DESTROY,
					o+"["+cxnId+"]"), e);
	    }
	}

	Destination d = ProducerUtil.getDestination(pid);
	if (d != null)  {
	    try  {
	        o = DestinationUtil.getMonitorObjectName(d);
	        DestinationMonitor dm = (DestinationMonitor)getMBean(o);
	        if (dm != null)  {
	            dm.notifyProducerDestroy(id);
	        }
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					ProducerNotification.PRODUCER_DESTROY,
					o+"["+d+"]"), e);
	    }
	}
    }
    */

    public void notifyTransactionCommit(TransactionUID tid)  {
	long id;

	if (tid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					TransactionNotification.TRANSACTION_COMMIT));
            logger.log(Logger.WARNING, rb.W_JMX_TXN_ID_NULL);
	    return;
	}

	id = tid.longValue();

	if (txnMgrMon != null)  {
	    txnMgrMon.notifyTransactionCommit(id);
	}
    }

    public void notifyTransactionPrepare(TransactionUID tid)  {
	long id;

	if (tid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					TransactionNotification.TRANSACTION_PREPARE));
            logger.log(Logger.WARNING, rb.W_JMX_TXN_ID_NULL);
	    return;
	}

	id = tid.longValue();

	if (txnMgrMon != null)  {
	    txnMgrMon.notifyTransactionPrepare(id);
	}
    }

    public void notifyTransactionRollback(TransactionUID tid)  {
	long id;

	if (tid == null)  {
            logger.log(Logger.WARNING, 
		rb.getString(rb.W_JMX_SEND_NOTIFICATION_PROBLEM,
					TransactionNotification.TRANSACTION_ROLLBACK));
            logger.log(Logger.WARNING, rb.W_JMX_TXN_ID_NULL);
	    return;
	}

	id = tid.longValue();

	if (txnMgrMon != null)  {
	    txnMgrMon.notifyTransactionRollback(id);
	}
    }

    public void notifyLogMessage(int level, String message)  {
	if (logMon != null)  {
	    String levelStr;

	    levelStr = Logger.jullevelIntToStr(level);

	    try {
	        logMon.notifyLogMessage(levelStr, message);
	    } catch (Exception e)  {
                logger.log(Logger.WARNING, 
		    rb.getString(rb.W_JMX_SEND_NOTIFICATION_FROM_MBEAN_EXCEPTION,
					LogNotification.LOG_LEVEL_PREFIX + levelStr,
					"Log Monitor"), e);
	    }
	}
    }

    public void notifyResourceStateChange(String oldResourceState, String newResourceState, 
						MemoryUsage heapMemoryUsage)  {
	if (bkrMon != null)  {
	    bkrMon.notifyResourceStateChange(oldResourceState, newResourceState, heapMemoryUsage);
	}
    }

    public void portMapperPortUpdated(Integer oldPort, Integer newPort)  {
    }


    /*
     * END: Methods related to MBean notifications
     */

    /*
     * Reset metrics that are not already handled by
     * com.sun.messaging.jmq.jmsserver.data.handlers.admin.ResetMetricsHandler
     */
    public void resetMetrics()  {
	if (cxnMgrMon != null)  {
	    cxnMgrMon.resetMetrics();
	}

	if (txnMgrMon != null)  {
	    txnMgrMon.resetMetrics();
	}

	/*
	 * Reset metrics in each ServiceMonitor MBean
	 */
	List svcs = ServiceUtil.getVisibleServiceNames();
	Iterator iter = svcs.iterator();
	while (iter.hasNext())  {
	    String service = (String)iter.next();

	    ServiceMonitor sm = getServiceMonitorMBean(service);

	    if (sm != null)  {
		sm.resetMetrics();
	    }
	}
    }

    public BrokerConfig getBrokerConfigMBean()  {
	BrokerConfig bc = null;
	ObjectName o;
	Exception ex = null;

        try  {
            o = new ObjectName(MQObjectName.BROKER_CONFIG_MBEAN_NAME);
	    bc = (BrokerConfig)getMBean(o);
        } catch(Exception e)  {
	    ex = e;
        }

	if (bc == null)  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG,
		    "Exception caught when obtaining handle to BrokerConfig MBean: "
			+ ((ex == null) ? "" : ex.toString()));
	    }
	}

	return (bc);
    }

    public BrokerMonitor getBrokerMonitorMBean()  {
	return (bkrMon);
    }

    public ServiceConfig getServiceConfigMBean(String svc)  {
	ServiceConfig sc = null;
	ObjectName o;
	Exception ex = null;

        try  {
            o = MQObjectName.createServiceConfig(svc);
	    sc = (ServiceConfig)getMBean(o);
        } catch(Exception e)  {
	    ex = e;
        }

	if (sc == null)  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG,
		    "Exception caught when obtaining handle to ServiceConfig MBean: "
			+ ((ex == null) ? "" : ex.toString()));
	    }
	}

	return (sc);
    }

    public ServiceMonitor getServiceMonitorMBean(String svc)  {
	ServiceMonitor sm = null;
	ObjectName o;
	Exception ex = null;

        try  {
            o = MQObjectName.createServiceMonitor(svc);
	    sm = (ServiceMonitor)getMBean(o);
        } catch(Exception e)  {
	    ex = e;
        }

	if (sm == null)  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG,
		    "Exception caught when obtaining handle to ServiceMonitor MBean: "
			+ ((ex == null) ? "" : ex.toString()));
	    }
	}

	return (sm);
    }

    public DestinationConfig getDestinationConfigMBean(String name,
					String type)  {
	DestinationConfig dc = null;
	ObjectName o;
	Exception ex = null;

        try  {
            o = MQObjectName.createDestinationConfig(type, name);
	    dc = (DestinationConfig)getMBean(o);
        } catch(Exception e)  {
	    ex = e;
        }

	if (dc == null)  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG,
		    "Exception caught when obtaining handle to DestinationConfig MBean: "
			+ ((ex == null) ? "" : ex.toString()));
	    }
	}

	return (dc);
    }

    public DestinationMonitor getDestinationMonitorMBean(String name,
					String type)  {
	DestinationMonitor dm = null;
	ObjectName o;
	Exception ex = null;

        try  {
            o = MQObjectName.createDestinationMonitor(type, name);
	    dm = (DestinationMonitor)getMBean(o);
        } catch(Exception e)  {
	    ex = e;
        }

	if (dm == null)  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG,
		    "Exception caught when obtaining handle to DestinationMonitor MBean: "
			+ ((ex == null) ? "" : ex.toString()));
	    }
	}

	return (dm);
    }


    public String getDefaultJMXUrlPathBase() throws BrokerException {
	MQAddress addr = Globals.getMQAddress();
	String rmiRegHostName, brokerHostName, ret = null;
	int brokerPort, rmiRegistryPort;

	if (addr == null)  {
	    return (null);
	}

	/*
	 * These other methods work too.
	 * Would be good if we could obtain a fully qualified hostname
	 * i.e. with domain.
	brokerHostName = Globals.getBrokerHostName();
	brokerHostName = Globals.getBrokerInetAddress().getCanonicalHostName();
	*/
	brokerHostName = addr.getHostName();
	rmiRegHostName = Globals.getJMXHostname();

	brokerPort = addr.getPort();

	rmiRegistryPort = getRmiRegistryPort();

	/*
	 * rmiRegHostName can be null if imq.jmx.hostname or imq.hostname is not set.
	 */
	if (rmiRegHostName == null)  {
	    rmiRegHostName = brokerHostName;
	} else {
        try {
             rmiRegHostName = MQAddress.getMQAddress(rmiRegHostName, 
                                        rmiRegistryPort).getHostName();
        } catch (MalformedURLException e) {
             throw new BrokerException(e.toString(), e);
        }
    }

	/*
	 * The default urlpath base is:
	 *  /jndi/rmi://<brokerhost>:<rmiport>/<brokerhost>/<brokerport>/
	 * e.g
	 *  /jndi/rmi://myhost:1099/myhost/7676/
	 */
	ret = "/jndi/rmi://"
		+ rmiRegHostName
		+ ":"
		+ Integer.toString(rmiRegistryPort)
		+ "/"
		+ brokerHostName
		+ "/"
		+ Integer.toString(brokerPort)
		+ "/";

	return (ret);
    }

    private MBeanServer createMBeanServer() {
        return(MBeanServerFactory.createMBeanServer());
    }

    private boolean platformMBeanServerAvailable()  {
	try {
	    Class.forName("java.lang.management.ManagementFactory");
	    return (true);
	} catch (Exception e)  {
	    return (false);
	}
    }

    private boolean jmxSupportEnabled() {
        return (config.getBooleanProperty(ENABLED, true));
    }

    private boolean usePlatformMBeanServer() {
        return (config.getBooleanProperty(USE_PLATFORM_MBEANSERVER));
    }

    /*
     * Register an MBean in broker's MBeanServer.
     * Has a bit more than just mbs.registerMBean() because
     * we need to additional bookkeeping.
     */
    private void agentRegisterMBean(Object mbean, ObjectName objName) 
			throws InstanceAlreadyExistsException,
				MBeanRegistrationException,
				NotCompliantMBeanException  {
	if (mbs == null)  {
	    return;
	}

        if (!mbs.isRegistered(objName))  {
            mbs.registerMBean(mbean, objName);

	    if (DEBUG)  {
	        logger.log(Logger.DEBUG,
		    "Registered MBean: " + objName.toString());
	    }

            addMBean(mbean, objName);
        }
    }

    /*
     * Unregister an MBean in broker's MBeanServer.
     * Has a bit more than just mbs.unregisterMBean() because
     * we need to additional bookkeeping.
     */
    private void agentUnregisterMBean(ObjectName objName) 
			throws InstanceNotFoundException,
				MBeanRegistrationException  {
	if (mbs == null)  {
	    return;
	}

	if (mbs.isRegistered(objName))  {
            mbs.unregisterMBean(objName);

	    if (DEBUG)  {
	        logger.log(Logger.DEBUG,
		"Unregistered MBean: " + objName.toString());
	    }

            removeMBean(objName);
        }
    }

    /*
     * Unregister ALL MBeans in broker's MBeanServer.
     * Has a bit more than just mbs.unregisterMBean() because
     * we need to additional bookkeeping.
     */
    private void agentUnregisterAllMBeans() 
			throws InstanceNotFoundException,
				MBeanRegistrationException  {
	if (mbs == null)  {
	    return;
	}

	synchronized (mbeans)  {
	    Set objNames = mbeans.keySet();
	    ObjectName[] objNamesArray = 
		(ObjectName[])objNames.toArray(new ObjectName [ objNames.size() ]);

	    for (int i = 0; i < objNamesArray.length; ++i)  {
		agentUnregisterMBean(objNamesArray[i]);
	    }
	}
    }

    private void addMBean(Object mbean, ObjectName objName)  {
	synchronized (mbeans)  {
	    mbeans.put(objName, mbean);
	}
    }

    private void removeMBean(ObjectName objName)  {
	synchronized (mbeans)  {
	    mbeans.remove(objName);
	}
    }

    private Object getMBean(ObjectName objName)  {
	Object o = null;

	synchronized (mbeans)  {
	    o = mbeans.get(objName);
	}

	return (o);
    }

    /**
     * listener who handles sending cluster info back to the client
     */
    class ClusterStateListener implements ClusterListener  {
       /**
        * Called to notify ClusterListeners when the cluster service
        * configuration. Configuration changes include:
        * <UL><LI>cluster service port</LI>
        *     <LI>cluster service hostname</LI>
        *     <LI>cluster service transport</LI>
        * </UL><P>
        *
        * @param name the name of the changed property
        * @param value the new value of the changed property
        */
        public void clusterPropertyChanged(String name, String value)  {
            // we dont care
        }

    
    
       /**
        * Called when a new broker has been added.
        * @param brokerSession uid associated with the added broker
        * @param broker the new broker added.
        */
        public void brokerAdded(ClusteredBroker broker, UID brokerSession)  {
            // we dont care
        }

    
       /**
        * Called when a broker has been removed.
        * @param broker the broker removed.
        * @param brokerSession uid associated with the removed broker
        */
        public void brokerRemoved(ClusteredBroker broker, UID brokerSession) {
            // we dont care
        }

    
       /**
        * Called when the broker who is the master broker changes
        * (because of a reload properties).
        * @param oldMaster the previous master broker.
        * @param newMaster the new master broker.
        */
        public void masterBrokerChanged(ClusteredBroker oldMaster,
                        ClusteredBroker newMaster) {
            // we dont care
        }

    
       /**
        * Called when the status of a broker has changed. The
        * status may not be accurate if a previous listener updated
        * the status for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldStatus the previous status.
        * @param brokerSession uid associated with the change
        * @param newStatus the new status.
        * @param userData data associated with the state change
        */
        public void brokerStatusChanged(String brokerid,
                      int oldStatus, int newStatus, UID brokerSession,
                      Object userData) {
	    /*
	    System.err.println("### BrokerStatusChanged");
	    System.err.println("\tbrokerid: " + brokerid);
	    System.err.println("\toldStatus: " + BrokerStatus.toString(oldStatus));
	    System.err.println("\tnewStatus: " + BrokerStatus.toString(newStatus));
	    */

	    /*
	     * Send appropriate notify event.
	     * Make sure that there was a change in the relevant status field - this 
	     * method callback may be called for a variety of reasons.
	     */

	    if (BrokerStatus.getBrokerIsUp(newStatus) && BrokerStatus.getBrokerIsDown(oldStatus))  {
                notifyClusterBrokerJoin(brokerid);
	    }

	    if (BrokerStatus.getBrokerIsDown(newStatus) && BrokerStatus.getBrokerIsUp(oldStatus))  {
                notifyClusterBrokerDown(brokerid);
	    }
        }

    
       /**
        * Called when the state of a broker has changed. The
        * state may not be accurate if a previous listener updated
        * the state for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldState the previous state.
        * @param newState the new state.
        */
        public void brokerStateChanged(String brokerid,
                      com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState oldState, 
		      com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState newState) {
	    if (!Globals.getHAEnabled())  {
		return;
	    }

	    String localBrokerID = Globals.getBrokerID(), takeoverBrokerID;

	    if (( (newState == com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_PENDING) ||
		  (newState == com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_STARTED) ) &&
		( (oldState != com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_PENDING) &&
		  (oldState != com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_STARTED) ) )  {
		takeoverBrokerID = getTakeoverBrokerID(brokerid);

		if ((takeoverBrokerID == null) || (!takeoverBrokerID.equals(localBrokerID)))  {
		    return;
		}

                notifyTakeoverStart(brokerid);
	    }

	    if ((newState == com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_COMPLETE) &&
	        (oldState != com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_COMPLETE))  {
		takeoverBrokerID = getTakeoverBrokerID(brokerid);

		if ((takeoverBrokerID == null) || (!takeoverBrokerID.equals(localBrokerID)))  {
		    return;
		}

                notifyTakeoverComplete(brokerid);
	    }

	    if ((newState == com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_FAILED) &&
	        (oldState != com.sun.messaging.jmq.jmsserver.cluster.api.BrokerState.FAILOVER_FAILED))  {
		takeoverBrokerID = getTakeoverBrokerID(brokerid);

		if ((takeoverBrokerID == null) || (!takeoverBrokerID.equals(localBrokerID)))  {
		    return;
		}

                notifyTakeoverFail(brokerid);
	    }
        }

	private String getTakeoverBrokerID(String failoverBrokerID)  {
	    ClusterManager cm = Globals.getClusterManager();

	    if (cm == null)  {
	        return (null);
	    }

	    ClusteredBroker cb = cm.getBroker(failoverBrokerID);

	    if (cb == null)  {
	        return (null);
	    }

	    Hashtable bkrInfo = GetClusterHandler.getBrokerClusterInfo(cb, logger);

	    if (bkrInfo == null)  {
	        return (null);
	    }

	    String takeoverBrokerID = (String)bkrInfo.get(BrokerClusterInfo.TAKEOVER_BROKER_ID);
	    return (takeoverBrokerID);
	}
    
       /**
        * Called when the version of a broker has changed. The
        * state may not be accurate if a previous listener updated
        * the version for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param oldVersion the previous version.
        * @param newVersion the new version.
        */
        public void brokerVersionChanged(String brokerid,
                      int oldVersion, int newVersion) {
            // we dont care
        }
    
       /**
        * Called when the url address of a broker has changed. The
        * address may not be accurate if a previous listener updated
        * the address for this specific broker.
        * @param brokerid the name of the broker updated.
        * @param newAddress the previous address.
        * @param oldAddress the new address.
        */
        public void brokerURLChanged(String brokerid,
                      MQAddress oldAddress, MQAddress newAddress) {
            // we dont care
        }
    }    
}
