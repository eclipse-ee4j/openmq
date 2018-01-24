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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;
import java.util.StringTokenizer;

import javax.jms.JMSSecurityException;

import com.sun.messaging.jmq.jmsspi.JMSAdmin;
import com.sun.messaging.jmq.jmsspi.JMSAdminFactory;
import com.sun.messaging.jmq.jmsspi.PropertiesHolder;


/**
 *  Runs a local broker instance through
 *  the JMSSPI interfaces exposed for out-of-process
 *  broker lifecycle control
 */

public class LocalBrokerRunner {

    /* Properties to be used by the embedded broker */
    //private Properties brokerProps = null;

    /* The JMS Admin SPI related objects */
    private JMSAdmin jmsadmin = null;
    private JMSAdmin tjmsadmin = null;

    /* The admin username that was configured when starting the broker */
    private String adminUsername;

    /* The admin password file that was configured when starting the broker */
    private String adminPassFile;

    /* The cmd line args for starting the broker */
    String[] brokerArgs;

    /* The bin directory for starting the broker */
    String brokerBinDir;

    /* The var directory to be used by the broker */
    String brokerVarDir;

    /* The instance name to be used for starting the broker */
    String brokerInstanceName;

    /* The full path to the broker log file to be checked for broker start failures */
    String brokerLogFilename;

    /* The timeout allowed when starting the broker */
    int brokerStartTimeout;

    /* Indicates the broker was started by AppServer(LOCAL) - ok to shutdown */
    private boolean startedByAS = false;
    private boolean startingRMIRegistry = false;

    /* BrokerURL for this LocalBrokerRunner */
    private String brokerURL;

    /* The main broker thread */
    //private Thread bt = null;

    /* Loggers */
    private static transient final String _className =
            "com.sun.messaging.jms.ra.LocalBrokerRunner";
    protected static transient final String _lgrNameLifecycle =
            "javax.resourceadapter.mqjmsra.lifecycle";
    protected static transient final Logger _loggerL =
            Logger.getLogger(_lgrNameLifecycle);
    protected static transient final String _lgrMIDPrefix = "MQJMSRA_LB";
    protected static transient final String _lgrMID_EET = _lgrMIDPrefix + "1001: ";
    protected static transient final String _lgrMID_INF = _lgrMIDPrefix + "1101: ";
    protected static transient final String _lgrMID_WRN = _lgrMIDPrefix + "2001: ";
    protected static transient final String _lgrMID_ERR = _lgrMIDPrefix + "3001: ";
    protected static transient final String _lgrMID_EXC = _lgrMIDPrefix + "4001: ";

