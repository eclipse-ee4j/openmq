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
 * @(#)BrokerConfig.java	1.103 09/05/07
 */ 

package com.sun.messaging.jmq.jmsserver.config;

import java.util.*;
import java.net.*;
import java.io.*;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.comm.CommGlobals;

//##########################################################################
//##########################################################################
//#                                                                        #
//#                      Public Class BrokerConfig                         #
//#                                                                        #
//##########################################################################
//##########################################################################


/**
 * This is a singleton class which contains the configuration information for
 * the broker<P>
 *
 * Configuration information can be obtained from several different
 * sources: <BR>
 * <UL>
 *    <LI> cluster (or multi-broker) configuration </LI>
 *    <LI> default (as shipped) configuration </LI>
 *    <LI> installation configuration (default properties for any
 *                    broker which runs on that installation regardless of
 *                    the host or port) </LI>
 *    <LI> instance specific properties (properties for
 *                    a specific running instance of the broker) </LI>
 * </UL>
 *  <P>
 *  The default and installation properties will ALWAYS be retrieved from
 *  property files which are located in the installation.
 *  Cluster and instance properties will, by default , be retrieve from
 *  files but may also be retrieve from another method (e.g. jndi).
 *
 * <P>
 *  This class is a subclass of UpdateProperties which is a basic
 *  class which handles maintaining a list of changed properties
 *  and calling any registered listeners which a properties is
 *  changed. <P>
 *
 *  The code to handle loading cluster and instance specific properties
 *  is handled by a seperate class which implements ConfigStore.
 *  This allows different mechanisms to be plugged in to load those
 *  properties.  The class used is determined by the property
 *  value for "imq.config.class". <P>
 *
 * <BOLD> Property Syntax</BOLD><P>
 *      All iMQ properties will be of the form: <BR>
 * <BlockQuote>
               imq.<area>[.instance].property=value[,value1]
 * </Blockquote>
 *      Where:<BR>
 * <BlockQuote>
 * <UL>
 *     <LI>    <I>area</I> is the general area the property controls
 *                 e.g. protocol </LI>
 *     <LI>    <I>instance</I> is used to define where the property is
 *                 used (for something like thread pool size which may be
 *                 used in several areas). This should heirarchically
 *                 define where the property is used</LI>
 *     <LI>    <I>property</I> is the string name of the configuration property</LI>
 *     <LI>    <I>value</I> is the value of the property (which may be an
 *                 , seperated list of entries)</LI>
 * </UL>
 *
 * </Blockquote>
 *       e.g. <BR>
 * <Blockquote>
 *             imq.protocol.list=tcp,http
 *             imq.protocol.tcp.list=normal,admin
 *             imq.protocol.http.list=normal
 *             imq.protocol.tcp.normal.threadpool.min=5
 *             imq.protocol.tcp.normal.threadpool.max=10
 *             imq.protocol.tcp.normal.port=2682
 *             imq.protocol.tcp.admin.threadpool.min=1
 *             imq.protocol.tcp.admin.threadpool.max=2
 *             imq.protocol.tcp.admin.port=555
 *             imq.protocol.http.normal.threadpool.min=15
 *             imq.protocol.http.normal.threadpool.max=20
 *             imq.protocol.http.normal.port=8080
 *
 * </BlockQuote>
 * <P>
 * Classes which use this configuration object need to be 
 * careful to ONLY use this at object creation or when an
 * update has occurred (because it has a high overhead)
 * <P>
 *
 * @see java.util.Properties
 * @see com.sun.messaging.jmq.jmsserver.config.ConfigListener
 * @see com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException
 * @see com.sun.messaging.jmq.jmsserver.config.WatchedProperty
 * @see "http://jpgserv.eng/JMQ20/engineering/details/broker/b_config.txt"
 */

/* XXX - Linda - 7/18/00 REVISIT
 *     TO DO:
 *     * add code to handle boolean flag setting
 *     * look at adding support for having a "template" properties (optional)
 *     * look at wild card support (optional)
 */
/*
 *######################################################################3
 *  PROPERTY DESCRIPTIONS
 *######################################################################3
 *
 * PLEASE ADD ANY NEW PROPERTIES TO THIS LIST IN FUTURE RELEASES
 *
 * Properties are broker into two categories:
 *    Public/Private - properties mentioned in the defaults.properties file
 *    Secret properties - properties which are only found here
 *
 * All normal properties should be listed here (if possible) to
 * provide a single point for engineers to look for all properties
 * (whether public/private or secret)
 *
 * 
 * NOTE: 
 *    if no properties are found (this is the only property file)
 *
 *   	 * the list of active Services will be -> jms and admin
 *       * the persistent store used will be file
 *       * error/info will be logged to the console & ERROR/WARNING/INFO
 *           will be logged to the file
 */

/*
 PROPERTY INFORMATION

// PUBLIC PROPERTIES
// -------------------------------
//  

// Connection Services Settings


// General Connection Services

// List of active services, started at startup
imq.service.activelist=jms,admin,httpjms

imq.service.activelist=jms,admin //  only set minimal properties

// Connection Service Specific Settings


// Some properties settings below are described in other sections of this file.

// Information about thread pool settings:
// min is the minimum number of threads in the system, the number MUST be even.
// max is the maximum  of threads in the system, the number MUST be even.
// shared is the property to switch between the new weighted thread pool code
//   and the old thread pool code (which may not be available after beta).
//   Setting this property to true, turns on the weighted thread pool behavior.
//   On either setting, the system will act identically IF the number of
//   current connections < max threads

// jms connection service
imq.jms.protocoltype=tcp
imq.jms.servicetype=NORMAL
imq.jms.tcp.port=0
imq.jms.tcp.hostname=
imq.jms.tcp.backlog=100
imq.jms.tcp.blocking=true
imq.jms.tcp.useChannels=false
imq.jms.min_threads=10
imq.jms.max_threads=1000
imq.jms.threadpool_model=dedicated

// jmsdirect connection service
imq.jmsdirect.servicetype=NORMAL
imq.jmsdirect.handler_name=jmsdirect

// MQ direct connection service
imq.mqdirect.servicetype=NORMAL
imq.mqdirect.min_threads=10
imq.mqdirect.max_threads=1000
imq.mqdirect.threadpool_model=dedicated

// MQ direct connection service (2)
imq.mqdirect2.servicetype=NORMAL

// ssljms connection service
// NOTE: ssljms is not active in the fallback case
imq.ssljms.protocoltype=tls
imq.ssljms.servicetype=NORMAL
imq.ssljms.tls.port=0
imq.ssljms.tls.hostname=
imq.ssljms.tls.backlog=100
imq.ssljms.tcp.blocking=true
imq.ssljms.tcp.useChannels=false
imq.ssljms.min_threads=10
imq.ssljms.max_threads=500
imq.ssljms.threadpool_model=dedicated

// configuration for the keystore used by the ssl service
imq.keystore.file.dirpath=${imq.etchome}
imq.keystore.file.name=keystore
imq.keystore.password=
imq.passfile.enabled=false
imq.passfile.dirpath=${imq.etchome}
imq.passfile.name=keypassfile

// admin connection service
imq.admin.protocoltype=tcp
imq.admin.servicetype=ADMIN
imq.admin.tcp.port=0
imq.admin.tcp.backlog=5
imq.admin.tcp.blocking=true
imq.admin.tcp.useChannels=false
imq.admin.min_threads=4
imq.admin.max_threads=10
imq.admin.threadpool_model=dedicated

// ssladmin connection service
// NOTE: ssladmin is not active in the fallback case
imq.ssladmin.protocoltype=tls
imq.ssladmin.servicetype=ADMIN
imq.ssladmin.tls.port=0
imq.ssladmin.tls.hostname=
imq.ssladmin.tls.backlog=5
imq.ssladmin.tcp.blocking=true
imq.ssladmin.tcp.useChannels=false
imq.ssladmin.min_threads=4
imq.ssladmin.max_threads=10
imq.ssladmin.threadpool_model=dedicated

// httpjms connection service

// NOTE: httpjms is not active in the fallback case

imq.httpjms.protocoltype=http
imq.httpjms.servicetype=NORMAL
imq.httpjms.http.servletHost=localhost
imq.httpjms.http.servletPort=7675
imq.httpjms.http.pullPeriod=-1
imq.httpjms.http.connectionTimeout=300
imq.httpjms.min_threads=10
imq.httpjms.max_threads=500
imq.httpjms.threadpool_model=dedicated

// httpsjms connection service (ssl communication with servlet)

imq.httpsjms.protocoltype=https
imq.httpsjms.servicetype=NORMAL
imq.httpsjms.https.servletHost=localhost
imq.httpsjms.https.servletPort=7674
imq.httpsjms.https.pullPeriod=-1
imq.httpjms.http.connectionTimeout=300
imq.httpsjms.min_threads=10
imq.httpsjms.max_threads=500

// Supported Protocols


// Buffer Size
// Set the per connection input and output buffer sizes in bytes.
// For the output buffer this basically affects per packet buffering, so
// having a value larger than your largest packet size may not improve
// performance much.
// For the input buffer this can affect buffering accross packets as
// well, depending on the situation.
// Set to 0 to turn off buffering.

// Only tcp is used in the true fallback case

imq.protocol.tcp.inbufsz=2048
imq.protocol.tcp.outbufsz=2048
imq.protocol.tls.inbufsz=2048
imq.protocol.tls.outbufsz=2048
imq.protocol.http.inbufsz=2048
imq.protocol.http.outbufsz=2048
imq.protocol.https.inbufsz=2048
imq.protocol.https.outbufsz=2048

// No Delay Flags
// this turns off the Nagle's algorithm for socket (turns on TCPNoDelay)
// Nagles algorithm speeds up performance on low band-width systems
// but lowers performance on most systems.

// In most configuration, no delay should be true
// (Nagle's algorithm should be off)

// This flag is only applicable on protocols running over tcp
// (this includes the tcp, and tls )
imq.protocol.tcp.nodelay=true
imq.protocol.tls.nodelay=true
imq.protocol.http.nodelay=true
imq.protocol.https.nodelay=true

// JMX Connector Settings
imq.jmx.usePlatformMBeanServer=true
imq.jmx.rmiregistry.start=false
imq.jmx.rmiregistry.use=false
imq.jmx.connector.list=jmxrmi,ssljmxrmi
imq.jmx.connector.activelist=jmxrmi
imq.jmx.connector.ssljmxrmi.useSSL=true

// Portmapper Settings


// Configuration for portmapper
imq.portmapper.port=7676
imq.portmapper.bind=true
imq.portmapper.backlog=50
imq.portmapper.sotimeout=500
// Linger on close in seconds, -1 will not set linger.
imq.portmapper.solinger=5


// Message Router Settings


// Memory reclamation period


// The message expiration timeout value determines how often (in
// seconds) the reaper thread will look at the current expiration 
// value for JMS messages
imq.message.expiration.interval=60

// Message limits: broker


// When these limits are reached, new messages received by broker are rejected
// until the system is below these limits

// Count limit (a value of -1 indicates no limit)
imq.system.max_count=-1

// Size limit (a value of -1 indicates no limit)
// To indicate a value other than bytes, an argument [m, k, b] can be added
//              1m-> 1meg
//              1k -> 1 Kbytes
//              1b -> 1 byte
imq.system.max_size=-1

// Individual message limits


// This sets a limit on the maximum size of ANY message body (in Kbytes)

// Size limit (a value of -1 indicates no limit)
// To indicate a value other than bytes, an argument [m, k, b] can be added
//              1m-> 1meg
//              1k -> 1 Kbytes
//              1b -> 1 byte
imq.message.max_size=-1



// Persistence Settings


// Type of data store


// Both file-based and JDBC-based persistence is currently supported. File-based
// is the default. (Set the value to jdbc for JDBC-based persistence.
imq.persist.store=file

// File-based store


// The percentage of files in the pool that are kept in a clean state 
// while the broker is running. Any files left in a dirty state must be 
// cleaned up at shutdown. A high value keeps the file pool in a clean 
// state which allows the broker to shutdown quickly but slows down the 
// performance of the file pool somewhat. A low value improves the speed 
// of the filepool, but may result in the broker taking longer to shutdown.
// Default: 60 percent
imq.persist.file.message.filepool.cleanratio=60

// Control whether the message store is 'cleaned up' when the broker exits.
// If the store is to be "cleaned" then the broker will truncate all
// message files to the appropriate size at exit. For files that contain
// message data this will be the size of the message data. For files that
// contain deleted messages this would be 0.
// This does not control whether the VR message file is compacted.
imq.persist.file.message.cleanup=true

// Initial size of VR message file for destinations.
imq.persist.file.message.vrfile.initial_size=1m

// block size of the VR message file
imq.persist.file.message.vrfile.block_size=256

// Maximum size of a message that will be stored in the VR message
// file. Messages that are bigger than this size will be stored in its
// own files
imq.persist.file.message.max_record_size=1m

// Maximum number of files in the file pool per destination used by the
// file based message store. The larger the pool the faster the broker
// can process large numbers of persistent messages at the expense of disk
// space.
// If the capacity of the file pool is exceeded, the broker will create
// and delete files as needed to process persistent messages.
// Default: 100 files
imq.persist.file.destination.message.filepool.limit=100


// JDBC-based store


// Vendor specific JDBC driver.
imq.persist.jdbc.driver=<jdbcdriver class>

// Vendor specific database url to get a database connection.
imq.persist.jdbc.opendburl=<url to open database>

// An identifier to make database table names unique per broker.
// If specified, this identifier will be appended to database tables
// names to make them unique in the case where more than one broker
// is persisting data in the same database
// The specified value should contain alphanumeric characters only.
// The length of the identifier should not exceed the maximum length
// of a table name allowed in the database minus 12.
// (12 is the length of table name reserved for iMQ's internal use.)
imq.persist.jdbc.brokerid=<alphanumeric id>

// Vendor specific url to create a database.
// This is an optional property. Should be specified if the database will
// need to be created using imqdbmgr.
imq.persist.jdbc.createdburl=<url to create database>

// Vendor specific database url to shutdown the connection.
// This is an optional property. Should be specified if the database needs
// to be shutdown explicitly. If specified,
// java.sql.DriverManager.getConnection() will be called with the specified
// url at broker shutdown.
imq.persist.jdbc.closedburl=<url to close database connection>

// User name used to open database connection.
// This is an optional property. The value can also be specified by command
// line opton for imqbroker and imqdbmgr.
imq.persist.jdbc.user=<username>

// Specify whether the broker should prompt the user for a password for
// database access.
// This is an optional property. It should be set to true if the database
// requires a password and the password is not provided by other means
imq.persist.jdbc.needpassword=[true|false]

// properties defining the database schema
imq.persist.jdbc.table.IMQSV35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQCCREC35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQDEST35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQINT35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQMSG35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQPROPS35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQILIST35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQTXN35=<CREATE TABLE SQL FOR VERSION TABLE>
imq.persist.jdbc.table.IMQTACK35=<CREATE TABLE SQL FOR VERSION TABLE>

// Security Settings


// Authentication Type


// You can specify two types of authentication: basic and digest.

// basic
imq.authentication.basic.user_repository=file

// digest (value of this property should not be changed from file)
imq.authentication.digest.user_repository=file

// You can specify authentication for all connection services or
// on a service-by-service basis. Individual service settings override 
// system settings.
imq.authentication.type=digest

// Authentication Timeout


// timeout in seconds in waiting for client hello and response to
// authentication request on a connection
imq.authentication.client.response.timeout=180

// User Repository


// You can define access to two types of user repositories: file-based and LDAP.

// File-based (file) user repository:
imq.user_repository.file.dirpath=${imq.etchome}
imq.user_repository.file.filename=passwd

// LDAP user repository (only supported for basic authentication type)
// The following properties are an example setup (please configure)


imq.user_repository.ldap.server=host:port
imq.user_repository.ldap.principal=
imq.user_repository.ldap.password=
imq.user_repository.ldap.base=ou=people, o=foobar.com
imq.user_repository.ldap.uidattr=uid
imq.user_repository.ldap.usrformat=
imq.user_repository.ldap.usrfilter=
imq.user_repository.ldap.grpsearch=false
imq.user_repository.ldap.grpbase=ou=groups, o=foobar.com
imq.user_repository.ldap.gidattr=cn
imq.user_repository.ldap.memattr=uniquemember
imq.user_repository.ldap.grpfilter=
imq.user_repository.ldap.ssl.enabled=false
imq.user_repository.ldap.ssl.socketfactory=com.sun.messaging.jmq.jmsserver.auth.ldap.TrustSSLSocketFactory

// (search in seconds)
imq.user_repository.ldap.timeout=180

// Access Control


// Enable authorization (access control)
// You can enable access control for all connection services or
// on a service-by-service basis. Individual service settings override 
// system settings.
imq.accesscontrol.enabled=true
imq.accesscontrol.type=file

// You can currently specify only file-based access control.

// File-based (file) access control
imq.accesscontrol.file.dirpath=${imq.etchome}
imq.accesscontrol.file.filename=accesscontrol.properties

// audit service configuration
imq.audit.enabled=false

// Logger Settings


// Log Level


// Only messages >= this level will get passed on to output channels 
(LogHandlers).
// The categories used in normal operation of the broker from highest 
// level to lowest are: ERROR, WARNING, INFO. You may specify NONE to 
// turn off logging.
.level=INFO

// Output Channels (LogHandlers)


// All Output Channels (LogHandlers) write out messages based on specific
// categories. Valid categories are ALL to select all messages, NONE
// to select no message or a comma (",") separated list of
// ERROR, WARNING, INFO.

// Specify supported and needed LogHandlers
// Supported Handles are java.util.logging.FileHandler, java.util.logging.ConsoleHandler,
 
// Old imq.log.handlers=file,console,destination,syslog,jmx
handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler, com.sun.messaging.jmq.jmsserver.service.DestinationLogHandler, com.sun.messaging.jmq.util.log.SysLogHandler, com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler
// FileLogHandler settings.
// The FileLogHandler logs messages to a set of rolling files.
// The rollover criteria can be the file size (bytes) and/or
// the file age (seconds). 0 means don't rollover based on that criteria.File age is currently not supported.
// java.util.logging.FileHandler.level specifies the default level for the Handler (defaults to Level.ALL). 
// java.util.logging.FileHandler.filter specifies the name of a Filter class to use (defaults to no Filter). 
// java.util.logging.FileHandler.formatter specifies the name of a Formatter class to use (defaults to java.util.logging.XMLFormatter) 
// java.util.logging.FileHandler.encoding the name of the character set encoding to use (defaults to the default platform encoding). 
// java.util.logging.FileHandler.limit specifies an approximate maximum amount to write (in bytes) to any one file. If this is zero, then there is no limit. (Defaults to no limit). 
// java.util.logging.FileHandler.count specifies how many output files to cycle through (defaults to 1). 
// java.util.logging.FileHandler.pattern specifies a pattern for generating the output file name. See below for details. (Defaults to "%h/java%u.log"). 
// java.util.logging.FileHandler.append specifies whether the FileHandler should append onto any existing files (defaults to false). 
java.util.logging.FileHandler.level=ALL
java.util.logging.FileHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter
java.util.logging.FileHandler.limit=268435456
java.util.logging.FileHandler.pattern=${imq.instanceshome}${/}${imq.instancename}${/}log${/}server.log

// Console settings.
// The console handler logs messages to an OutputStream. This can either be
// System.err (ERR) or System.out (OUT).
java.util.logging.ConsoleHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter
java.util.logging.ConsoleHandler.level=WARNING

// Syslog settings.
// The syslog handler logs messages to syslog on UNIX.
imq.log.syslog.facility=LOG_DAEMON
imq.log.syslog.logpid=true
imq.log.syslog.logconsole=false
imq.log.syslog.identity=imqbrokerd_${imq.instancename}
imq.log.syslog.output=ERROR

// Destination log handler settings.
// The destination handler logs messages to a topic (mq.log.broker)
imq.log.destination.output=ERROR|WARNING
imq.log.destination.timetolive=300
imq.log.destination.persist=false

// JMX log handler settings
// The jmx log handler exposes log messages as JMX notifications
imq.log.jmx.output=ALL

// Metrics settings


// These settings enable metric counting in the broker and set the 
// time interval (in seconds)
// at which the broker will generate a metrics report. 0 means no report.
imq.metrics.enabled=true
imq.metrics.interval=0

// Properties for configuring the behaviout of metrics obtained
// via the JMS monitoring API
imq.metrics.topic.enabled=true
imq.metrics.topic.interval=60
imq.metrics.topic.persist=false
imq.metrics.topic.timetolive=300



// Destination Management Settings


// autocreate for topics should always be true
imq.autocreate.topic=true

// autocreate for queues should default to false after EA
imq.autocreate.queue=true

//max active/failover counts for autocreated queues
imq.autocreate.queue.maxNumActiveConsumers=-1
imq.autocreate.queue.maxNumBackupConsumers=0

// default queue delivery policy (for auto-create queues)
// Choices are:
//      single - standard single queue receiver queues
//      failover - failover
//      round-robin
imq.queue.deliverypolicy=single

// defines the number of messages queued at a time to a receiver
// on a round robin queue
imq.queue.rr.messageblock=5


// Miscellaneous Settings


// Exit code Broker uses when it is exiting due to a restart
imq.restart.code=255


// SECRET PROPERTIES (WILL BE REMOVED BY FCS)


// list of supported protocols
imq.protocol.list=tcp,tls,http,https

// List of available system services (may be needed by admin)
imq.service.list=jms,admin,ssljms,httpjms,httpsjms,ssladmin,mqdirect,mqdirect2

// authentication classes/properties
imq.authentication.basic.properties=class,user_repository
imq.authentication.basic.class=com.sun.messaging.jmq.jmsserver.auth.JMQBasicAuthenticationHandler
imq.authentication.digest.properties=class,user_repository
imq.authentication.digest.class=com.sun.messaging.jmq.jmsserver.auth.JMQDigestAuthenticationHandler

// user_repository classes/properties
imq.user_repository.file.properties=class,filename,userPrincipalClass,groupPrincipalClass,dirpath
imq.user_repository.file.class=com.sun.messaging.jmq.jmsserver.auth.file.JMQFileUserRepository
imq.user_repository.ldap.properties=class,server,principal,password,base,uidattr,usrformat,usrfilter,grpsearch,grpbase,gidattr,memattr,grpfilter,timeout,ssl.enabled,ssl.socketfactory,userPrincipalClass, groupPrincipalClass
imq.user_repository.ldap.class=com.sun.messaging.jmq.jmsserver.auth.ldap.LdapUserRepository

// accesscontrol classes/properties
imq.accesscontrol.file.properties=class,filename,dirpath
imq.accesscontrol.file.class=com.sun.messaging.jmq.jmsserver.auth.acl.JMQFileAccessControlModel

imq.accesscontrol.jaas.properties=class,permissionFactory,permissionFactoryPrivate,policyProvider
imq.accesscontrol.jaas.class=com.sun.messaging.jmq.jmsserver.auth.acl.JAASAccessControlModel

// logging classes
imq.log.file.class=com.sun.messaging.jmq.util.log.FileLogHandler
imq.log.console.class=com.sun.messaging.jmq.util.log.StreamLogHandler
imq.log.syslog.class=com.sun.messaging.jmq.util.log.SysLogHandler
imq.log.destination.class=com.sun.messaging.jmq.jmsserver.service.DestinationLogHandler
imq.log.jmx.class=com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler

// Class/properties of supported protocols

imq.protocol.tcp.propertylist=port,backlog,useChannels,blocking
imq.protocol.tcp.class=com.sun.messaging.jmq.jmsserver.net.tcp.TcpProtocol

imq.protocol.tls.propertylist=port,backlog,keystore,,useChannels,blocking
imq.protocol.tls.class=com.sun.messaging.jmq.jmsserver.net.tls.TLSProtocol

imq.protocol.http.propertylist=servletHost,servletPort,pullPeriod,connectionTimeout,useChannels,blocking
imq.protocol.http.class=com.sun.messaging.jmq.jmsserver.net.http.HTTPProtocol

// not exposed yet; we'll always trust the servlet host for now
jms.httpsjms.https.isHostTrusted=true
imq.protocol.https.propertylist=servletHost,servletPort,pullPeriod,connectionTimeout,sHostTrusted
imq.protocol.https.class=com.sun.messaging.jmq.jmsserver.net.https.HttpsProtocol

// Class for creating standard services
imq.service_handler.dedicated.class=com.sun.messaging.jmq.jmsserver.service.imq.dedicated.DedicatedServiceFactory
imq.service_handler.shared.class=com.sun.messaging.jmq.jmsserver.service.imq.assigned.AssignedServiceFactory
imq.service_handler.group.class=com.sun.messaging.jmq.jmsserver.service.imq.group.GroupServiceFactory
imq.service_handler.direct.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQDirectServiceFactory 
imq.service_handler.mqdirect.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQEmbeddedServiceFactory 
imq.service_handler.mqdirect2.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQDualThreadServiceFactory 

// Debug properties

// Format is: imq.debug.<class name to control debugging on>=true|false
// Note: To see debug messages the log level must be set to
//       DEBUG, DEBUGMED or DEBUGHIGH
imq.debug.com.sun.messaging.jmq.jmsserver.multibroker.RouteTable=false
imq.debug.com.sun.messaging.jmq.jmsserver.persist.api.Store=false

// packet tracking code (turns on additional information on packets through
// the system)

imq.dump.packet.debug=false
imq.dump.packet.debugall=false

// Reference Tracking:

// track information on all packet references and dump the information when
// the packet is destroyed (very memory/time intensive)
imq.debug.packet.pktdebug_all

// track information on all packet references and dump the information when
// an exception is received (very memory/time intensive)
imq.debug.packet.pktdebug

// PERSISTENCE

// control whether to sync all persistent operations, including those that
// truncate a file or tag a file 'FREE'
imq.persist.file.sync.all=false

// the class instantiated for a specific type of store
// imq.persist.<type>.class


// SERVICES

// A service controls the connections between clients and how
// those clients are processed (threading model)

// In the 2.0 release, only one type of service (standard) which
// has a protocol and two threadpools (input and output) is supported.

// However, the infrastructure is designed to allow other models to
// be plugged into the system.

// The service has several pieces:
//      Service -> the actual service class, handles accepting "connections"
//                from a client and creating Connection objects
//     Connection-> an abstract class which represents a JMS connection
//     ServiceHandler -> a class which controls monitoring, creating
//                      and updating properties on a service
//     ServiceManager -> the class that creates services

// the creating and access of a service is controled through properties

// the class instanciated for a specific service handler

// imq.service_handler.<handlername>.class 

// the handler used for a specific service

// imq.<service name>.service_handler 

// the list of all available services in the system (may be used
// by admin in the future

imq.service.list=jms,admin,httpjms,ssljms,httpsjms,ssladmin

// the list of currently "active" services
imq.service.activelist=jms,admin

// type of service (NORMAL or ADMIN)
// imq.<service>.servicetype

// imq.service.jms.servicetype=NORMAL
// imq.service.ssljms.servicetype=NORMAL
// imq.service.admin.servicetype=ADMIN
// imq.service.httpjms.servicetype=NORMAL
// imq.service.httpsjms.servicetype=NORMAL
// imq.service.ssladmin.servicetype=ADMIN

// The following properties can be configured for a standard service

// imq.<instancename>.threadpool.is_shared
// imq.<instancename>.threadpool.shared_percent
// imq.<instancename>.threadpool.priority
// imq.<instancename>.<protocolname>.<property>

// imq.protocol.<protocolname>.propertylist
// imq.protocol.<protocolname>.class
// imq.<instancename>.protocoltype
// imq.<instancename>.min_threads
// imq.<instancename>.max_threads
// imq.<instancename>.<protocol>.nodelay
// imq.<instancename>.<protocol>.inbufsz


// MEMORY management properties

imq.memory.levels=green,yellow,orange,red
imq.memory.gcdelta=1024
imq.memory.hysteresis=1024
imq.memory.overhead=10240
imq.green.threshold=0
imq.green.count=50000
imq.green.gccount=0
imq.green.gcitr=0
imq.green.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Green
imq.yellow.seconds=5
imq.yellow.threshold=60
imq.yellow.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Yellow
imq.yellow.count=50
imq.yellow.gccount=1
imq.yellow.gcitr=1000
imq.yellow.seconds=2
imq.orange.threshold=70
imq.orange.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Orange
imq.orange.count=1
imq.orange.gccount=5
imq.orange.gcitr=100
imq.orange.seconds=1
imq.red.threshold=80
imq.red.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Red
imq.red.count=0
imq.red.gccount=10
imq.red.gcitr=5
imq.red.seconds=10

*/
public class BrokerConfig extends UpdateProperties
{
    private static final String IMQ = CommGlobals.IMQ;

    //#########################################################################
    //#                                                                       #
    //#                             Variables                                 #
    //#                                                                       #
    //#########################################################################

    // Version information for property files. 
    // imq.config.version is set in the default.properties file
    // imq.instanceconfig.version is set in the instance config.properties
    // when it is first created.
    public static final String CONFIG_VERSION_PROP
	                            = IMQ + ".config.version";
    public static final String INSTANCECONFIG_VERSION_PROP
                                    = IMQ + ".instanceconfig.version";
    public static final String CONFIG_VERSION = "300";


    //------------------------------------------------------------------------
    //--                       FALLBACK properties                          --
    //------------------------------------------------------------------------

    /**
     * this is the set of Fallback properties (properties to use if none of
     * the property files can be used). Fallback properties are now always
     * loaded (since secret properties may be set here)
     */

    private static final String JMQ_FallbackProperties = 
       IMQ + ".service.activelist=jms,admin\n" // only set minimal properties
        + IMQ + ".jms.protocoltype=tcp\n"
        + IMQ + ".jms.servicetype=NORMAL\n"
        + IMQ + ".jms.tcp.port=0\n"
        + IMQ + ".jms.tcp.backlog=100\n"
        + IMQ + ".jms.tcp.blocking=true\n"
        + IMQ + ".jms.tcp.useChannels=false\n"
        + IMQ + ".jms.min_threads=10\n"
        + IMQ + ".jms.max_threads=1000\n"
        + IMQ + ".jms.threadpool_model=dedicated\n"
        + IMQ + ".jmsdirect.servicetype=NORMAL\n"
        + IMQ + ".jmsdirect.handler_name=direct\n"
        + IMQ + ".mqdirect.servicetype=NORMAL\n"
        + IMQ + ".mqdirect.threadpool_model=dedicated\n"
        + IMQ + ".mqdirect.min_threads=10\n"
        + IMQ + ".mqdirect.max_threads=1000\n"
        + IMQ + ".mqdirect.handler_name=mqdirect\n"
        + IMQ + ".mqdirect2.servicetype=NORMAL\n"
        + IMQ + ".mqdirect2.handler_name=mqdirect2\n"
        + IMQ + ".wsjms.servicetype=NORMAL\n"
        + IMQ + ".wsjms.handler_name=websocket\n"
        + IMQ + ".wsjms.min_threads=10\n"
        + IMQ + ".wsjms.max_threads=1000\n"
        + IMQ + ".wsjms.protocoltype=ws\n"
        + IMQ + ".wsjms.ws.port=7670\n"
        + IMQ + ".wsjms.services=mqjms,mqstomp,mqjsonstomp\n"
        + IMQ + ".wssjms.servicetype=NORMAL\n"
        + IMQ + ".wssjms.handler_name=websocket\n"
        + IMQ + ".wssjms.min_threads=10\n"
        + IMQ + ".wssjms.max_threads=1000\n"
        + IMQ + ".wssjms.protocoltype=wss\n"
        + IMQ + ".wssjms.wss.port=7671\n"
        + IMQ + ".wssjms.services=mqjms,mqstomp,mqjsonstomp\n"
        + IMQ + ".ssljms.protocoltype=tls\n"
        + IMQ + ".ssljms.servicetype=NORMAL\n"
        + IMQ + ".ssljms.tls.port=0\n"
        + IMQ + ".ssljms.tls.backlog=100\n"
        + IMQ + ".ssljms.tcp.blocking=true\n"
        + IMQ + ".ssljms.tcp.useChannels=false\n"
        + IMQ + ".ssljms.min_threads=10\n"
        + IMQ + ".ssljms.max_threads=500\n"
        + IMQ + ".ssljms.threadpool_model=dedicated\n"
        + IMQ + ".keystore.file.dirpath=${imq.etchome}\n"
        + IMQ + ".keystore.file.name=keystore\n"
        + IMQ + ".keystore.password=\n"
        + IMQ + ".passfile.enabled=false\n"
        + IMQ + ".passfile.dirpath=${imq.etchome}\n"
        + IMQ + ".passfile.name=keypassfile\n"
        + IMQ + ".admin.protocoltype=tcp\n"
        + IMQ + ".admin.servicetype=ADMIN\n"
        + IMQ + ".admin.tcp.port=0\n"
        + IMQ + ".admin.tcp.backlog=5\n"
        + IMQ + ".admin.tcp.blocking=true\n"
        + IMQ + ".admin.tcp.useChannels=false\n"
        + IMQ + ".admin.min_threads=4\n"
        + IMQ + ".admin.max_threads=10\n"
        + IMQ + ".admin.threadpool_model=dedicated\n"
        + IMQ + ".httpjms.protocoltype=http\n"
        + IMQ + ".httpjms.servicetype=NORMAL\n"
        + IMQ + ".httpjms.http.servletHost=localhost\n"
        + IMQ + ".httpjms.http.servletPort=7675\n"
        + IMQ + ".httpjms.http.pullPeriod=-1\n"
        + IMQ + ".httpjms.http.connectionTimeout=300\n"
        + IMQ + ".httpjms.tcp.blocking=true\n"
        + IMQ + ".httpjms.tcp.useChannels=false\n"
        + IMQ + ".httpjms.threadpool_model=dedicated\n"
        + IMQ + ".httpjms.min_threads=10\n"
        + IMQ + ".httpjms.max_threads=500\n"
        + IMQ + ".httpsjms.protocoltype=https\n"
        + IMQ + ".httpsjms.servicetype=NORMAL\n"
        + IMQ + ".httpsjms.https.servletHost=localhost\n"
        + IMQ + ".httpsjms.https.servletPort=7674\n"
        + IMQ + ".httpsjms.https.pullPeriod=-1\n"
        + IMQ + ".httpjms.http.connectionTimeout=300\n"
        + IMQ + ".httpsjms.https.isHostTrusted=true\n"
        + IMQ + ".httpsjms.min_threads=10\n"
        + IMQ + ".httpsjms.max_threads=500\n"
        + IMQ + ".httpsjms.threadpool_model=dedicated\n"
        + IMQ + ".ssladmin.protocoltype=tls\n"
        + IMQ + ".ssladmin.servicetype=ADMIN\n"
        + IMQ + ".ssladmin.tls.port=0\n"
        + IMQ + ".ssladmin.tls.backlog=5\n"
        + IMQ + ".ssladmin.tcp.blocking=true\n"
        + IMQ + ".ssladmin.tcp.useChannels=false\n"
        + IMQ + ".ssladmin.min_threads=4\n"
        + IMQ + ".ssladmin.max_threads=10\n"
        + IMQ + ".ssladmin.threadpool_model=dedicated\n"
        + IMQ + ".protocol.tcp.inbufsz=2048\n"
        + IMQ + ".protocol.tcp.outbufsz=2048\n"
        + IMQ + ".protocol.tls.inbufsz=2048\n"
        + IMQ + ".protocol.tls.outbufsz=2048\n"
        + IMQ + ".protocol.http.inbufsz=2048\n"
        + IMQ + ".protocol.http.outbufsz=2048\n"
        + IMQ + ".protocol.https.inbufsz=2048\n"
        + IMQ + ".protocol.https.outbufsz=2048\n"
        + IMQ + ".protocol.tcp.nodelay=true\n"
        + IMQ + ".protocol.tls.nodelay=true\n"
        + IMQ + ".protocol.http.nodelay=true\n"
        + IMQ + ".protocol.https.nodelay=true\n"

        + IMQ + ".jmx.usePlatformMBeanServer=true\n"
        + IMQ + ".jmx.rmiregistry.start=false\n"
        + IMQ + ".jmx.rmiregistry.use=false\n"
        + IMQ + ".jmx.connector.list=jmxrmi,ssljmxrmi\n"
        + IMQ + ".jmx.connector.activelist=jmxrmi\n"
        + IMQ + ".jmx.connector.ssljmxrmi.useSSL=true\n"

        + IMQ + ".portmapper.port=7676\n"
        + IMQ + ".portmapper.bind=true\n"
        + IMQ + ".portmapper.backlog=50\n"
        + IMQ + ".portmapper.sotimeout=500\n"
        + IMQ + ".portmapper.solinger=5\n"
        + IMQ + ".message.expiration.interval=60\n"
        + IMQ + ".system.max_count=-1\n"
        + IMQ + ".system.max_size=-1\n"
        + IMQ + ".message.max_size=-1\n"
        + IMQ + ".persist.store=file\n"
        + IMQ + ".persist.file.message.vrfile.threshold_factor=0\n"
	+ IMQ + ".persist.file.message.vrfile.threshold=0\n"
	+ IMQ + ".persist.file.message.vrfile.growth_factor=50\n"
        + IMQ + ".persist.file.message.vrfile.initial_size=1m\n"
        + IMQ + ".persist.file.message.vrfile.block_size=256\n"
        + IMQ + ".persist.file.message.max_record_size=1m\n"
        + IMQ + ".persist.file.message.filepool.cleanratio=60\n"
        + IMQ + ".persist.file.destination.message.filepool.limit=100\n"
        + IMQ + ".persist.file.message.cleanup=false\n"
        + IMQ + ".persist.file.destination.file.size=1m\n"
        + IMQ + ".authentication.basic.user_repository=file\n"
        + IMQ + ".authentication.digest.user_repository=file\n"
        + IMQ + ".authentication.type=digest\n"
        + IMQ + ".authentication.client.response.timeout=180\n"
        + IMQ + ".user_repository.file.filename=passwd\n"
        + IMQ + ".user_repository.file.dirpath=${imq.instanceshome}${/}${imq.instancename}${/}etc\n"
        + IMQ + ".user_repository.ldap.grpsearch=false\n"
        + IMQ + ".user_repository.ldap.ssl_enabled=false\n"
        + IMQ + ".user_repository.ldap.ssl.socketfactory=com.sun.messaging.jmq.jmsserver.auth.ldap.TrustSSLSocketFactory\n"
        + IMQ + ".user_repository.ldap.timeout=180\n"
        + IMQ + ".accesscontrol.enabled=true\n"
        + IMQ + ".accesscontrol.type=file\n"
        + IMQ + ".accesscontrol.file.dirpath=${imq.instanceshome}${/}${imq.instancename}${/}etc\n"
        + IMQ + ".accesscontrol.file.filename=accesscontrol.properties\n"
        // Logging fallback properties are delared under Logger_FallbackProperties
//        + ".level=INFO\n"
//        + "handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler, com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler\n"
//        + "java.util.logging.FileHandler.level=ALL\n"
//        + "java.util.logging.FileHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter\n"
//        + "java.util.logging.FileHandler.limit=268435456\n"
//        + "java.util.logging.FileHandler.pattern=${imq.instanceshome}${/}${imq.instancename}${/}log${/}log.txt\n"
//        + "java.util.logging.ConsoleHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter\n"
//        + "java.util.logging.ConsoleHandler.level=INFO\n"
//        + "imq.log.jmx.output=ALL\n"
        + IMQ + ".metrics.enabled=true\n"
        + IMQ + ".metrics.interval=0\n"
        + IMQ + ".metrics.topic.enabled=true\n"
        + IMQ + ".metrics.topic.interval=60\n"
        + IMQ + ".metrics.topic.persist=false\n"
        + IMQ + ".metrics.topic.timetolive=300\n"
        + IMQ + ".autocreate.topic=true\n"
        + IMQ + ".autocreate.queue=true\n"
        + IMQ + ".autocreate.queue.maxNumActiveConsumers=-1\n"
        + IMQ + ".autocreate.queue.maxNumBackupConsumers=0\n"
        + IMQ + ".queue.deliverypolicy=single\n"
        + IMQ + ".queue.rr.messageblock=5\n"
        + IMQ + ".restart.code=255\n"
        + IMQ + ".protocol.list=tcp\n"
        + IMQ + ".service.list=jms,admin,ssljms,httpjms,httpsjms,ssladmin,wsjms,wssjms\n"
        + IMQ + ".authentication.basic.properties=class,user_repository\n"
        + IMQ + ".authentication.basic.class=com.sun.messaging.jmq.jmsserver.auth.JMQBasicAuthenticationHandler\n"
        + IMQ + ".authentication.digest.properties=class,user_repository\n"
        + IMQ + ".authentication.digest.class=com.sun.messaging.jmq.jmsserver.auth.JMQDigestAuthenticationHandler\n"
        + IMQ + ".user_repository.file.properties=class,filename,userPrincipalClass,groupPrincipalClass,dirpath\n"
        + IMQ + ".user_repository.file.class=com.sun.messaging.jmq.jmsserver.auth.file.JMQFileUserRepository\n"
        + IMQ + ".user_repository.file.userPrincipalClass=com.sun.messaging.jmq.auth.jaas.MQUser\n"
        + IMQ + ".user_repository.file.groupPrincipalClass=com.sun.messaging.jmq.auth.jaas.MQGroup\n"
        + IMQ + ".user_repository.ldap.properties=class,server,principal,password,base,uidattr,usrformat,usrfilter,grpsearch,grpbase,gidattr,memattr,grpfilter,timeout,ssl.enabled,ssl.socketfactory,userPrincipalClass,groupPrincipalClass\n"
        + IMQ + ".user_repository.ldap.class=com.sun.messaging.jmq.jmsserver.auth.ldap.LdapUserRepository\n"
        + IMQ + ".user_repository.ldap.userPrincipalClass=com.sun.messaging.jmq.auth.jaas.MQUser\n"
        + IMQ + ".user_repository.ldap.groupPrincipalClass=com.sun.messaging.jmq.auth.jaas.MQGroup\n"
        + IMQ + ".user_repository.jaas.properties=class,name,userPrincipalClass,groupPrincipalClass,subjectHelperClass,subjectHelperClass.props\n"
        + IMQ + ".user_repository.jaas.class=com.sun.messaging.jmq.jmsserver.auth.jaas.UserRepositoryImpl\n"
        + IMQ + ".accesscontrol.file.properties=class,filename,dirpath,url\n"
        + IMQ + ".accesscontrol.file.class=com.sun.messaging.jmq.jmsserver.auth.acl.JMQFileAccessControlModel\n"
        + IMQ + ".accesscontrol.jaas.properties=class,permissionFactory,permissionFactoryProvide,policyProvider\n"
        + IMQ + ".accesscontrol.jaas.class=com.sun.messaging.jmq.jmsserver.auth.acl.JAASAccessControlModel\n"
        + IMQ + ".log.file.class=com.sun.messaging.jmq.util.log.FileLogHandler\n"
        + IMQ + ".log.console.class=com.sun.messaging.jmq.util.log.StreamLogHandler\n"
        + IMQ + ".log.syslog.class=com.sun.messaging.jmq.util.log.SysLogHandler\n"
        + IMQ + ".log.destination.class=com.sun.messaging.jmq.jmsserver.service.DestinationLogHandler\n"
        + IMQ + ".log.destination.topic=mq.log.broker\n"
        + IMQ + ".log.jmx.class=com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler\n"
        + IMQ + ".protocol.tcp.propertylist=port,backlog,useChannels,blocking,hostname\n"
        + IMQ + ".protocol.tcp.class=com.sun.messaging.jmq.jmsserver.net.tcp.TcpProtocol\n"
        + IMQ + ".protocol.tls.propertylist=port,backlog,keystore,useChannels,blocking,hostname\n"
        + IMQ + ".protocol.tls.class=com.sun.messaging.jmq.jmsserver.net.tls.TLSProtocol\n"
        + IMQ + ".protocol.http.propertylist=servletHost,servletPort,pullPeriod,connectionTimeout,useChannels,blocking\n"
        + IMQ + ".protocol.http.class=com.sun.messaging.jmq.jmsserver.net.http.HTTPProtocol\n"
        + IMQ + ".protocol.https.propertylist=servletHost,servletPort,pullPeriod,connectionTimeout,isHostTrusted\n"
        + IMQ + ".protocol.https.class=com.sun.messaging.jmq.jmsserver.net.https.HttpsProtocol\n"
        + IMQ + ".protocol.ws.propertylist=port,hostname,backlog\n"
        + IMQ + ".protocol.wss.propertylist=port,hostname,backlog,requireClientAuth\n"
        + IMQ + ".service_handler.mqdirect.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQEmbeddedServiceFactory\n"
        + IMQ + ".service_handler.mqdirect2.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQDualThreadServiceFactory\n"
        + IMQ + ".service_handler.dedicated.class=com.sun.messaging.jmq.jmsserver.service.imq.dedicated.DedicatedServiceFactory\n"
        + IMQ + ".service_handler.shared_old.class=com.sun.messaging.jmq.jmsserver.service.imq.group.GroupServiceFactory\n"
        + IMQ + ".service_handler.group_old.class=com.sun.messaging.jmq.jmsserver.service.imq.group.GroupServiceFactory\n"
        + IMQ + ".service_handler.direct.class=com.sun.messaging.jmq.jmsserver.service.imq.IMQDirectServiceFactory\n"
        + IMQ + ".service_handler.shared.class=com.sun.messaging.jmq.jmsserver.service.imq.grizzly.GrizzlyIPServiceFactory\n"
        + IMQ + ".service_handler.websocket.class=com.sun.messaging.jmq.jmsserver.service.imq.websocket.WebSocketIPServiceFactory\n"
        + IMQ + ".selectors.limit=16\n"
        + IMQ + ".cluster.port=0\n"
        + IMQ + ".cluster.masterbroker.enforce=true\n"
        + IMQ + ".cluster.locktimeout=60\n"
        + IMQ + ".cluster.sharecc.persist=jdbc\n"
        + IMQ + ".cluster.sharecc.persistCreate=true\n"
        + IMQ + ".memory.levels=green,yellow,orange,red\n"
        + IMQ + ".memory.hysteresis=1024\n"
        + IMQ + ".memory.overhead=10240\n"
        + IMQ + ".memory.gcdelta=1024\n"
        + IMQ + ".green.threshold=0\n"
        + IMQ + ".green.count=50000\n"
        + IMQ + ".green.gccount=0\n"
        + IMQ + ".green.gcitr=0\n"
        + IMQ + ".green.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Green\n"
        + IMQ + ".yellow.threshold=60\n"
        + IMQ + ".yellow.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Yellow\n"
        + IMQ + ".yellow.count=50\n"
        + IMQ + ".yellow.gccount=1\n"
        + IMQ + ".yellow.gcitr=1000\n"
        + IMQ + ".yellow.seconds=2\n"
        + IMQ + ".orange.threshold=80\n"
        + IMQ + ".orange.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Orange\n"
        + IMQ + ".orange.count=1\n"
        + IMQ + ".orange.gccount=5\n"
        + IMQ + ".orange.gcitr=100\n"
        + IMQ + ".orange.seconds=1\n"
        + IMQ + ".red.threshold=90\n"
        + IMQ + ".red.classname=com.sun.messaging.jmq.jmsserver.memory.levels.Red\n"
        + IMQ + ".red.count=0\n"
        + IMQ + ".red.gccount=10\n"
        + IMQ + ".red.gcitr=5\n"
        + IMQ + ".red.seconds=10\n"
        + IMQ + ".cluster.manager.class="+
          "com.sun.messaging.jmq.jmsserver.cluster.manager.ClusterManagerImpl\n"
        + IMQ + ".hacluster.jdbc.manager.class="+
          "com.sun.messaging.jmq.jmsserver.cluster.manager.ha.HAClusterManagerImpl\n"
        + IMQ + ".hacluster.bdbsfs.manager.class="+
          "com.sun.messaging.jmq.jmsserver.cluster.manager.ha.SFSHAClusterManagerImpl\n"
        + IMQ + ".cluster.migratable.bdb.manager.class="+
          "com.sun.messaging.jmq.jmsserver.cluster.manager.ha.RepHAClusterManagerImpl\n"
        + IMQ + ".cluster.heartbeat.class="+
          "com.sun.messaging.jmq.jmsserver.multibroker.heartbeat.HeartbeatImpl\n"
        ;

    //------------------------------------------------------------------------
    //--  Temporary FALLBACK properties until we are fully inside nuclues all the time                         --
    //------------------------------------------------------------------------
    public static final String Logger_FallbackProperties =
    	
        "handlers=java.util.logging.FileHandler, java.util.logging.ConsoleHandler, com.sun.messaging.jmq.jmsserver.management.agent.JMXLogHandler\n"
    	+ ".level=INFO\n"
        + "java.util.logging.FileHandler.level=ALL\n"
        + "java.util.logging.FileHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter\n"
        + "java.util.logging.FileHandler.limit=268435456\n"
        + "java.util.logging.FileHandler.pattern=${imq.instanceshome}${/}${imq.instancename}${/}log${/}log.txt\n"
        + "java.util.logging.FileHandler.append=true\n"
        // Currently not working but it is intended to be used once working
        + "com.sun.enterprise.server.logging.GFFileHandler.file=${imq.instanceshome}${/}${imq.instancename}${/}log${/}log.txt\n)" 
        + "com.sun.enterprise.server.logging.GFFileHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter\n"
        + "com.sun.enterprise.server.logging.GFFileHandler.rotationLimitInBytes=268435456\n"
        + "com.sun.enterprise.server.logging.GFFileHandler.rotationTimelimitInMinutes=10080\n"
        + "com.sun.enterprise.server.logging.GFFileHandler.level = ALL\n"
        
        + "java.util.logging.ConsoleHandler.formatter=com.sun.messaging.jmq.util.log.UniformLogFormatter\n"
        + "java.util.logging.ConsoleHandler.level=INFO\n"
        + "imq.log.jmx.output=ALL\n"
        + "sun.os. patch.level=unknown\n" // Workaround 
        ;
    //------------------------------------------------------------------------
    //--                       static final config information              --
    //------------------------------------------------------------------------


    /**
     * full path to the non-editable iMQ configuration location.
     */
    private static final String JMQ_prop_loc = CommGlobals.getJMQ_LIB_HOME() + File.separator +
                                               CommGlobals.JMQ_BROKER_PROP_LOC;

    /**
     * full path to the default properties file. This is a non-editable property
     * file which contains the default settings for all properties
     */
    private static final String default_loc = JMQ_prop_loc + "default.properties";

    /**
     * full path to the install properties file. This is a non-editable property
     * file which contains any property setting set during install which affect
     * any version of iMQ running w/ this install image.
     */
    private static final String install_loc = JMQ_prop_loc + "install.properties";


    /**
     * optional property which defines the Configuration store class
     */
    private static final String ConfigStoreProperty=IMQ + ".config.class";

    /**
     * the default class for handling Configuration Storage
     */
    private static final String Default_Config_Store= 
                     "com.sun.messaging.jmq.jmsserver.config.FileConfigStore";

  

 
    //------------------------------------------------------------------------
    //--                       instance variables                           --
    //------------------------------------------------------------------------
   

    Properties params = null;
    transient ConfigStore localconfig = null;
    transient Logger logger = null;


    //#########################################################################
    //#                                                                       #
    //#                             METHODS                                   #
    //#                                                                       #
    //#########################################################################





    //------------------------------------------------------------------------
    //--               constructor methods                                  --
    //------------------------------------------------------------------------
         

    /**
     * create a properties file with the passed in instance location
     *
     * XXX - LKS 6/29/00 - Need to handle exceptions with errors ..
     * XXX - LKS 6/29/00 - log messages
     *
     * @param configname the name used by the broker, passed in at startup
     */
    public BrokerConfig(String configname, Properties params, boolean resetProp, Properties saveProps) throws BrokerException {
        super();

        if (params == null) {
            params = new Properties(); // create empty object, its easier
        }
        this.params = params;

        logger = CommGlobals.getLogger();
        
        logger.log(Logger.DEBUG, BrokerResources.I_JMQ_HOME,
                                 CommGlobals.getJMQ_HOME());

        InputStream is = null;

        try {
            this.load(new ByteArrayInputStream(JMQ_FallbackProperties.getBytes()));
        } catch (IOException ex1) {
            throw new BrokerException(CommGlobals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"unable to load fallback properties"), ex1);
        }

        try {
            this.load(new ByteArrayInputStream(Logger_FallbackProperties.getBytes()));
        } catch (IOException ex1) {
            throw new BrokerException(CommGlobals.getBrokerResources().getString(
               BrokerResources.X_INTERNAL_EXCEPTION,"unable to load logger fallback properties"), ex1);
        }

        try {
            loadDefaultProperties(default_loc);
        } catch (IOException ex) {
            logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY_FILE,
                "default", default_loc, ex);
            logger.log(Logger.ERROR, BrokerResources.E_FALLBACK_PROPS);

            // OK if we got this error .. nothing else to do, we'll just have
            // to use the fallback properties

            return;

        }

        try {
            loadDefaultProperties(install_loc);
        } catch (Exception ex) {

            logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY_FILE,
                "install", install_loc, ex);
        }

        // NOW ... determine the class for the configuration storage

        String configprop =  super.getProperty(ConfigStoreProperty,
                             Default_Config_Store);
        try {
            localconfig = (ConfigStore) Class.forName(configprop).newInstance();  
        } catch (Exception ex) {
            logger.logStack(Logger.ERROR, BrokerResources.E_BAD_CONFIG_STORE,
                     configprop, ex );
        }

        if (localconfig != null) {
	    if (resetProp) {
                localconfig.clearProps(configname);
            }
            Properties storedprops = localconfig.loadStoredProps(this, configname);
            Properties clusterprops = localconfig.loadClusterProps(this, params, storedprops);

            // overlay cluster props
            if (clusterprops != null)
                putAll(clusterprops);

            // overlay stored props
            setStoredProperties(storedprops);

        }

        /*
         * Fix 4939648
         * Load system properties for properties. This to to handle the 
         * case where somebody used "-Dfoo=bar" with the java command to
         * set an MQ property. It also lets the system properties be
         * dumped when we dump broker state.
         */
        this.putAll(System.getProperties());

        /* Put properties from command line */
        putAll(params);
        if (saveProps != null) {
            setStoredProperties(saveProps);
        }
        checkProperties();
        
    }

    /**
     * checks the properties for bogus values ... currently only
     * looks for trailing whitespace, however we may want to expand
     * it in the future to remove ctrl characters, etc 
     */
    protected void checkProperties() {
        Enumeration keys = propertyNames();
        while (keys.hasMoreElements()) {
            String name = (String)keys.nextElement();
            if (name == null) continue;
            String value = getProperty(name);
            if (value == null || value.length() <= 0)
                continue;
            char c = value.charAt(value.length() -1);
            if (Character.isSpaceChar(c)) {
                Exception e = null;
                value = value.trim();
                try {
                    updateProperty(name, value);
                } catch (Exception ex) {
                    e = ex;
                    put(name, value);
                }
                if (e == null)
                    logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY,
                         name, value );
                else
                    logger.log(Logger.WARNING, BrokerResources.W_BAD_PROPERTY,
                         name, value, e );
            }
        }
    }


    //------------------------------------------------------------------------
    //--             Methods for setting/Updating/Retrieving properties     --
    //------------------------------------------------------------------------
    

    /**
     * Writes out the updated instance property file when a property is
     * changed. For BrokerConfig, we want to override the standard
     * UpdateProperties mechanism to use localconfig to provide the
     * flexibility to add additional formats for storing properties 
     * in the future (e.g. JNDI)
     *
     * XXX - LKS 7/5/00 - How should IOExceptiones be handled ?? 
     */
    protected void saveUpdatedProperties(Properties props)
        throws IOException
    {
        if (localconfig == null) {

            logger.log(Logger.WARNING, BrokerResources.W_CONFIG_STORE_WRITE);

            return;
        }
        localconfig.storeProperties(storedprops);
    }

    public void reloadProps(String instancename, String[] propnames)
        throws BrokerException
    {
        reloadProps(instancename, propnames, true);
    }

    /**
     * Reload given set of properties from the configuration store.
     */
    public void reloadProps(String instancename, String[] propnames, boolean overideparams)
        throws BrokerException {
        if (localconfig == null)
            return;

        // Remove old values.
        ArrayList newpropnames  = new ArrayList();
        for (int i = 0; i < propnames.length; i++) {
            if (overideparams) {
                remove(propnames[i]);
            } else if (params.get(propnames[i]) == null) {
                remove(propnames[i]);
                newpropnames.add(propnames[i]);
            }
        }
        if (!overideparams) propnames = (String[]) newpropnames.toArray(
                                            new String[newpropnames.size()]);

        // Reload broker properties.

        Properties sprops = localconfig.reloadProps(instancename, propnames);
        if (sprops == null) sprops = new Properties();

        Properties clusterprops =  localconfig.loadClusterProps(this, new Properties(), sprops);

        Properties cprops = new Properties();
        if (clusterprops != null) {
            String value = null;
            for (int i = 0; i < propnames.length; i++) {
                value = clusterprops.getProperty(propnames[i]);
                if (value != null) cprops.setProperty(propnames[i], value);
            }
        }

        // ok, override sprops over cprops
        cprops.putAll(sprops);

        // update the broker - sending out notifications
        try {
            updateProperties(cprops, false);
        } catch (Exception ex) {
           putAll(cprops);
        }
    }

}
