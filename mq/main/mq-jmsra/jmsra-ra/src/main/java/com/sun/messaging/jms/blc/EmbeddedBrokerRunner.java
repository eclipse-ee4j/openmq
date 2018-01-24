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

package com.sun.messaging.jms.blc;

import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.messaging.jmq.jmsclient.runtime.BrokerInstance;
import com.sun.messaging.jmq.jmsclient.runtime.ClientRuntime;
import com.sun.messaging.jmq.jmsservice.BrokerEvent;
import com.sun.messaging.jmq.jmsservice.BrokerEventListener;
import com.sun.messaging.jmq.jmsservice.JMSService;
import com.sun.messaging.jms.ra.ResourceAdapter;

/**
 *  Runs an embedded broker instance through
 *  the Broker* interfaces exposed for in-process
 *  broker lifecycle control
 */

public class EmbeddedBrokerRunner
        implements BrokerEventListener {

    /** Properties to be used by the embedded broker */
    private Properties brokerProps = null;

    /** The in-VM broker instance  */
    private BrokerInstance directBroker;

    /** The JMSService acquired from the JMSBroker (used only if the RA is using RADirect) */
    private JMSService jmsservice= null;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.EmbeddedBrokerRunner";
    protected static transient final String _lgrNameLifecycle =
            "javax.resourceadapter.mqjmsra.lifecycle";
    protected static transient final Logger _loggerL =
            Logger.getLogger(_lgrNameLifecycle);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_EB";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    public EmbeddedBrokerRunner(
            String brokerTypeArgIgnored, String brokerInstanceName, String brokerBindAddress, int brokerPort,
            String brokerHomeDir, String brokerLibDir, String brokerVarDir, String brokerJavaDir,
            String brokerExtraArgs,
            boolean useJNDIRMIServiceURL, int rmiRegistryPort, boolean startRMIRegistry,
            boolean useSSLJMXConnector, boolean doBind, Properties sysProps) throws ClassNotFoundException, IllegalAccessException, InstantiationException
    {
    	
    	/**
    	 * NOTE: If we ever implement embedded broker restart we need to make sure that the restarted broker 
    	 * uses the latest versions of the broker properties, probably using a PropertiesHolder in the same
    	 * was as LocalBrokerRunner does. We would then need to extend TestResourceAdapterSetClusterBrokerList to test this.
    	 */
    	
    	if (_loggerL.isLoggable(Level.FINER)){
    		// log input parameters
	        Object params[] = new Object[14];
	        params[0] = brokerInstanceName;
	        params[1] = brokerBindAddress;
	        params[2] = Integer.toString(brokerPort);
	        params[3] = brokerHomeDir;
	        params[4] = brokerLibDir;
	        params[5] = brokerVarDir;
	        params[6] = brokerJavaDir;
	        params[7] = brokerExtraArgs;
	        params[8] = Boolean.valueOf(useJNDIRMIServiceURL);
	        params[9] = Integer.valueOf(rmiRegistryPort);
	        params[10] = Boolean.valueOf(startRMIRegistry);
	        params[11] = Boolean.valueOf(useSSLJMXConnector);
	        params[12] = doBind;
	        params[13] = sysProps;
	        _loggerL.entering(_className, "constructor()", params);
    	}

        // assemble broker arguments from method arguments
       	String[] brokerArgs = assembleBrokerArgs(brokerInstanceName,
				brokerPort, brokerHomeDir, brokerLibDir, brokerVarDir,
				brokerExtraArgs, useJNDIRMIServiceURL, rmiRegistryPort,
				startRMIRegistry, doBind);
    	
       	// instantiate the broker instance
        createTheInVMBrokerInstance();
		
		// parse the broker args into a properties object that will be passed to the broker
        // (need to do this after we instantiate the broker instance)
        // Args of the form -D are converted to explicit properties
        // Args such as -varhome which implicitly set properties are converted to the property in question
        // All other args are assembled in a single "BrokerArgs" property
		brokerProps=parseArgs(brokerArgs);

        if (brokerBindAddress != null && !("localhost".equals(brokerBindAddress)) ) {
            brokerProps.setProperty("imq.hostname", brokerBindAddress);
        }
        
        // add the supplied properties to the properties object that will be passed to the broker
        for (Enumeration e = sysProps.keys() ; e.hasMoreElements() ;) {
            String key = (String)e.nextElement();
            if (key != null) {
                String val = (String)sysProps.getProperty(key);
                brokerProps.setProperty(key, val);
            }
        }
        
        // tell the embedded broker to log these properties at startup
        logSysPropsInbrokerLog(sysProps);
        
    }
    
	/**
	 * Tell the broker to log the specified broker properties (as a single INFO message) at startup
	 * 
	 * @param props
	 */
	private void logSysPropsInbrokerLog(Properties props) {
		
		if (props.isEmpty()) return;		
		
		// log all properties except for passwords
		boolean first=true;
		String stringToLog="";
		for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
			String thisPropertyName = (String) e.nextElement();
			String thisPropertyValue = "";
			if (thisPropertyName.endsWith("password")){
				// don't log the password!
				thisPropertyValue = "*****";
			} else {
				thisPropertyValue = props.getProperty(thisPropertyName);
			}
			if (first) {
				first=false;
			} else {
				stringToLog += ", ";
			}

		    stringToLog+=thisPropertyName+"="+thisPropertyValue;
		}
	
		// tell the broker to log this message when it starts
        directBroker.addEmbeddedBrokerStartupMessage("JMSRA BrokerProps: "+stringToLog);

	}

    /**
     * Assemble a String[] of broker arguments corresponding to the supplied method arguments
     * 
     * @param brokerInstanceName
     * @param brokerPort
     * @param brokerHomeDir
     * @param brokerLibDir
     * @param brokerVarDir
     * @param brokerExtraArgs
     * @param useJNDIRMIServiceURL
     * @param rmiRegistryPort
     * @param startRMIRegistry
     * @return The String[] of broker arguments
     */
	private String[] assembleBrokerArgs(String brokerInstanceName,
			int brokerPort, String brokerHomeDir, String brokerLibDir,
			String brokerVarDir, String brokerExtraArgs,
			boolean useJNDIRMIServiceURL, int rmiRegistryPort,
			boolean startRMIRegistry, boolean doBind) {
    	
        Vector<String> v = new Vector<String>();

        //Add extra args first; explicit config will override args 
        if (brokerExtraArgs != null && !("".equals(brokerExtraArgs)) ) {
            StringTokenizer st = new StringTokenizer(brokerExtraArgs, " ");
            while (st.hasMoreTokens()) {
                String t = st.nextToken();
                v.add(t);
            }
        }

        //Add explicit config
        //
        v.add("-port");
        v.add(Integer.toString(brokerPort));

        if (brokerInstanceName != null) {
            v.add("-name");
            v.add(brokerInstanceName);
        }
        
        if (!doBind) {
            v.add("-nobind");
        }
 
        if (brokerHomeDir != null) {
            v.add("-imqhome");
            v.add(brokerHomeDir);
        }
 
        if (brokerVarDir != null) {
            v.add("-varhome");
            v.add(brokerVarDir);
        }

        if (brokerLibDir != null && !("".equals(brokerLibDir))) {
            v.add("-libhome");
            v.add(brokerLibDir);
        }

        if (useJNDIRMIServiceURL == true) {
            if (startRMIRegistry == true) {
                v.add("-startRmiRegistry");
            } else {
                v.add("-useRmiRegistry");
            }
            v.add("-rmiRegistryPort");
            v.add(Integer.toString(rmiRegistryPort));
        }
        
        //Add -save cli option to have the broker save properties at startup
        //The properties that are to be saved are defined by the broker
        //Enables MQ cli utilities that depend on the configuration to be run
        //after AS has shut down.
        v.add("-save");

        //Add -silent cli option to minimize broker output to AS log
        v.add("-silent");

    	String[] brokerArgs = (String []) v.toArray(new String[0]);
		return brokerArgs;
	}

    /**
     * Create the in-JVM broker instance
     * 
     * Requires field: brokerType
     * Sets fields: directBroker  
     * @throws InstantiationException 
     * @throws IllegalAccessException 
     * @throws ClassNotFoundException 
     */
	private void createTheInVMBrokerInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {

		// get a client runtime object
		ClientRuntime runtime = ClientRuntime.getRuntime();

		// get a direct broker instance
//		try {
			directBroker = runtime.createBrokerInstance();
//		} catch (ClassNotFoundException e) {
//			System.out.println("SJSMQRA_EB:DebugCFN-ExcMsg=" + e.getMessage());
//			e.printStackTrace();
//		} catch (IllegalAccessException e) {
//			System.out.println("SJSMQRA_EB:DebugCFN-ExcMsg=" + e.getMessage());
//			e.printStackTrace();
//		} catch (InstantiationException e) {
//			System.out.println("SJSMQRA_EB:DebugCFN-ExcMsg=" + e.getMessage());
//			e.printStackTrace();
//		}
	}		
    
	/**
	 * Parse the supplied broker arguments and convert them into a Properties object
	 * 
	 * Requires fields: brokerType, apiDirectBroker or raDirectBroker 
	 * 
	 * @param brokerArgs the supplied broker arguments
	 * @return a Properties object corresponding to the supplied arguments
	 */
    private Properties parseArgs(String[] brokerArgs){
    	
    	return directBroker.parseArgs(brokerArgs);

    }

    public synchronized void
    init()
    {
        _loggerL.entering(_className, "init()");
        
       	directBroker.init(brokerProps, this);

    }

    public synchronized void start() {
		_loggerL.entering(_className, "start()");
		_loggerL.config(_lgrMID_INF + "EB-start:brokerProps=" + brokerProps.toString());

		directBroker.start();
	}

    protected synchronized void stop() {
		_loggerL.entering(_className, "stop()");
		try {
			// brokerInstance field will get set to null when we call stop() so take a copy 
			BrokerInstance brokerInstance = directBroker;
			brokerInstance.stop();
			brokerInstance.shutdown();
			directBroker = null;
		} catch (Exception bse) {
			_loggerL.severe(_lgrMID_EXC + "stop:Exception on in-VM broker shutdown:msg=" + bse.getMessage());
			bse.printStackTrace();
		}
	}

    public JMSService getJMSService(){
    	
        if (jmsservice == null) {
        		jmsservice = directBroker.getJMSService();
        }
        return jmsservice;
    }
    
    //BrokerEventListener methods
    public void brokerEvent(BrokerEvent bErrEvt){
        _loggerL.fine(_lgrMID_INF+"stateChanged:" + bErrEvt);
        if (bErrEvt.getType()==BrokerEvent.Type.SHUTDOWN){
        	synchronized(this){ 	
        		directBroker=null;
        	}
        }
//        if (bErrEvt.getType()==BrokerEvent.Type.RESTART){
//        	//System.out.println("Restarting embedded broker");
//        	stop();
//            // create the in-JVM broker instance
//        	createTheInVMBrokerInstance();
//
//        	init();
//        	start();
//        }
    }
    
    /**
     *  Notify the BrokerEventLstener that the broker would like to shut down.<p>
     *
     *  @param event The BrokerEvent that contains information about the reason
     *               why the broker is requesting to exit. The event Id could
     *               be one of:<p>
     *       <UL>
     *         <LI>REASON_SHUTDOWN</LI>
     *         <LI>REASON_RESTART</LI>
     *         <LI>REASON_FATAL</LI>
     *         <LI>REASON_ERROR</LI>
     *         <LI>REASON_EXCEPTION</LI>
     *         <LI>REASON_STOP</LI>
     *       </UL>
     *  @param thr An optional Throwable
     *
     *  @return true Not used
     */
    public boolean exitRequested (BrokerEvent bEvt, Throwable thr){
    	return true;
    }

}