    /**
	 * @param brokerURL
	 *            URL (imqAddressList) that can be used to connect to managed broker for administrative purposes
	 * @param brokerInstanceName
	 * @param brokerBindAddress
	 * @param brokerPort
	 * @param brokerHomeDir
	 * @param brokerLibDir
	 * @param brokerVarDir
	 * @param brokerJavaDir
	 * @param brokerExtraArgs
	 * @param useJNDIRMIServiceURL
	 * @param rmiRegistryPort
	 * @param startRMIRegistry
	 * @param useSSLJMXConnector
	 * @param brokerStartTimeout
	 * @param adminUsername
	 * @param adminPassFile
	 * @param brokerPropertiesHolder
	 *            Holder of Properties to be passed to the managed broker. A PropertiesHolder is used to allow the
	 *            Properties to be regenerated if broker is restarted
	 * @throws Exception
	 */
    public LocalBrokerRunner(String brokerURL, String brokerInstanceName, String brokerBindAddress, int brokerPort,
			String brokerHomeDir, String brokerLibDir, String brokerVarDir, String brokerJavaDir,
			String brokerExtraArgs, boolean useJNDIRMIServiceURL, int rmiRegistryPort, boolean startRMIRegistry,
			boolean useSSLJMXConnector, int brokerStartTimeout, String adminUsername, String adminPassFile,
			PropertiesHolder brokerPropertiesHolder) throws Exception {
    	
    	if (_loggerL.isLoggable(Level.FINER)) {
			Object params[] = new Object[16];
			params[0] = brokerURL;
			params[1] = brokerInstanceName;
			params[2] = brokerBindAddress;
			params[3] = Integer.toString(brokerPort);
			params[4] = brokerHomeDir;
			params[5] = brokerLibDir;
			params[6] = brokerVarDir;
			params[7] = brokerJavaDir;
			params[8] = brokerExtraArgs;
			params[9] = Boolean.valueOf(useJNDIRMIServiceURL);
			params[10] = Integer.valueOf(rmiRegistryPort);
			params[11] = Boolean.valueOf(startRMIRegistry);
			params[12] = Boolean.valueOf(useSSLJMXConnector);
			params[13] = adminUsername;
			params[14] = adminPassFile;
			params[15] = brokerPropertiesHolder.getProperties();
			_loggerL.entering(_className, "constructor()", params);
		}

        this.brokerURL = brokerURL;
        this.adminUsername = adminUsername;
        this.brokerStartTimeout = brokerStartTimeout;
        
        String adminPassword = brokerPropertiesHolder.getProperties().getProperty("imq.imqcmd.password");
 
        jmsadmin = ((JMSAdminFactory)(new com.sun.messaging.jmq.admin.jmsspi.JMSAdminFactoryImpl())).getJMSAdmin(
                                brokerURL, brokerPropertiesHolder, adminUsername, adminPassword);
        tjmsadmin = ((JMSAdminFactory)(new com.sun.messaging.jmq.admin.jmsspi.JMSAdminFactoryImpl())).getJMSAdmin(
                                brokerURL, brokerPropertiesHolder, "admin", "admin");

        checkVersion(jmsadmin);

        Vector<String> v = new Vector<String>();

        if (brokerJavaDir != null) {
            v.add("-javahome");
            v.add(brokerJavaDir);
        }

        //Add extra args first; explicit config will override args 
        if (brokerExtraArgs != null && !("".equals(brokerExtraArgs)) ) {
            StringTokenizer st = new StringTokenizer(brokerExtraArgs, " ");
            while (st.hasMoreTokens()) {
                String t = st.nextToken();
                v.add(t);
            }
        }

        this.brokerInstanceName = brokerInstanceName;
        this.brokerBinDir = brokerHomeDir + java.io.File.separator + "bin";
        this.brokerVarDir = brokerVarDir;
        this.brokerLogFilename = brokerVarDir
                                     + java.io.File.separator + "instances"
                                     + java.io.File.separator + brokerInstanceName
                                     + java.io.File.separator + "log"
                                     + java.io.File.separator + "log.txt" ;
        this.startedByAS = false;
        this.startingRMIRegistry = false;

        if (brokerVarDir != null) {
            v.add("-varhome");
            v.add(brokerVarDir);
        }

        if (useJNDIRMIServiceURL == true) {
            if (startRMIRegistry == true) {
                v.add("-startRmiRegistry");
                this.startingRMIRegistry = true;
            } else {
                v.add("-useRmiRegistry");
                this.startingRMIRegistry = false;
            }
            v.add("-rmiRegistryPort");
            v.add(Integer.toString(rmiRegistryPort));
        }

		if ((adminUsername != null) && !("".equals(adminUsername))) {
			 if ((adminPassFile != null) && !("".equals(adminPassFile))) {
            v.add("-Dimq.imqcmd.user="+adminUsername);
            v.add("-passfile");
            v.add(adminPassFile);

            //Set adminPassFile to non-null
            this.adminPassFile = adminPassFile;
			} else if ((adminPassword != null) && !("".equals(adminPassword))) {
				v.add("-Dimq.imqcmd.user=" + adminUsername);
        }
		}
		
        if (brokerBindAddress != null && !("localhost".equals(brokerBindAddress)) ) {
            v.add("-Dimq.hostname="+brokerBindAddress);
        }
        //Add -save cli option to have the broker save properties at startup
        //The properties that are to be saved are defined by the broker
        //Enables MQ cli utilities that depend on the configuration to be run
        //after AS has shut down.
        v.add("-save");
 
        brokerArgs = (String []) v.toArray(new String[0]);

    }

