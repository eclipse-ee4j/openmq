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
 * @(#)FileConfigStore.java	1.76 09/05/07
 */ 

package com.sun.messaging.jmq.jmsserver.config;

import java.util.*;
import java.net.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 * this is the implementation of ConfigStore used to
 * store configuration properties in java property files
 * on the local system.
 *
 * @see ConfigStore
 * @see BrokerConfig
 */

public class FileConfigStore implements ConfigStore
{

    //private static boolean DEBUG = false;

 
    /**
     * property which contains the URL to the cluster property file (if any)
     */
    private static final String cluster_prop = CommGlobals.IMQ + ".cluster.url";

    /**
     * path to the actual location of the instance property file 
     */
    private String storedprop_loc = null;

    /**
     * logger
     */
    private final Logger logger = CommGlobals.getLogger();

    /**
     * loads the instance properties
     *
     * @param currentprops already loaded properties (including
     *             system, default and install properties)
     * @param instancename the name used by the broker, passed in at startup
     *
     * @return a properties object with the correct instance properties
     *
     * @throws BrokerException if a fatal error occurs loading the 
     *         config store
     */

    public Properties loadStoredProps(Properties currentprops,
                   String instancename) 
           throws BrokerException
    {
        // now load the stored properties seperately (for storing later)
        //

	/**
	 * full path to the editable JMQ configuration location.
	 */
        String vardirectory = CommGlobals.getJMQ_INSTANCES_HOME() + File.separator 
                  + instancename + File.separator+ "props" + File.separator;
        storedprop_loc =  vardirectory+ "config.properties";
        Properties storedprops = new Properties();
        try {
            FileInputStream  cfile = new FileInputStream(storedprop_loc);
            BufferedInputStream  bfile = new BufferedInputStream(cfile);
            storedprops.load(bfile);
            cfile.close();
            bfile.close();
        } catch (IOException ex) {

            File f = new File(storedprop_loc);
            if (f.exists()) {
                logger.logStack(Logger.WARNING,
			BrokerResources.W_BAD_PROPERTY_FILE,
			instancename, storedprop_loc, ex);
            } else {
                logger.log(Logger.INFO,
			BrokerResources.I_NEW_PROP, instancename);
             }

            // make the directory (if necessary)
            File dir = new File(vardirectory);
            if (!dir.exists())
                dir.mkdirs();

            if (!f.exists()) {
                try {
                   // Create a new config.properties. Make sure version 
                   // property is set.
		   storedprops.setProperty(
                    BrokerConfig.INSTANCECONFIG_VERSION_PROP,
                    BrokerConfig.CONFIG_VERSION);
                   storeProperties(storedprops);
                } catch (IOException ex1) {
                    logger.logStack(Logger.WARNING,
			BrokerResources.X_CANNOT_CREATE_PROP_FILE,
			f.toString(), ex);
                }
            }
        }

        // Check version
        String version =
            storedprops.getProperty(BrokerConfig.INSTANCECONFIG_VERSION_PROP);
        if (version != null && !version.equals("") &&
            !version.equals(BrokerConfig.CONFIG_VERSION)) {
            Object args[] = {
                storedprop_loc,
                BrokerConfig.CONFIG_VERSION,
                version};
            logger.log(Logger.WARNING, BrokerResources.W_BAD_CONFIG_VERSION,
                args);
        }

        return storedprops;
    }


    /**
     * loads the cluster properties
     *
     * @param currentprops already loaded properties (including
     *             system, default and install properties)
     * @param parameters properties passed in on the command line
     * @param instanceprops properties returned from the 
     *             loadStoredProps method
     *
     * @return a properties object with the correct cluster properties
     *            (or null if there aren't any)
     *
     * @throws BrokerException if a fatal error occurs loading the 
     *         config store
     */

    public Properties loadClusterProps(Properties currentprops,
                     Properties parameters,
                     Properties instanceprops) 
           throws BrokerException
    {
        String cluster_url_str = parameters.getProperty(cluster_prop,
                       instanceprops.getProperty(cluster_prop,
                              currentprops.getProperty(cluster_prop)));

        return loadClusterProps(cluster_url_str);
    }

    private Properties loadClusterProps(String cluster_url_str) {
        if (cluster_url_str == null)
                return null;
        Properties storedprops = new Properties();

        try {
            URL curl = new URL(cluster_url_str);
            InputStream cu_is = curl.openStream();
            BufferedInputStream  bfile = new BufferedInputStream(cu_is);
            storedprops.load(bfile);
            bfile.close();
            cu_is.close();
        } catch (IOException ex) {
            logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY_FILE, 
                      "cluster", cluster_url_str, ex);
        }
        return storedprops;
    }

    /**
     * stores the modified properties
     *
     * @param props the list of properties to store
     *
     * @throws IOException if the property can not be stored
     */

    public void storeProperties(Properties props)
              throws IOException
    {
        try {
            FileOutputStream  cfile = new FileOutputStream(storedprop_loc);
            BufferedOutputStream bos = new BufferedOutputStream(cfile);
            props.store(bos, PROP_HEADER_STR);
            bos.close();
            cfile.close();
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR, BrokerResources.E_PROPERTY_WRITE, 
                      storedprop_loc, ex);

            // re-throw the exception
            throw ex;
        }
    }

    /**
     * Reload the specified properties from the store.
     *
     * @param instancename the name used by the broker, passed in at startup
     *
     * @param propnames Array containing names of the properties
     * to be reloaded.
     */
    public Properties reloadProps(String instancename, String[] propnames)
        throws BrokerException {
        Properties props = loadStoredProps(null, instancename);
        if (props == null)
            return null;

        Properties ret = new Properties();
        for (int i = 0; i < propnames.length; i++) {
            String value = props.getProperty(propnames[i]);
            if (value != null)
                ret.setProperty(propnames[i], value);
        }

        /*
        String cluster_url_str = props.getProperty(cluster_prop, null);
        props = loadClusterProps(cluster_url_str);
        if (props == null)
            return ret;

        for (int i = 0; i < propnames.length; i++) {
            String value = props.getProperty(propnames[i]);
            if (value != null)
                ret.setProperty(propnames[i], value);
        }
        */

        props = null;
        return ret;
    }

    public void clearProps(String instancename)
    {
        String vardirectory = CommGlobals.getJMQ_INSTANCES_HOME() + File.separator 
                  + instancename + File.separator+ "props" + File.separator;
        storedprop_loc =  vardirectory+ "config.properties";
        File f = new File(storedprop_loc);
        try {
            f.delete();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING,
			BrokerResources.X_CANNOT_CREATE_PROP_FILE,
			f.toString(), ex);
        }
    }
