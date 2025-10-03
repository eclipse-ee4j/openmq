/*
 * Copyright (c) 2000, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package com.sun.messaging.jmq.jmsserver.config;

import java.util.*;
import java.net.*;
import java.io.*;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;

/**
 * this is the implementation of ConfigStore used to store configuration properties in java property files on the local
 * system.
 *
 * @see ConfigStore
 * @see BrokerConfig
 */

public class FileConfigStore implements ConfigStore {

    // private static boolean DEBUG = false;

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
     * @param currentprops already loaded properties (including system, default and install properties)
     * @param instancename the name used by the broker, passed in at startup
     *
     * @return a properties object with the correct instance properties
     *
     * @throws BrokerException if a fatal error occurs loading the config store
     */

    @Override
    public Properties loadStoredProps(Properties currentprops, String instancename) throws BrokerException {
        // now load the stored properties seperately (for storing later)
        //

        /**
         * full path to the editable JMQ configuration location.
         */
        String vardirectory = CommGlobals.getJMQ_INSTANCES_HOME() + File.separator + instancename + File.separator + "props" + File.separator;
        storedprop_loc = vardirectory + "config.properties";
        Properties storedprops = new Properties();
        try (FileInputStream cfile = new FileInputStream(storedprop_loc)) {
            try (BufferedInputStream bfile = new BufferedInputStream(cfile)) {
                storedprops.load(bfile);
            }
        } catch (IOException ex) {

            File f = new File(storedprop_loc);
            if (f.exists()) {
                logger.logStack(Logger.WARNING, BrokerResources.W_BAD_PROPERTY_FILE, instancename, storedprop_loc, ex);
            } else {
                logger.log(Logger.INFO, BrokerResources.I_NEW_PROP, instancename);
            }

            // make the directory (if necessary)
            File dir = new File(vardirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            if (!f.exists()) {
                try {
                    // Create a new config.properties. Make sure version
                    // property is set.
                    storedprops.setProperty(BrokerConfig.INSTANCECONFIG_VERSION_PROP, BrokerConfig.CONFIG_VERSION);
                    storeProperties(storedprops);
                } catch (IOException ex1) {
                    logger.logStack(Logger.WARNING, BrokerResources.X_CANNOT_CREATE_PROP_FILE, f.toString(), ex);
                }
            }
        }

        // Check version
        String version = storedprops.getProperty(BrokerConfig.INSTANCECONFIG_VERSION_PROP);
        if (version != null && !version.equals("") && !version.equals(BrokerConfig.CONFIG_VERSION)) {
            Object args[] = { storedprop_loc, BrokerConfig.CONFIG_VERSION, version };
            logger.log(Logger.WARNING, BrokerResources.W_BAD_CONFIG_VERSION, args);
        }

        return storedprops;
    }

    /**
     * loads the cluster properties
     *
     * @param currentprops already loaded properties (including system, default and install properties)
     * @param parameters properties passed in on the command line
     * @param instanceprops properties returned from the loadStoredProps method
     *
     * @return a properties object with the correct cluster properties (or null if there aren't any)
     *
     * @throws BrokerException if a fatal error occurs loading the config store
     */

    @Override
    public Properties loadClusterProps(Properties currentprops, Properties parameters, Properties instanceprops) throws BrokerException {
        String cluster_url_str = parameters.getProperty(cluster_prop, instanceprops.getProperty(cluster_prop, currentprops.getProperty(cluster_prop)));

        return loadClusterProps(cluster_url_str);
    }

    @SuppressWarnings({
        "deprecation" // URL(java.lang.String) in java.net.URL has been deprecated
    })
    private Properties loadClusterProps(String cluster_url_str) {
        if (cluster_url_str == null) {
            return null;
        }
        Properties storedprops = new Properties();

        try {
            URL curl = new URL(cluster_url_str);
            try (InputStream cu_is = curl.openStream()) {
                try (BufferedInputStream bfile = new BufferedInputStream(cu_is)) {
                    storedprops.load(bfile);
                }
            }
        } catch (IOException ex) {
            logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY_FILE, "cluster", cluster_url_str, ex);
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

    @Override
    public void storeProperties(Properties props) throws IOException {
        try {
            try (FileOutputStream cfile = new FileOutputStream(storedprop_loc)) {
                try (BufferedOutputStream bos = new BufferedOutputStream(cfile)) {
                    props.store(bos, PROP_HEADER_STR);
                }
            }
        } catch (IOException ex) {
            logger.logStack(Logger.ERROR, BrokerResources.E_PROPERTY_WRITE, storedprop_loc, ex);

            // re-throw the exception
            throw ex;
        }
    }

    /**
     * Reload the specified properties from the store.
     *
     * @param instancename the name used by the broker, passed in at startup
     *
     * @param propnames Array containing names of the properties to be reloaded.
     */
    @Override
    public Properties reloadProps(String instancename, String[] propnames) throws BrokerException {
        Properties props = loadStoredProps(null, instancename);
        if (props == null) {
            return null;
        }

        Properties ret = new Properties();
        for (int i = 0; i < propnames.length; i++) {
            String value = props.getProperty(propnames[i]);
            if (value != null) {
                ret.setProperty(propnames[i], value);
            }
        }

        /*
         * String cluster_url_str = props.getProperty(cluster_prop, null); props = loadClusterProps(cluster_url_str); if (props
         * == null) return ret;
         *
         * for (int i = 0; i < propnames.length; i++) { String value = props.getProperty(propnames[i]); if (value != null)
         * ret.setProperty(propnames[i], value); }
         */

        props = null;
        return ret;
    }

    @Override
    public void clearProps(String instancename) {
        String vardirectory = CommGlobals.getJMQ_INSTANCES_HOME() + File.separator + instancename + File.separator + "props" + File.separator;
        storedprop_loc = vardirectory + "config.properties";
        File f = new File(storedprop_loc);
        try {
            f.delete();
        } catch (Exception ex) {
            logger.logStack(Logger.WARNING, BrokerResources.X_CANNOT_CREATE_PROP_FILE, f.toString(), ex);
        }
    }
//-----------------------------------------------------------------------
// HEADER
//-----------------------------------------------------------------------

    /**
     * string placed at the top of the instance property file
     */
    static final String PROP_HEADER_STR = """
        #
        # Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
        #
        # This program and the accompanying materials are made available under the
        # terms of the Eclipse Public License v. 2.0, which is available at
        # http://www.eclipse.org/legal/epl-2.0.
        #
        # This Source Code may also be made available under the following Secondary
        # Licenses when the conditions for such availability set forth in the
        # Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
        # version 2 with the GNU Classpath Exception, which is available at
        # https://www.gnu.org/software/classpath/license.html.
        #
        # SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
        #/
        #############################################################################
        #
        # File: config.properties\s
        #
        #
        # This file contains the instance specific properties for
        # a running broker.
        #
        # Only some of the properties listed in the default.properties
        # file should be changed (some of the properties are for\s
        # field support or future features).
        #
        # WARNING:\s
        #
        #   Use care when editing this file by hand.
        #
        #   This file is automatically update.  Changes made while
        #   the broker is running MAY be lost. Any comments added to
        #   this file may be lost.
        #
        #   Only the properties listed below can be modified in a\s
        #   supported configuration.
        #
        #   Please make any property changes at the bottom of this file.
        #
        ###################################################################
        #
        #  Supported properties are:
        #
        # Connection Services Settings
        # ----------------------------
        #
        #    General Connection Services
        #
        #            imq.service.activelist
        #            imq.shared.connectionMonitor_limit
        #            imq.hostname
        #
        #    Connection Service Specific Settings
        #
        #            imq.<service_name>.threadpool_model
        #            imq.<service_name>.<protocol_name>.port
        #            imq.<service_name>.<protocol_name>.hostname
        #            imq.<service_name>.min_threads
        #            imq.<service_name>.max_threads
        #
        #       configuration for the keystore used by the ssl service
        #
        #            imq.keystore.file.dirpath
        #            imq.keystore.file.name
        #            imq.passfile.enabled
        #            imq.passfile.dirpath
        #            imq.passfile.name
        #           \s
        #       http specific parameters
        #
        #            imq.httpjms.http.servletHost
        #            imq.httpjms.http.servletPort
        #            imq.httpjms.http.pullPeriod
        #            imq.httpjms.http.connectionTimeout
        #           \s
        #       https specific parameters
        #
        #            imq.httpsjms.https.servletHost
        #            imq.httpsjms.https.servletPort
        #            imq.httpsjms.https.pullPeriod
        #            imq.httpsjms.https.connectionTimeout
        #
        #    General JMX parameters
        #
        #            imq.jmx.hostname
        #
        #    General JMX Connector Services
        #
        #            imq.jmx.connector.activelist
        #
        #    JMX Connector specific settings
        #
        #            imq.jmx.connector.<connector server name>.urlpath
        #            imq.jmx.connector.<connector server name>.useSSL
        #            imq.jmx.connector.<connector server name>.brokerHostTrusted
        #
        #    RMI Registry (used by RMI JMX Connectors)
        #
        #            imq.jmx.rmiregistry.use
        #            imq.jmx.rmiregistry.start
        #            imq.jmx.rmiregistry.port
        #
        #    Portmapper Settings
        #
        #            imq.portmapper.hostname
        #            imq.portmapper.port
        #            imq.portmapper.backlog
        #
        # Message Router Settings
        # -----------------------
        #
        #    Memory reclamation period
        #
        #            imq.message.expiration.interval
        #
        #    Message limits: broker
        #
        #            imq.system.max_count
        #            imq.system.max_size
        #
        #    Individual message limits
        #
        #            imq.message.max_size
        #
        # Persistence Settings
        # --------------------
        #
        #    Type of data store
        #
        #            imq.persist.store
        #
        #    File-based store
        #
        #            imq.persist.file.message.max_record_size
        #            imq.persist.file.destination.message.filepool.limit
        #            imq.persist.file.message.filepool.cleanratio
        #            imq.persist.file.message.cleanup
        #            imq.persist.file.sync.enabled
        #
        #    JDBC-based store
        #
        #            imq.brokerid
        #            imq.persist.jdbc.dbVendor
        #            imq.persist.jdbc.<dbVendor>.driver
        #            imq.persist.jdbc.<dbVendor>.opendburl
        #            imq.persist.jdbc.<dbVendor>.createdburl
        #            imq.persist.jdbc.<dbVendor>.closedburl
        #            imq.persist.jdbc.<dbVendor>.user
        #            imq.persist.jdbc.<dbVendor>.needpassword
        #            imq.persist.jdbc.<dbVendor>.table.MQVER41
        #            imq.persist.jdbc.<dbVendor>.table.MQCREC41
        #            imq.persist.jdbc.<dbVendor>.table.MQBKR41
        #            imq.persist.jdbc.<dbVendor>.table.MQSES41
        #            imq.persist.jdbc.<dbVendor>.table.MQDST41
        #            imq.persist.jdbc.<dbVendor>.table.MQCON41
        #            imq.persist.jdbc.<dbVendor>.table.MQCONSTATE41
        #            imq.persist.jdbc.<dbVendor>.table.MQMSG41
        #            imq.persist.jdbc.<dbVendor>.table.MQPROP41
        #            imq.persist.jdbc.<dbVendor>.table.MQTXN41
        #
        #
        # Memory Management Settings
        # --------------------------
        #
        #            imq.<resource_state>.threshold
        #            imq.<resource_state>.count
        #
        #
        # Security Settings
        # -----------------
        #
        #    Authentication
        #
        #            imq.authentication.type
        #            imq.<service_name>.authentication.type
        #            imq.authentication.basic.user_repository
        #            imq.authentication.client.response.timeout
        #
        #    User Repository
        #
        #            imq.user_repository.ldap.server
        #            imq.user_repository.ldap.principal
        #            imq.user_repository.ldap.base
        #            imq.user_repository.ldap.uidattr
        #            imq.user_repository.ldap.usrformat
        #            imq.user_repository.ldap.usrfilter
        #            imq.user_repository.ldap.grpsearch
        #            imq.user_repository.ldap.grpbase
        #            imq.user_repository.ldap.gidattr
        #            imq.user_repository.ldap.memattr
        #            imq.user_repository.ldap.grpfilter
        #            imq.user_repository.ldap.ssl.enabled
        #            imq.user_repository.ldap.timeout
        #
        #            imq.user_repository.jaas.name
        #            imq.user_repository.jaas.userPrincipalClass
        #            imq.user_repository.jaas.groupPrincipalClass
        #
        #            imq.user_repository.file.dirpath
        #            imq.user_repository.file.filename
        #
        #    Access Control
        #            imq.accesscontrol.enabled
        #            imq.<service_name>.accesscontrol.enabled
        #            imq.accesscontrol.file.filename
        #            imq.accesscontrol.file.dirpath
        #            imq.<service_name>.accesscontrol.file.filename
        #            imq.<service_name>.accesscontrol.file.dirpath
        #            imq.accesscontrol.file.url
        #            imq.<service_name>.accesscontrol.file.url
        #
        # Log Settings
        # ------------
        #
        #    Log Level
        #            imq.log.level
        #            imq.log.timezone
        #
        #    Output Channels
        #        File:
        #            imq.log.file.rolloverbytes
        #            imq.log.file.rolloversecs
        #            imq.log.file.dirpath
        #            imq.log.file.filename
        #            imq.log.file.output
        #        Console:
        #            imq.log.console.stream
        #            imq.log.console.output
        #        Solaris syslog:
        #            imq.log.syslog.facility
        #            imq.log.syslog.logpid
        #            imq.log.syslog.logconsole
        #            imq.log.syslog.identity
        #            imq.log.syslog.output
        #
        #    Metrics settings
        #            imq.metrics.enabled
        #            imq.metrics.interval
        #            imq.metrics.topic.enabled
        #            imq.metrics.topic.interval
        #            imq.metrics.topic.persist
        #            imq.metrics.topic.timetolive
        #
        # Destination Management Settings
        # -------------------------------
        #
        #            imq.autocreate.topic
        #            imq.autocreate.topic.consumerFlowLimit
        #            imq.autocreate.queue
        #            imq.autocreate.reaptime
        #            imq.autocreate.queue.consumerFlowLimit
        #            imq.autocreate.queue.maxNumActiveConsumers
        #            imq.autocreate.queue.maxNumBackupConsumers
        #            imq.autocreate.queue.localDeliveryPreferred
        #            imq.autocreate.destination.isLocalOnly
        #            imq.autocreate.destination.maxNumMsgs
        #            imq.autocreate.destination.maxTotalMsgBytes
        #            imq.autocreate.destination.maxNumProducers
        #            imq.autocreate.destination.maxBytesPerMsg
        #            imq.autocreate.destination.limitBehavior
        #            imq.autocreate.destination.useDMQ
        #
        #            imq.destination.DMQ.truncateBody
        #            imq.destination.logDeadMsgs
        #
        #
        # Transaction Settings
        # --------------------
        #
        #            imq.transaction.autorollback
        #            imq.transaction.detachedTimeout
        #            imq.transaction.producer.maxNumMsgs
        #            imq.transaction.consumer.maxNumMsgs
        #
        #
        # Cluster Management Settings
        # ---------------------------
        #
        #    Cluster file location
        #
        #            imq.cluster.url
        #
        #    HA Cluster:
        #
        #        HA Cluster per broker settings
        #
        #            imq.brokerid
        #
        #        HA Cluster Configuration Setting\s
        #
        #        NOTE: Under normal circumstances, setting for these
        #              properties should be made in the cluster.url file
        #              where then can be accessed by all brokers in the
        #              cluster, not in this file
        #
        #            imq.cluster.ha
        #            imq.cluster.clusterid
        #            imq.persist.store
        #            imq.persist.jdbc.dbVendor
        #
        #    Non-HA Cluster:
        #
        #        Cluster per broker settings
        #
        #            imq.cluster.hostname
        #            imq.cluster.port
        #
        #        Cluster Configuration Setting\s
        #
        #        NOTE: Under normal circumstances, setting for these
        #              properties should be made in the cluster.url file
        #              where then can be accessed by all brokers in the
        #              cluster, not in this file
        #
        #            imq.cluster.brokerlist
        #            imq.cluster.masterbroker
        #            imq.cluster.transport
        #
        #
        # Miscellaneous Settings
        # ----------------------
        #
        #            imq.ping.interval
        #
        #
        # Bridge Service Manager Settings
        # -------------------------------
        #
        #            imq.bridge.enabled
        #            imq.bridge.activelist
        #            imq.bridge.admin.user
        #
        #
        # Please see the documentation for specifics on how to set these
        # properties.
        #
        ##############################################################
        #
        # To plug in a Sun HADB database, either uncomment or
        # set 'dbVendor' property to 'hadb', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in a Sun HADB database
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=hadb
        # Replace 'server list' with your comma-separated list of servers.
        #imq.persist.jdbc.hadb.property.serverList=<server list>
        # Replace username.
        #imq.persist.jdbc.hadb.user=<username>
        #imq.persist.jdbc.hadb.needpassword=[true|false]
        #
        # End of properties to plug in a Sun HADB database
        ##############################################################
        #
        # To plug in a MySQL database, either uncomment or
        # set 'dbVendor' property to 'mysql', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in a MySQL database
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=mysql
        # Replace hostname, port and database in imq.persist.jdbc.mysql.property.url.
        #imq.persist.jdbc.mysql.property.url=jdbc:mysql://<hostname>:<port>/<database>
        # Replace username.
        #imq.persist.jdbc.mysql.user=<username>
        #imq.persist.jdbc.mysql.needpassword=[true|false]
        #
        # End of properties to plug in a MySQL database
        ##############################################################
        #
        # To plug in a DB2 database, either uncomment or
        # set 'dbVendor' property to 'db2', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in a DB2 database
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=db2
        # Replace hostname, port and database in imq.persist.jdbc.db2.opendburl.
        #imq.persist.jdbc.db2.opendburl=jdbc:db2://<hostname>:<port>/<database>
        # Replace username.
        #imq.persist.jdbc.db2.user=<username>
        #imq.persist.jdbc.db2.needpassword=[true|false]
        #
        # End of properties to plug in a DB2 database
        ##############################################################
        #
        # To plug in an Oracle database, either uncomment or
        # set 'dbVendor' property to 'oracle', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in an Oracle database
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=oracle
        # Replace hostname, port and sid in imq.persist.jdbc.oracle.property.url.
        #imq.persist.jdbc.oracle.property.url=jdbc:oracle:thin:@<hostname>:<port>:<sid>
        # Replace username.
        #imq.persist.jdbc.oracle.user=<username>
        #imq.persist.jdbc.oracle.needpassword=[true|false]
        #
        # End of properties to plug in an Oracle database
        ##############################################################
        #
        # To plug in a Java DB (Derby) embedded database, either uncomment or
        # set 'dbVendor' property to 'derby', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in a Java DB (Derby) embedded database
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=derby
        #imq.persist.jdbc.derby.createdburl=jdbc:derby:${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb;create=true
        #imq.persist.jdbc.derby.opendburl=jdbc:derby:${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb
        #imq.persist.jdbc.derby.closedburl=jdbc:derby:;shutdown=true
        # Replace username.
        #imq.persist.jdbc.derby.user=<username>
        #imq.persist.jdbc.derby.needpassword=[true|false]
        #
        # End of properties to plug in a Java DB (Derby) embedded database
        ##############################################################
        #
        # To plug in a Java DB (Derby) Network Server, either uncomment or
        # set 'dbVendor' property to 'derby', and edit the\s
        # values according to your database configuration.
        # Then, finish the steps outlined in the Administrative Guide
        # to plug in and set up the database store.
        #
        ##############################################################
        # Beginning of properties to plug in a Java DB (Derby) Network Server
        #
        # Replace 'alphanumeric id' with your broker identifier
        #imq.brokerid=<alphanumeric id>
        #imq.persist.store=jdbc
        #imq.persist.jdbc.dbVendor=derby
        #imq.persist.jdbc.derby.createdburl=
        #imq.persist.jdbc.derby.opendburl=jdbc:derby://<hostname>:<port>/${imq.instanceshome}${/}${imq.instancename}${/}dbstore${/}imqdb
        #imq.persist.jdbc.derby.closedburl=
        # Replace username.
        #imq.persist.jdbc.derby.user=<username>
        #imq.persist.jdbc.derby.needpassword=[true|false]
        #imq.persist.jdbc.derby.driver=org.apache.derby.jdbc.ClientDriver
        #
        # End of properties to plug in a Java DB (Derby) Network Server
        ##############################################################
        
        
        ##############################################################
        #
        # An example of using Directory Server 5.2 as
        # the user repository
        #
        ##############################################################
        #
        #imq.authentication.type=basic
        #imq.authentication.basic.user_repository=ldap
        #imq.user_repository.ldap.server=host:port
        #imq.user_repository.ldap.principal=
        #imq.user_repository.ldap.base=ou=People, dc=sun,dc=com
        #imq.user_repository.ldap.uidattr=uid
        #imq.user_repository.ldap.usrfilter=
        #imq.user_repository.ldap.grpsearch=false
        #imq.user_repository.ldap.grpbase=ou=Groups, dc=sun,dc=com
        #imq.user_repository.ldap.gidattr=cn
        #imq.user_repository.ldap.memattr=uniquemember
        #imq.user_repository.ldap.grpfilter=
        #imq.user_repository.ldap.ssl.enabled=false
        #imq.user_repository.ldap.timeout=180
        #
        ##############################################################
        #
        #
        #Last Update:""";
}