    protected synchronized void
    start()
    throws Exception
    {
        _loggerL.entering(_className, "start()");
        boolean already_running = false;
        String running_varhome = null;

        if (this.startingRMIRegistry) {
            //Condition this on the AppServer making MQ starting an RMI Registry
            //Else if we use an existsing broker that was uses AppServer's RMI
            //Registry, no asadmin command will work as the stubs will not
            //have been bound by the MQ broker into the new App Server Registry
            _loggerL.fine(_lgrMID_INF+"Looking for Broker Running at:"+
                        this.brokerURL);
            try {
                jmsadmin.pingProvider();
                _loggerL.info(_lgrMID_INF+"Detected Broker Running at:"+
                        this.brokerURL);
                already_running = true;
            } catch (Exception e){
                already_running = false;
            }
            if (already_running) {
                this.startedByAS = false;
                try {
                    jmsadmin.connectToProvider();
                    running_varhome = jmsadmin.getProviderVarHome();
                } catch (Exception e){
                    //can't get the varhome directory
                    //will fall through to assume not startedbyAS and fail start
                }
                _loggerL.info(_lgrMID_INF+"Detected Broker VAR directory="+
                        running_varhome);
                if (running_varhome != null && !("".equals(running_varhome)) &&
                        (running_varhome.equals(this.brokerVarDir))){
                        //Ok to connect to the broker; This was started by us
                    this.startedByAS = true;
                    //Ok to attempt to connect and use it
                } else {
                    //This broker cannot be used by AS since it has a different
                    //varhome directory than the one AS is asking the broker to use
                    //Using it could cause problems
                    //Therefore we need to abort the start and report a failure
                    String excerrmsg = _lgrMID_EXC+"start:Broker running "+
                            "at:" + this.brokerURL +
                            " has a different var directory of:" +
                            running_varhome +
                            ":Failing ra.start()";
                    this._loggerL.severe(excerrmsg);
                    throw new Exception(excerrmsg);
                }
            } else {
                //broker not running at the designated port; attempt to start one
                jmsadmin.startProvider(brokerBinDir, brokerArgs, brokerInstanceName);
                this.startedByAS = true;
            }
        } else {
            jmsadmin.startProvider(brokerBinDir, brokerArgs, brokerInstanceName);
            this.startedByAS = true;
        }

        //Now try to connect to the provider
        boolean ready = false;
        long firstPingTime = java.lang.System.currentTimeMillis();

        while (true) {
            try {
                //Wait a sec before attempting connection
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {}
                //Change to make the connection at start()and keep it alive
                //till stop(), else trying to connect at stop() when broker
                //store fails will fail and broker will remain running
                //jmsadmin.pingProvider();
                if (!already_running){
                    jmsadmin.connectToProvider();
                }
                ready = true;
                break;
            } catch (JMSSecurityException jmsse) {
                //Here because the auth info is incorrect  and the broker was able to be started.
                //i.e. they did not use adminPassFile to prevent broker startup with wrong auth
                //Attempt to shut down broker with the default admin/admin username/password
                //And then trow an Exception back to ra.start()

                Exception startEx = new Exception(_lgrMID_EXC+"start:Unable to ping Broker due to authentication error for user " + adminUsername + " : shutting down broker.");
                startEx.initCause(jmsse);
                try {
                    tjmsadmin.connectToProvider();
                    tjmsadmin.shutdownProvider();
                } catch (Exception spe) {
                    _loggerL.warning(_lgrMID_EXC+"start:Exception on LOCAL broker shutdown after connect auth failure:msg="+spe.getMessage());
                    //spe.printStackTrace();
                }
                throw startEx;
            } catch (Exception e) {
                if (java.lang.System.currentTimeMillis() - firstPingTime >= brokerStartTimeout) {
                    // Couldn't connect to broker, we've tried for long enough, assume broker is not running and throw an exception
                	// note that even if the broker were running we couldn't shut it down here since we can't connect to it
                    _loggerL.severe(_lgrMID_EXC+"start:Ping broker failed " + (java.lang.System.currentTimeMillis() - firstPingTime) + " millis after broker start performed. Failing ra.start()");
                    _loggerL.warning(_lgrMID_EXC+"start:Aborting:Check Broker Log File at:"+brokerLogFilename);
                    break;
                } else {
                    // couldn't connect to local broker, perhaps it hasn't started yet, try again
                }
            }
        }
        if (!ready) {
            throw new RuntimeException(_lgrMID_EXC+"start:Aborted:Unable to ping Broker within " + brokerStartTimeout + " millis (startTimeOut)");
        }
    }

    protected synchronized void
    stop()
    {
        _loggerL.entering(_className, "stop()");
        if (jmsadmin != null) {
            if (!this.startedByAS){
                //We cannot shut down this broker as it was not started by AS
                //It does not have the same varhome directory
                _loggerL.warning(_lgrMID_WRN+"stop:" +
                        "Skipping LOCAL broker shutdown:"+
                        "Broker not started by App Server");
                return;
            }
            try {
                //Chg: use jmsadmin connection that was acquired at start()
                //jmsadmin.connectToProvider();
                jmsadmin.shutdownProvider();
            } catch (Exception bse) {
                _loggerL.warning(_lgrMID_EXC+"stop:Exception on LOCAL broker shutdown:msg="+bse.getMessage());
                if (_loggerL.isLoggable(Level.FINER)) {
                    bse.printStackTrace();
                }
            }
        }
    }

    private static void
    checkVersion(JMSAdmin jmsadm)
    {
        float ver_f = 0.0f;
        String ver_s = "?.?";
        try {
            ver_s = jmsadm.getVersion();
            ver_f = Float.parseFloat(ver_s);
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing SJSMQ SPI version string (" + ver_s + ").");
        }
        if (ver_f < 2.0 || ver_f >= 3.0) {
            throw new RuntimeException("Incorrect SJSMQ SPI version detected (" + ver_s + ").");
        }
    }
}