//-----------------------------------------------------------------------
// HEADER
//-----------------------------------------------------------------------

    /**
     * string placed at the top of the instance property file 
     */
    static final String PROP_HEADER_STR = 
		"#\n" +
		"# Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.\n" +
		"#\n" +
		"# This program and the accompanying materials are made available under the\n" +
		"# terms of the Eclipse Public License v. 2.0, which is available at\n" +
		"# http://www.eclipse.org/legal/epl-2.0.\n" +
		"#\n" +
		"# This Source Code may also be made available under the following Secondary\n" +
		"# Licenses when the conditions for such availability set forth in the\n" +
		"# Eclipse Public License v. 2.0 are satisfied: GNU General Public License,\n" +
		"# version 2 with the GNU Classpath Exception, which is available at\n" +
		"# https://www.gnu.org/software/classpath/license.html.\n" +
		"#\n" +
		"# SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0\n" +
		"#/\n" +
		"#############################################################################\n" +
        "#\n"+
        "# File: config.properties \n"+
        "#\n"+
        "#\n"+
        "# This file contains the instance specific properties for\n"+
        "# a running broker.\n"+
        "#\n"+
        "# Only some of the properties listed in the default.properties\n"+
        "# file should be changed (some of the properties are for \n"+
        "# field support or future features).\n"+
        "#\n"+
        "# WARNING: \n"+
        "#\n"+
        "#   Use care when editing this file by hand.\n"+
        "#\n"+
        "#   This file is automatically update.  Changes made while\n"+
        "#   the broker is running MAY be lost. Any comments added to\n"+
        "#   this file may be lost.\n"+
        "#\n"+
        "#   Only the properties listed below can be modified in a \n"+
        "#   supported configuration.\n"+
        "#\n"+
        "#   Please make any property changes at the bottom of this file.\n"+
        "#\n"+
        "###################################################################\n"+
        "#\n"+
        "#  Supported properties are:\n"+
        "#\n"+
        "# Connection Services Settings\n"+
        "# ----------------------------\n"+
        "#\n"+
        "#    General Connection Services\n"+
        "#\n"+
        "#            imq.service.activelist\n"+
        "#            imq.shared.connectionMonitor_limit\n"+
        "#            imq.hostname\n"+
        "#\n"+
        "#    Connection Service Specific Settings\n"+
        "#\n"+
        "#            imq.<service_name>.threadpool_model\n"+
        "#            imq.<service_name>.<protocol_name>.port\n"+
        "#            imq.<service_name>.<protocol_name>.hostname\n"+
        "#            imq.<service_name>.min_threads\n"+
        "#            imq.<service_name>.max_threads\n"+
        "#\n"+
        "#       configuration for the keystore used by the ssl service\n"+
        "#\n"+
        "#            imq.keystore.file.dirpath\n"+
        "#            imq.keystore.file.name\n"+
        "#            imq.passfile.enabled\n"+
        "#            imq.passfile.dirpath\n"+
        "#            imq.passfile.name\n"+
        "#            \n"+
        "#       http specific parameters\n"+
        "#\n"+
        "#            imq.httpjms.http.servletHost\n"+
        "#            imq.httpjms.http.servletPort\n"+
        "#            imq.httpjms.http.pullPeriod\n"+
        "#            imq.httpjms.http.connectionTimeout\n"+
        "#            \n"+
        "#       https specific parameters\n"+
        "#\n"+
        "#            imq.httpsjms.https.servletHost\n"+
        "#            imq.httpsjms.https.servletPort\n"+
        "#            imq.httpsjms.https.pullPeriod\n"+
        "#            imq.httpsjms.https.connectionTimeout\n"+
        "#\n"+
        "#    General JMX parameters\n"+
        "#\n"+
        "#            imq.jmx.hostname\n"+
        "#\n"+
        "#    General JMX Connector Services\n"+
        "#\n"+
        "#            imq.jmx.connector.activelist\n"+
        "#\n"+
        "#    JMX Connector specific settings\n"+
        "#\n"+
        "#            imq.jmx.connector.<connector server name>.urlpath\n"+
        "#            imq.jmx.connector.<connector server name>.useSSL\n"+
        "#            imq.jmx.connector.<connector server name>.brokerHostTrusted\n"+
        "#\n"+
        "#    RMI Registry (used by RMI JMX Connectors)\n"+
        "#\n"+
        "#            imq.jmx.rmiregistry.use\n"+
        "#            imq.jmx.rmiregistry.start\n"+
        "#            imq.jmx.rmiregistry.port\n"+
        "#\n"+
        "#    Portmapper Settings\n"+
        "#\n"+
        "#            imq.portmapper.hostname\n"+
        "#            imq.portmapper.port\n"+
        "#            imq.portmapper.backlog\n"+
        "#\n"+
        "# Message Router Settings\n"+
        "# -----------------------\n"+
        "#\n"+
        "#    Memory reclamation period\n"+
        "#\n"+
        "#            imq.message.expiration.interval\n"+
        "#\n"+
        "#    Message limits: broker\n"+
        "#\n"+
        "#            imq.system.max_count\n"+
        "#            imq.system.max_size\n"+
        "#\n"+
        "#    Individual message limits\n"+
        "#\n"+
        "#            imq.message.max_size\n"+
        "#\n"+
        "# Persistence Settings\n"+
        "# --------------------\n"+
        "#\n"+
        "#    Type of data store\n"+
        "#\n"+
        "#            imq.persist.store\n"+
        "#\n"+
        "#    File-based store\n"+
        "#\n"+
        "#            imq.persist.file.message.max_record_size\n"+
        "#            imq.persist.file.destination.message.filepool.limit\n"+
        "#            imq.persist.file.message.filepool.cleanratio\n"+
        "#            imq.persist.file.message.cleanup\n"+
        "#            imq.persist.file.sync.enabled\n"+
        "#\n"+
        "#    JDBC-based store\n"+
        "#\n"+
        "#            imq.brokerid\n"+
        "#            imq.persist.jdbc.dbVendor\n"+                
        "#            imq.persist.jdbc.<dbVendor>.driver\n"+
        "#            imq.persist.jdbc.<dbVendor>.opendburl\n"+
        "#            imq.persist.jdbc.<dbVendor>.createdburl\n"+
        "#            imq.persist.jdbc.<dbVendor>.closedburl\n"+
        "#            imq.persist.jdbc.<dbVendor>.user\n"+
        "#            imq.persist.jdbc.<dbVendor>.needpassword\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQVER41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQCREC41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQBKR41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQSES41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQDST41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQCON41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQCONSTATE41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQMSG41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQPROP41\n"+
        "#            imq.persist.jdbc.<dbVendor>.table.MQTXN41\n"+
        "#\n"+
        "#\n"+
        "# Memory Management Settings\n"+
        "# --------------------------\n"+
        "#\n"+
        "#            imq.<resource_state>.threshold\n"+
        "#            imq.<resource_state>.count\n"+
        "#\n"+
        "#\n"+
        "# Security Settings\n"+
        "# -----------------\n"+
        "#\n"+
        "#    Authentication\n"+
        "#\n"+
        "#            imq.authentication.type\n"+
        "#            imq.<service_name>.authentication.type\n"+
        "#            imq.authentication.basic.user_repository\n"+
        "#            imq.authentication.client.response.timeout\n"+
        "#\n"+
        "#    User Repository\n"+
        "#\n"+
        "#            imq.user_repository.ldap.server\n"+
        "#            imq.user_repository.ldap.principal\n"+
        "#            imq.user_repository.ldap.base\n"+
        "#            imq.user_repository.ldap.uidattr\n"+
        "#            imq.user_repository.ldap.usrformat\n"+
        "#            imq.user_repository.ldap.usrfilter\n"+
        "#            imq.user_repository.ldap.grpsearch\n"+
        "#            imq.user_repository.ldap.grpbase\n"+
        "#            imq.user_repository.ldap.gidattr\n"+
        "#            imq.user_repository.ldap.memattr\n"+
        "#            imq.user_repository.ldap.grpfilter\n"+
        "#            imq.user_repository.ldap.ssl.enabled\n"+
        "#            imq.user_repository.ldap.timeout\n"+
        "#\n"+
        "#            imq.user_repository.jaas.name\n"+
        "#            imq.user_repository.jaas.userPrincipalClass\n"+
        "#            imq.user_repository.jaas.groupPrincipalClass\n"+
        "#\n"+
        "#            imq.user_repository.file.dirpath\n"+
        "#            imq.user_repository.file.filename\n"+
        "#\n"+
        "#    Access Control\n"+
        "#            imq.accesscontrol.enabled\n"+
        "#            imq.<service_name>.accesscontrol.enabled\n"+
        "#            imq.accesscontrol.file.filename\n"+
        "#            imq.accesscontrol.file.dirpath\n"+
        "#            imq.<service_name>.accesscontrol.file.filename\n"+
        "#            imq.<service_name>.accesscontrol.file.dirpath\n"+
        "#            imq.accesscontrol.file.url\n"+
        "#            imq.<service_name>.accesscontrol.file.url\n"+
        "#\n"+
        "# Log Settings\n"+
        "# ------------\n"+
        "#\n"+
        "#    Log Level\n"+
        "#            imq.log.level\n"+
        "#            imq.log.timezone\n"+
        "#\n"+
        "#    Output Channels\n"+
        "#        File:\n"+
        "#            imq.log.file.rolloverbytes\n"+
        "#            imq.log.file.rolloversecs\n"+
        "#            imq.log.file.dirpath\n"+
        "#            imq.log.file.filename\n"+
        "#            imq.log.file.output\n"+
        "#        Console:\n"+
        "#            imq.log.console.stream\n"+
        "#            imq.log.console.output\n"+
        "#        Solaris syslog:\n"+
        "#            imq.log.syslog.facility\n"+
        "#            imq.log.syslog.logpid\n"+
        "#            imq.log.syslog.logconsole\n"+
        "#            imq.log.syslog.identity\n"+
        "#            imq.log.syslog.output\n"+
        "#\n"+
        "#    Metrics settings\n"+
        "#            imq.metrics.enabled\n"+
        "#            imq.metrics.interval\n"+
        "#            imq.metrics.topic.enabled\n"+
        "#            imq.metrics.topic.interval\n"+
        "#            imq.metrics.topic.persist\n"+
        "#            imq.metrics.topic.timetolive\n"+
        "#\n"+
        "# Destination Management Settings\n"+
        "# -------------------------------\n"+
        "#\n"+
        "#            imq.autocreate.topic\n"+
        "#            imq.autocreate.topic.consumerFlowLimit\n"+
        "#            imq.autocreate.queue\n"+
        "#            imq.autocreate.reaptime\n"+
        "#            imq.autocreate.queue.consumerFlowLimit\n"+
        "#            imq.autocreate.queue.maxNumActiveConsumers\n"+
        "#            imq.autocreate.queue.maxNumBackupConsumers\n"+
        "#            imq.autocreate.queue.localDeliveryPreferred\n"+
        "#            imq.autocreate.destination.isLocalOnly\n"+
        "#            imq.autocreate.destination.maxNumMsgs\n"+
        "#            imq.autocreate.destination.maxTotalMsgBytes\n"+
        "#            imq.autocreate.destination.maxNumProducers\n"+
        "#            imq.autocreate.destination.maxBytesPerMsg\n"+
        "#            imq.autocreate.destination.limitBehavior\n"+
        "#            imq.autocreate.destination.useDMQ\n"+
        "#\n"+
        "#            imq.destination.DMQ.truncateBody\n"+
        "#            imq.destination.logDeadMsgs\n"+
        "#\n"+
        "#\n"+
        "# Transaction Settings\n"+
        "# --------------------\n"+
        "#\n"+
        "#            imq.transaction.autorollback\n"+
        "#            imq.transaction.detachedTimeout\n"+
        "#            imq.transaction.producer.maxNumMsgs\n"+
        "#            imq.transaction.consumer.maxNumMsgs\n"+
        "#\n"+
        "#\n"+
        "# Cluster Management Settings\n"+
        "# ---------------------------\n"+
        "#\n"+
        "#    Cluster file location\n"+
        "#\n"+
        "#            imq.cluster.url\n"+
        "#\n"+
        "#    HA Cluster:\n"+
        "#\n"+
        "#        HA Cluster per broker settings\n"+
        "#\n"+
        "#            imq.brokerid\n"+
        "#\n"+
        "#        HA Cluster Configuration Setting \n"+
        "#\n"+
        "#        NOTE: Under normal circumstances, setting for these\n"+
        "#              properties should be made in the cluster.url file\n"+
        "#              where then can be accessed by all brokers in the\n"+
        "#              cluster, not in this file\n"+
        "#\n"+
        "#            imq.cluster.ha\n"+
        "#            imq.cluster.clusterid\n"+
        "#            imq.persist.store\n"+
        "#            imq.persist.jdbc.dbVendor\n"+
        "#\n"+
        "#    Non-HA Cluster:\n"+
        "#\n"+
        "#        Cluster per broker settings\n"+
        "#\n"+
        "#            imq.cluster.hostname\n"+
        "#            imq.cluster.port\n"+
        "#\n"+
        "#        Cluster Configuration Setting \n"+
        "#\n"+
        "#        NOTE: Under normal circumstances, setting for these\n"+
        "#              properties should be made in the cluster.url file\n"+
        "#              where then can be accessed by all brokers in the\n"+
        "#              cluster, not in this file\n"+
        "#\n"+
        "#            imq.cluster.brokerlist\n"+
        "#            imq.cluster.masterbroker\n"+
        "#            imq.cluster.transport\n"+
        "#\n"+
        "#\n"+
        "# Miscellaneous Settings\n"+
        "# ----------------------\n"+
        "#\n"+
        "#            imq.ping.interval\n"+
        "#\n"+
        "#\n"+
        "# Bridge Service Manager Settings\n"+
        "# -------------------------------\n"+
        "#\n"+
        "#            imq.bridge.enabled\n"+
        "#            imq.bridge.activelist\n"+
        "#            imq.bridge.admin.user\n"+
        "#\n"+
        "#\n"+
        "# Please see the documentation for specifics on how to set these\n"+
        "# properties.\n"+
        "#\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in a Sun HADB database, either uncomment or\n"+
        "# set 'dbVendor' property to 'hadb', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in a Sun HADB database\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=hadb\n"+
        "# Replace 'server list' with your comma-separated list of servers.\n"+
        "#imq.persist.jdbc.hadb.property.serverList=<server list>\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.hadb.user=<username>\n"+
        "#imq.persist.jdbc.hadb.needpassword=[true|false]\n"+
        "#\n"+
        "# End of properties to plug in a Sun HADB database\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in a MySQL database, either uncomment or\n"+
        "# set 'dbVendor' property to 'mysql', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in a MySQL database\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=mysql\n"+
        "# Replace hostname, port and database in imq.persist.jdbc.mysql.property.url.\n"+
        "#imq.persist.jdbc.mysql.property.url=jdbc:mysql://<hostname>:<port>/<database>\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.mysql.user=<username>\n"+
        "#imq.persist.jdbc.mysql.needpassword=[true|false]\n"+
        "#\n"+
        "# End of properties to plug in a MySQL database\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in a DB2 database, either uncomment or\n"+
        "# set 'dbVendor' property to 'db2', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in a DB2 database\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=db2\n"+
        "# Replace hostname, port and database in imq.persist.jdbc.db2.opendburl.\n"+
        "#imq.persist.jdbc.db2.opendburl=jdbc:db2://<hostname>:<port>/<database>\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.db2.user=<username>\n"+
        "#imq.persist.jdbc.db2.needpassword=[true|false]\n"+
        "#\n"+
        "# End of properties to plug in a DB2 database\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in an Oracle database, either uncomment or\n"+
        "# set 'dbVendor' property to 'oracle', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in an Oracle database\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=oracle\n"+
        "# Replace hostname, port and sid in imq.persist.jdbc.oracle.property.url.\n"+
        "#imq.persist.jdbc.oracle.property.url=jdbc:oracle:thin:@<hostname>:<port>:<sid>\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.oracle.user=<username>\n"+
        "#imq.persist.jdbc.oracle.needpassword=[true|false]\n"+
        "#\n"+
        "# End of properties to plug in an Oracle database\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in a Java DB (Derby) embedded database, either uncomment or\n"+
        "# set 'dbVendor' property to 'derby', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in a Java DB (Derby) embedded database\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=derby\n"+
        "#imq.persist.jdbc.derby.createdburl=jdbc:derby:${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb;create=true\n"+
        "#imq.persist.jdbc.derby.opendburl=jdbc:derby:${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb\n"+
        "#imq.persist.jdbc.derby.closedburl=jdbc:derby:;shutdown=true\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.derby.user=<username>\n"+
        "#imq.persist.jdbc.derby.needpassword=[true|false]\n"+
        "#\n"+
        "# End of properties to plug in a Java DB (Derby) embedded database\n"+
        "##############################################################\n"+
        "#\n"+
        "# To plug in a Java DB (Derby) Network Server, either uncomment or\n"+
        "# set 'dbVendor' property to 'derby', and edit the \n"+
        "# values according to your database configuration.\n"+
        "# Then, finish the steps outlined in the Administrative Guide\n"+
        "# to plug in and set up the database store.\n"+
        "#\n"+
        "##############################################################\n"+
        "# Beginning of properties to plug in a Java DB (Derby) Network Server\n"+
        "#\n"+
        "# Replace 'alphanumeric id' with your broker identifier\n"+
        "#imq.brokerid=<alphanumeric id>\n"+
        "#imq.persist.store=jdbc\n"+
        "#imq.persist.jdbc.dbVendor=derby\n"+
        "#imq.persist.jdbc.derby.createdburl=\n"+
        "#imq.persist.jdbc.derby.opendburl=jdbc:derby://<hostname>:<port>/${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb\n"+
        "#imq.persist.jdbc.derby.closedburl=\n"+
        "# Replace username.\n"+
        "#imq.persist.jdbc.derby.user=<username>\n"+
        "#imq.persist.jdbc.derby.needpassword=[true|false]\n"+
        "#imq.persist.jdbc.derby.driver=org.apache.derby.jdbc.ClientDriver\n"+
        "#\n"+
        "# End of properties to plug in a Java DB (Derby) Network Server\n"+
        "##############################################################\n"+
        "\n"+
        "\n"+
        "##############################################################\n"+
        "#\n"+
        "# An example of using Directory Server 5.2 as\n"+
        "# the user repository\n"+
        "#\n"+
        "##############################################################\n"+
        "#\n"+
        "#imq.authentication.type=basic\n"+
        "#imq.authentication.basic.user_repository=ldap\n"+
        "#imq.user_repository.ldap.server=host:port\n"+
        "#imq.user_repository.ldap.principal=\n"+
        "#imq.user_repository.ldap.base=ou=People, dc=sun,dc=com\n"+
        "#imq.user_repository.ldap.uidattr=uid\n"+
        "#imq.user_repository.ldap.usrfilter=\n"+
        "#imq.user_repository.ldap.grpsearch=false\n"+
        "#imq.user_repository.ldap.grpbase=ou=Groups, dc=sun,dc=com\n"+
        "#imq.user_repository.ldap.gidattr=cn\n"+
        "#imq.user_repository.ldap.memattr=uniquemember\n"+
        "#imq.user_repository.ldap.grpfilter=\n"+
        "#imq.user_repository.ldap.ssl.enabled=false\n"+
        "#imq.user_repository.ldap.timeout=180\n"+
        "#\n"+
        "##############################################################\n"+
        "#\n"+
        "#\n"+
        "#Last Update:";
}
