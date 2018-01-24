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
 * @(#)DebugHandler.java	1.42 08/13/07
 */ 

package com.sun.messaging.jmq.jmsserver.data.handlers.admin;

import java.util.Hashtable;
import java.util.List;
import java.io.*;
import java.util.Iterator;
import java.util.Properties;
import java.lang.reflect.*;
import com.sun.messaging.jmq.util.Debug;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.util.GoodbyeReason;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQConnection;
import com.sun.messaging.jmq.jmsserver.service.imq.IMQBasicConnection;
import com.sun.messaging.jmq.jmsserver.service.ConnectionUID;
import com.sun.messaging.jmq.jmsserver.service.Service;
import com.sun.messaging.jmq.jmsserver.core.Producer;
import com.sun.messaging.jmq.jmsserver.core.Session;
import com.sun.messaging.jmq.jmsserver.core.SessionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionUID;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.core.ConsumerUID;
import com.sun.messaging.jmq.jmsserver.core.Consumer;
import com.sun.messaging.jmq.jmsserver.plugin.spi.ConsumerSpi;
import com.sun.messaging.jmq.jmsserver.core.ProducerUID;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.util.DebugPrinter;
import com.sun.messaging.jmq.util.SupportUtil;
import com.sun.messaging.jmq.util.admin.MessageType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.FaultInjection;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.memory.MemoryManager;

/**
 * Admin handler for DEBUG message.
 */
public class DebugHandler extends AdminCmdHandler
{
    private static boolean DEBUG = getDEBUG();

    public DebugHandler(AdminDataHandler parent) {
	super(parent);
    }

    /**
     * Handle the incomming administration message.
     *
     * @param con	The Connection the message came in on.
     * @param cmd_msg	The administration message
     * @param cmd_props The properties from the administration message
     */
    public boolean handle(IMQConnection con, Packet cmd_msg,
				       Hashtable cmd_props) {

        int status = Status.OK;
        String msg = null;
        boolean logOnly = false;

	if ( DEBUG ) {
            logger.log(Logger.DEBUG, this.getClass().getName() + ": " +
                cmd_props);
        }

	String cmd = (String)cmd_props.get(MessageType.JMQ_CMD),
	        cmdarg = (String)cmd_props.get(MessageType.JMQ_CMDARG),
	        target = (String)cmd_props.get(MessageType.JMQ_TARGET),
	        targetType = (String)cmd_props.get(MessageType.JMQ_TARGET_TYPE);

        // Get properties we are to update from message body
	Properties p = (Properties)getBodyObject(cmd_msg);

	/*
	 * To always see output (when debugging/developing this handler)
	 * change:
	 *   if (DEBUG)
	 * to
	 *   if (true)
	 *
	 * and 
	 *   Logger.DEBUG
	 * to
	 *   Logger.INFO
	 */
	if (DEBUG)  {
	    logger.log(Logger.DEBUG, "DEBUG message received:");
	    logger.log(Logger.DEBUG, "\t" + MessageType.JMQ_CMD + ": " + cmd);
	    logger.log(Logger.DEBUG, "\t" + MessageType.JMQ_CMDARG + ": " + cmdarg);
	    logger.log(Logger.DEBUG, "\t" + MessageType.JMQ_TARGET + ": " + target);
	    logger.log(Logger.DEBUG, "\tOptional Properties: " + p);
	}

	/*
	 * CHECK: Should make sure relevant values above are non null (e.g. cmd).
	 */

	Hashtable debugHash = null;
	String fileStr = p.getProperty("file");

	// Send reply
	Packet reply = new Packet(con.useDirectBuffers());
	reply.setPacketType(PacketType.OBJECT_MESSAGE);

        if ((cmd == null) || (cmdarg == null)) {
            status = Status.BAD_REQUEST;
            msg = "Null/Missing values for " 
			+ MessageType.JMQ_CMD
		  	+ " and/or "
			+ MessageType.JMQ_CMDARG
		  	+ " properties.";

	    setProperties(reply, MessageType.DEBUG_REPLY,
		status, msg);

	    parent.sendReply(con, cmd_msg, reply);

            return true;
	}


/*
 * imqcmd kill cxn -n #
 * imqcmd [debug] [pkt|pktin|pktout|<class>|gc]
 */

        if (cmd.equals("update") && cmdarg.equals("bkr")) {
            // Get properties we are to update from message body

            // Update the broker configuration
	    BrokerConfig bcfg = Globals.getConfig();
	    try {
                 bcfg.updateProperties(p, true);
	    } catch (PropertyUpdateException e) {
                status = Status.BAD_REQUEST;
                msg = e.getMessage();
                logger.log(Logger.WARNING, msg);
	    } catch (IOException e) {
                status = Status.ERROR;
                msg = e.toString();
                logger.log(Logger.WARNING, msg);
	    }
 
	    // Send reply

	    setProperties(reply, MessageType.DEBUG_REPLY,
		status, msg);

	    parent.sendReply(con, cmd_msg, reply);
            return true;        
        } else if (cmd.equals("dump") || cmd.equals("query")) {
            if (cmd.equals("dump")) {
                logOnly = true;
                if (fileStr == null) {
                    fileStr = "dumpOutput";
                }
            }
            try {
                debugHash = getDebugInfo(cmdarg, target, targetType, p); 
            } catch (Exception ex) {
                status = Status.ERROR;
                msg = "Error "+cmd+"ing " + cmdarg + " because "
                         + ex.getMessage();
                logger.logStack(Logger.INFO,msg, ex);
            }
      
        } else if (cmd.equals("list")) {
   	    debugHash = new Hashtable();
            if (cmdarg.equals("dst")) { 
                Iterator[] itrs = DL.getAllDestinations(null);
                Iterator itr = itrs[0];
                while (itr.hasNext()) {
                    Destination d = (Destination)itr.next();
                    debugHash.put(d.getDestinationUID().toString(),
                        DestType.toString(d.getType()) + ":" +
                        (d.isStored() ? "stored" : "not stored"));
                }
            } else if (cmdarg.equals("con")) {
   	      debugHash = new Hashtable();
              
              if (target == null) { // all
                  Iterator itr = Consumer.getAllConsumers();
                  if (!itr.hasNext()) {
                      status = Status.ERROR;
                      msg = "No consumers on the broker";
                  }
                  while (itr.hasNext()) {
                      Consumer c = (Consumer)itr.next();
                      if ( c == null) continue;
                      IMQConnection cxn = (IMQConnection)
                          Globals.getConnectionManager()
                              .getConnection(c.getConnectionUID());
                      ConsumerUID cuid = c.getConsumerUID();
                      ConnectionUID cxuid = c.getConnectionUID(); 
                      debugHash.put(String.valueOf(
                             (cuid == null ? 0 : cuid.longValue())),
                             (cxn == null ? "none" : cxn.getRemoteConnectionString())
                            + " ["
                            +  String.valueOf( (cxuid == null ? 0 : 
                                 cxuid.longValue())) + "]");
                  }
              } else if (targetType == null) {
                  msg = "Please supply targetType if you are supplying a target ";
                  status = Status.ERROR;
              } else if (targetType.equals("t") || targetType.equals("q")) {
                  try {
                      boolean isQueue = false;
                      if (targetType.equals("q")) {
                          isQueue = true;
                      }
                      DestinationUID uid = null;
                      Destination d = null;
                      
                      if (status != Status.ERROR) {
                          uid = DestinationUID.getUID(target,isQueue);
                          Destination[] ds = DL.getDestination(null, uid);
                          d = ds[0];
                      }
                      if (status != Status.ERROR && d == null) {
                          status = Status.ERROR;
                          msg = "Error listing consumers on destination  " + target +
                             " unknown destination";
                      } else if (status != Status.ERROR) {
                          Iterator itr = d.getConsumers();
                          if (!itr.hasNext()) {
                              status = Status.ERROR;
                              msg = "No consumers on destination  " + target ;
                          }
      
                          while (itr.hasNext()) {
                              Consumer c = (Consumer)itr.next();
                              IMQConnection cxn = (IMQConnection)
                              Globals.getConnectionManager()
                                  .getConnection(c.getConnectionUID());
                              debugHash.put(String.valueOf(
                                  c.getConsumerUID().longValue()),
                                  (cxn == null ? "" : cxn.getRemoteConnectionString())  + " ["
                                  + String.valueOf((c.getConnectionUID() == null ?
                                                    "none":c.getConnectionUID().longValue())) + "]");
                          }
                      }
                   } catch (Exception ex) {
                       status = Status.ERROR;
                       msg = "Error listing consumers on connection  " + target +
                         " because " + ex.toString();
                      logger.logStack(Logger.INFO,msg, ex);
                   }
              } else if (targetType.equals("ses")) {
                  try {
                      SessionUID uid = new SessionUID(
                          Long.parseLong(target));
                      Session ses = Session.getSession(uid);
                      if (ses == null) {
                          status = Status.ERROR;
                          msg = "Error listing consumers on session  " 
                             + target + " unknown sessionUID";
                      } else {
                          Iterator itr = ses.getConsumers();
                          if (!itr.hasNext()) {
                              status = Status.ERROR;
                              msg = "No consumers on session  " + target ;
                          }
                          while (ses != null && itr.hasNext()) {
                              ConsumerSpi c = (ConsumerSpi)itr.next();
                              if (uid.equals(c.getConnectionUID())) {
                                  debugHash.put(String.valueOf(
                                        c.getConsumerUID().longValue()),
                                        c.getDestinationUID().toString());
                              }
                          }
                      }
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error listing consumers on connection  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
              } else if (targetType.equals("cxn")) {
                  try {
                      ConnectionUID uid = new ConnectionUID(
                          Long.parseLong(target));
                      IMQConnection cxn = (IMQConnection)
                          Globals.getConnectionManager()
                                  .getConnection(uid);
                      if (cxn == null) {
                          status = Status.ERROR;
                          msg = "Error listing consumers on connection  " + target +
                             " unknown connectionUID";
                      } else {
                          Iterator itr = Consumer.getAllConsumers();
                          while (cxn != null && itr.hasNext()) {
                              Consumer c = (Consumer)itr.next();
                              if (uid.equals(c.getConnectionUID())) {
                                  debugHash.put(String.valueOf(
                                        c.getConsumerUID().longValue()),
                                        c.getDestinationUID().toString());
                              }
                          }
                      }
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error listing consumers on connection  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
              } else  {
                  status = Status.ERROR;
                  msg = "Unknown targetType (-t) " + target
                        + "\n Valid formats are of the form: "
                        + "[q|t|ses|cxn]";
              }
            } else if (cmdarg.equals("prd")) {
   	      debugHash = new Hashtable();
              
              if (target == null) { // all
                  Iterator itr = Producer.getAllProducers();
                  while (itr.hasNext()) {
                      Producer c = (Producer)itr.next();
                      IMQConnection cxn = (IMQConnection)
                          Globals.getConnectionManager()
                              .getConnection(c.getConnectionUID());
 
                      debugHash.put(String.valueOf(
                             c.getProducerUID().longValue()),
                             cxn.getRemoteConnectionString()  + " ["
                            +  String.valueOf(c.getConnectionUID()
                                 .longValue()) + "]");
                  }
              } else if (targetType == null) {
                  msg = "Please supply targetType if you are supplying a target ";
                  status = Status.ERROR;
              } else if (targetType.equals("t") || targetType.equals("q")) {
                  // destination
                  boolean isQueue = false;
                  if (targetType.equals("q")) {
                     isQueue = true;
                  }
                  try {
                      DestinationUID uid = DestinationUID.getUID(target, isQueue);
                      Destination[] ds = DL.getDestination(null, uid);
                      Destination d = ds[0];
                      if (d == null) {
                          status = Status.ERROR;
                          msg = "Error listing producers on destination  " + target +
                             " unknown destination";
                      } else {
                          Iterator itr = d.getProducers();
                          while (itr.hasNext()) {
                              Producer c = (Producer)itr.next();
                              IMQConnection cxn = (IMQConnection)
                              Globals.getConnectionManager()
                                  .getConnection(c.getConnectionUID());
 
                              debugHash.put(String.valueOf(
                                  c.getProducerUID().longValue()),
                                  cxn.getRemoteConnectionString()  + " ["
                                  + String.valueOf(c.getConnectionUID()
                                     .longValue()) + "]");
                          }
                      }
                   } catch (Exception ex) {
                       status = Status.ERROR;
                       msg = "Error listing producers on connection  " + target +
                         " because " + ex.toString();
                      logger.logStack(Logger.INFO,msg, ex);
                   }
              } else if (targetType.equals("cxn")) {
                  try {
                      ConnectionUID uid = new ConnectionUID(
                          Long.parseLong(target));
                      IMQConnection cxn = (IMQConnection)
                          Globals.getConnectionManager()
                                  .getConnection(uid);
                      if (cxn == null) {
                          status = Status.ERROR;
                          msg = "Error listing producers on connection  " + target +
                             " unknown connectionUID";
                      } else {
                          Iterator itr = Producer.getAllProducers();
                          while (cxn != null && itr.hasNext()) {
                              Producer c = (Producer)itr.next();
                              if (uid.equals(c.getConnectionUID())) {
                                  debugHash.put(String.valueOf(
                                        c.getProducerUID().longValue()),
                                        c.getDestinationUID().toString());
                              }
                          }
                      }
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error listing producers on connection  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
              } else  {
                  status = Status.ERROR;
                  msg = "Unknown targetType (-t) " + targetType
                        + "\n Valid formats are of the form: "
                        + "[t|q|cxn]";
              }
            } else {
                status = Status.ERROR;
                msg = "Unknown argument " + cmdarg;
            }
                
        } else if (cmd.equals("debug")) {
            String debugStr = (String) p.get("enable");

            if (debugStr != null && !debugStr.equalsIgnoreCase("true")
                 && !debugStr.equalsIgnoreCase("false")) {
                status = Status.ERROR;
                msg = "bad enable flag setting " +
                   debugStr + " defauling to false";
            }
            boolean debugOn=(debugStr == null) ? true :
                      Boolean.valueOf(debugStr).booleanValue();
            if (cmdarg.equals("reset")) {
                if (targetType == null) {
                    msg = "Please supply targetType (-t)\n Valid formats are of the form: [metrics|jdbcconnpool]";
                    status = Status.ERROR;
                } else if (targetType.equalsIgnoreCase("jdbcconnpool")) {
                    try {
                        if (Globals.getStore().isJDBCStore()) {
                            Globals.getStore().resetConnectionPool();
                        } else {
                            status = Status.ERROR;
                            msg = "Operation is not applicable for a file-based data store.";                            
                        } 
                    } catch (Exception ex) {
                        status = Status.ERROR;
                        msg = "Error resetting JDBC connection pool because " +
                            ex.toString();
                        logger.logStack(Logger.INFO, msg, ex);
                    }
                } else if (targetType.equalsIgnoreCase("metrics")) {
                    com.sun.messaging.jmq.jmsserver.data.handlers.admin.ResetMetricsHandler.resetAllMetrics();
                }
            } else if (cmdarg.equals("fault")) {

                // handle fault injection
                String faultName = (String)p.get("name");
                if (faultName == null)
                    faultName = target;
                String faultSelector = (String)p.get("selector");
                FaultInjection fi = FaultInjection.getInjection();
                boolean faultOn = true;

                // ok only turn off fault injection if no name pair
                if (debugStr != null && debugStr.equalsIgnoreCase("false")) {
                    if (faultName == null) {
                        fi.setFaultInjection(false);
                    } else {
                        fi.unsetFault(faultName);
                    }
                } else {
                    fi.setFaultInjection(true);
                    if (faultName != null) {
                        try {
                            fi.setFault(faultName, faultSelector, p);
                        } catch (Exception ex) {
                          status = Status.ERROR;
                          msg = "Bad Selector " + faultSelector;
                        }
                    }
                }

            } else if (cmdarg.equals("gc")) {
                logger.log(Logger.INFO,"GC'ing the system");
                System.gc();
                long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                msg = "Used memory is " + 
                    (usedMem/1024l) + "k, "  +
                   " this is " + (usedMem*100/Runtime.getRuntime().maxMemory())
                    + "% of " + (Runtime.getRuntime().maxMemory()/1024l)
                     + "k";
                logger.log(Logger.INFO,msg);
                if (debugHash == null) debugHash = new Hashtable();
                debugHash.put("Memory", msg);
                debugHash.put("Used", (usedMem/1024l)+"k");
                debugHash.put("Total", ((Runtime.getRuntime().totalMemory()/1024l)+"k"));
                debugHash.put("Free", ((Runtime.getRuntime().freeMemory()/1024l)+"k"));
                debugHash.put("Max", ((Runtime.getRuntime().maxMemory()/1024l)+"k"));
            } else if (cmdarg.equals("threads")) {
                // log
                try {
                    debugHash = new Hashtable();
                    debugHash.put("threads", "dumped to log");
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error "+cmd+"ing " + cmdarg + " because "
                         + ex.getMessage() + "";
                    logger.logStack(Logger.INFO,msg, ex);
                }
                logger.log(Logger.INFO,"Dumping threads:\n" + SupportUtil.getAllStackTraces("\t"));
            } else if (cmdarg.equals("pkt")) {
                IMQBasicConnection.dumpPacket(debugOn);
            } else if (cmdarg.equals("pktin")) {
                IMQBasicConnection.dumpInPacket(debugOn);
            } else if (cmdarg.equals("pktout")) {
                IMQBasicConnection.dumpOutPacket(debugOn);
            } else if (cmdarg.equals("class")) {
                try {
                    Class cl = Class.forName(target);
                    Field[] fields = cl.getDeclaredFields();
                    boolean found = false;
                    for (int i = 0; i < fields.length; i++) { 
                         if (fields[i].getName().equals(Debug.debugFieldName)) {
                             logger.log(Logger.INFO, "Turn "+(debugOn ? "on":"off")+" debug for class "+target);
                             final Field f =  fields[i];
                             java.security.AccessController.doPrivileged(
                                 new java.security.PrivilegedAction<Object>() {
                                     public Object run() { 
                                         f.setAccessible(true);
                                         return null;
                                     }
                             });
                             fields[i].setBoolean(null, debugOn);
                             found = true;
                             break;
                         }
                    }
                    if (!found) throw new NoSuchFieldException(Debug.debugFieldName);
                } catch (Exception ex) {
                   status = Status.ERROR;
                   msg = "Unable to set DEBUG on class " + target +
                         " because " + ex.toString();
                   logger.logStack(Logger.INFO,msg, ex);
                }
            } else {
                status = Status.ERROR;
                msg = "Unknown debug argument " + cmdarg;            
            }
        } else if (cmd.equals("resume")) {
            // session, connection, consumerUID, producer
            if (cmdarg.equals("prd")) {
                try {
                    ProducerUID pid = new ProducerUID(Long.parseLong(target));
                    Producer pr = (Producer)Producer.getProducer(pid);
                    Destination[] ds = DL.getDestination(null, pr.getDestinationUID());
                    Destination d = ds[0];
                    logger.log(Logger.INFO,"Resuming " + pr);
                    d.forceResumeFlow(pr);
                    
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error resuming flow from producer  " + target +
                         " because " + ex.toString();
                   logger.logStack(Logger.INFO,msg, ex);
                }
            } else if (cmdarg.equals("cxn")) {
                try {
                    ConnectionUID uid = new ConnectionUID(
                        Long.parseLong(target));
                    IMQConnection cxn = (IMQConnection)
                        Globals.getConnectionManager()
                                .getConnection(uid);
                    cxn.resumeFlow(-1);
                    
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error resuming flow on connection  " + target +
                         " because " + ex.toString();
                   logger.logStack(Logger.INFO,msg, ex);
                }
            } else if (cmdarg.equals("con")) {
                try {
                    ConsumerUID cid = new ConsumerUID(
                        Long.parseLong(target));
                    Consumer cxn = (Consumer)
                        Consumer.getConsumer(cid);
                    cxn.resume("admin debug");
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error resuming flow to consumer  " + target +
                         " because " + ex.toString();
                   logger.logStack(Logger.INFO,msg, ex);
                }
            } else if (cmdarg.equals("ses")) {
                try {
                    SessionUID sid = new SessionUID(
                        Long.parseLong(target));
                    Session session = (Session)
                        Session.getSession(sid);
                    session.resume("admin debug");
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error resuming flow to session  " + target +
                         " because " + ex.toString();
                   logger.logStack(Logger.INFO,msg, ex);
                }
            } else {
                status = Status.ERROR;
                msg = "Unknown resume argument " + cmdarg;            
            }
        } else if (cmd.equals("send")) {
            if (cmdarg.equals("cxn")) {
                try {
                    if (target == null) {
                        status = Status.ERROR;
                        msg = "Missing connectionUID ";
                    } else {
                        ConnectionUID uid = new ConnectionUID(
                            Long.parseLong(target));
                        IMQConnection cxn = (IMQConnection)
                            Globals.getConnectionManager()
                                    .getConnection(uid);
                        if (cxn == null) {
                            status = Status.ERROR;
                            msg = "Unknown connectionUID " + uid;
                        } else {
                            sendClientDEBUG(cxn, cmd_props, p);
                        }
                    }
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error notifying consumer  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
            } else if (cmdarg.equals("bkr") || cmdarg.equals("svc")) {
                Service s = null;
                if (target != null) {
                    s = Globals.getServiceManager().getService(
                        target);
                    if (s == null) {
                        msg = "Unknown service " + target ;
                        status = Status.ERROR;
                    }
                }

                Iterator itr = Globals.getConnectionManager().
                       getConnectionList(s).iterator();
                try {
                    while (itr.hasNext()) {
                        IMQConnection cxn = (IMQConnection)itr.next();
                        Packet pkt = new Packet(false);
                        pkt.setPacketType(PacketType.DEBUG);
                        Hashtable hash = new Hashtable(cmd_props);
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(p);
                        oos.flush();
                        bos.flush();
                        pkt.setMessageBody(bos.toByteArray());
                        pkt.setProperties(hash);
                        cxn.sendControlMessage(pkt);
                    }
                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error notifying consumer  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
                  
            } else {
                status = Status.ERROR;
                msg = "Unknown send argument " + cmdarg;            
            }
        } else if (cmd.equals("kill")) {
            if (cmdarg.equals("cxn")) {
                try {
                    ConnectionUID uid = new ConnectionUID(
                        Long.parseLong(target));
                    IMQConnection cxn = (IMQConnection)
                        Globals.getConnectionManager()
                                .getConnection(uid);
                    cxn.destroyConnection(true, GoodbyeReason.ADMIN_KILLED_CON,
                            "kill cnx");

                } catch (Exception ex) {
                    status = Status.ERROR;
                    msg = "Error killing connection  " + target +
                         " because " + ex.toString();
                    logger.logStack(Logger.INFO,msg, ex);
                }
                
            } else {
                status = Status.ERROR;
                msg = "Unknown kill argument " + cmdarg;            
            }
        }

	if (fileStr != null && debugHash != null)  {
	    DebugPrinter dbp = new DebugPrinter(2);
	    dbp.setHashtable(debugHash);
	    dbp.setFile(fileStr);
	    dbp.println();
	    dbp.close();
            if (status == Status.OK)
                msg = "Data logged at file " + fileStr;
            if (logOnly) {
                debugHash = new Hashtable();
                debugHash.put("logfile", fileStr);
            }
        }
        if (msg != null)
            logger.log(Logger.INFO,msg);

	setProperties(reply, MessageType.DEBUG_REPLY,
		status, msg);
	
        if (debugHash != null ) {
	    setBodyObject(reply, debugHash);
        }
	

	parent.sendReply(con, cmd_msg, reply);

    return true;
    }

    public static void sendClientDEBUG(IMQConnection cxn, 
        Hashtable cmd_props, Properties p) throws IOException {

        Packet pkt = new Packet(false);
        pkt.setPacketType(PacketType.DEBUG);
        Hashtable hash = new Hashtable(cmd_props);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(p);
        oos.flush();
        bos.flush();
        pkt.setMessageBody(bos.toByteArray());
        //hash.putAll(p);
        pkt.setProperties(hash);
        cxn.sendControlMessage(pkt);
    }

    public Hashtable  getDebugInfo(String arg, String target, String targetType,
              Properties p)
        throws Exception
    {
        if (arg.equals("cxn")) {
            if (target == null) {
                return getAllCxnInfo(null);
            } else {
                ConnectionUID uid = new ConnectionUID(
                      Long.parseLong(target));
                return getCxnInfo(uid);
            }
        } else if (arg.equals("config")) {
            return getConfig();
        } else if (arg.equals("memory")) {
            return getMemory();
        } else if (arg.equals("dst")) {
            if (target == null) {
                return DL.getAllDebugState();
            } else {
                boolean isQueue = false;
                if (targetType == null) {
                    throw new Exception("topic or queue not "+
                         "specified with -t [t|q]");
                } else if (targetType.equals("t")) {
                    isQueue = false;
                } else if (targetType.equals("q")) {
                    isQueue = true;
                } else {
                    throw new Exception("Unknown -t argument " + targetType +
                        " expected t or q");
                }
                DestinationUID uid = DestinationUID.getUID(
                      target, isQueue);
                return getDestinationInfo(uid);
            }
        } else if (arg.equals("ses")) {
            if (target == null) {
                return Session.getAllDebugState();
            } else {
                SessionUID uid = new SessionUID(
                      Long.parseLong(target));
                return getSession(uid);
            }
        } else if (arg.equals("prd")) {
            if (target == null) {
                return Producer.getAllDebugState();
            } else {
                ProducerUID uid = new ProducerUID(
                      Long.parseLong(target));
                return getProducerInfo(uid);
            }
        } else if (arg.equals("con")) {
            if (target == null) {
                return Consumer.getAllDebugState();
            } else {
                ConsumerUID uid = new ConsumerUID(
                      Long.parseLong(target));
                return getConInfo(uid);
            }
        } else if (arg.equals("svc")) {
            if (target == null) {
                logger.log(Logger.INFO,"XXX - target of null "
                   + "not implemented for " + arg);
            } else {
                return getSvcInfo(target);
            }
        } else if (arg.equals("db")) {
             return getDBInfo();
        } else if (arg.equals("trans")) {
            if (target == null) {
                return getTransactionInfo(null);
            } else {
                TransactionUID uid = new TransactionUID(
                      Long.parseLong(target));
                return getTransactionInfo(uid);
            }
        } else if (arg.equals("pool")) {
            if (target == null) {
                logger.log(Logger.INFO,"XXX - target of null "
                   + "not implemented for " + arg);
            } else {
                return getThreadPoolInfo(target);
            }
        } else if (arg.equals("threads")) {
            return SupportUtil.getAllStackTracesAsMap();
        } else if (arg.equals("cls")) {
            return getClusterInfo();
        } else if (arg.equals("bkr")) {
            return getBrokerInfo();

        } else if (arg.equals("pkt")) {
	     String full = p.getProperty("full");
             boolean fullDump = false;
             if (full != null && full.equalsIgnoreCase("True"))
                 fullDump = true;
             return getPktInfo(target, targetType, fullDump);
        } 
        logger.log(Logger.INFO,"Unknown dump arg " + arg);
        return null;

    }


    private Hashtable getPktInfo(String target, String type, boolean full)
        throws Exception
    {
        Hashtable ht = new Hashtable();
        if (type == null || type.length() == 0 ||
            type.equals("bkr")) {

            Hashtable dest = new Hashtable();
            Iterator[] itrs = DL.getAllDestinations(null);
            Iterator itr = itrs[0];
            while (itr.hasNext()) {
                Destination d = (Destination)itr.next();
                dest.put(d.getDestinationUID().toString(),
                            d.getDebugMessages(full));
            }
            ht.put("Destinations", dest);
//XXX LKS 1/8/2004
// add entries for sessions, etc
//
        } else if (type.equals("q") || type.equals("t")) {
            boolean isQueue = false;
            if (type.equals("t")) {
                isQueue = false;
            } else if (type.equals("q")) {
                isQueue = true;
            }
            DestinationUID uid = DestinationUID.getUID(target, isQueue);
            Destination[] ds = DL.getDestination(null, uid);
            Destination d = ds[0];
            if (d == null) {
                throw new Exception("Unknown destination " + uid);
            } else {
                ht.putAll(d.getDebugMessages(full));
            }
        } else if (type.equals("con")) {
            if (target == null) {
                throw new Exception("Please specify consumerUID" );
            } else {
                ConsumerUID uid = new ConsumerUID(
                      Long.parseLong(target));
                Consumer c = Consumer.getConsumer(uid);
                if (c == null) {
                    throw new Exception("Unknown consumer " + uid );
                } else {
                    ht.put(uid.toString(), c.getDebugMessages(full));
                }
            }
        } else if (type.equals("cxn")) {
            if (target == null) {
                throw new Exception("Please specify connectionUID" );
            } 
            ConnectionUID uid = new ConnectionUID(
                      Long.parseLong(target));
            IMQConnection cxn = (IMQConnection)
                Globals.getConnectionManager()
                .getConnection(uid);
            if (cxn == null) 
                throw new Exception("Can not find connection " + uid);
            ht.put(target,cxn.getDebugMessages(full)); 
        } else if (type.equals("ses")) {
            ht.put("Dump acks ", target);
            if (target == null) {
                throw new Exception("Please specify SessionUID" );
            } 
            SessionUID uid = new SessionUID(
                      Long.parseLong(target));
            Session sess = Session.getSession(uid);
            if (sess == null) 
                throw new Exception("Can not find session " + uid);
            ht.put(target,sess.getDebugMessages(full)); 
        } else {
            ht.put("Error", "Unknown pkt type " + type);
        }
        return ht;
    }

    private Hashtable getAllCxnInfo(String svc)
        throws Exception
    {
        Service s = null;
        Hashtable debugHash = new Hashtable();
        if (svc != null) {
            s = Globals.getServiceManager().getService(svc);
            debugHash.put("threadPool", getThreadPoolInfo(svc));
            debugHash.put("threads", SupportUtil.getAllStackTraces(""));
        }
        Iterator itr = Globals.getConnectionManager().getConnectionList(s).iterator();
        while (itr.hasNext()) {
            IMQConnection c = (IMQConnection) itr.next();
            debugHash.put(c.getRemoteConnectionString(), c.getDebugState());
        }
        return debugHash;
    }


    private Hashtable getCxnInfo(ConnectionUID uid)
        throws Exception
    {
        IMQConnection cxn = (IMQConnection)
                Globals.getConnectionManager()
                .getConnection(uid);
        if (cxn == null) 
            throw new Exception("Can not find uid " + uid);

        return cxn.getDebugState();
    }

    private Hashtable getConInfo(ConsumerUID uid)
        throws Exception
    {
        Consumer c = Consumer.getConsumer(uid);
        if (c == null)
            throw new Exception("Can not find consumer " + uid);
        return c.getDebugState();
    }

    private Hashtable getSession(SessionUID uid)
        throws Exception
    {
        Session c = Session.getSession(uid);
        if (c == null)
            throw new Exception("Can not find session " + uid);
        return c.getDebugState();
    }

    private Hashtable getSvcInfo(String svcname)
        throws Exception
    {
        if (svcname == null) {
            Hashtable ht = new Hashtable();
            List l = Globals.getServiceManager().getAllActiveServiceNames();
            Iterator itr = l.iterator();
            while (itr.hasNext()) {
                String name = (String)itr.next();
                ht.put(name, getSvcInfo(name));
            }
            return ht;

        }
        Service s = Globals.getServiceManager().getService(
                        svcname);
        if (s ==  null) {
            throw new Exception("unknown service " + svcname);
        }
        return s.getDebugState();
    }

    private Hashtable getProducerInfo(ProducerUID uid)
        throws Exception
    {
        Producer p = (Producer)Producer.getProducer(uid);
        if (p == null)
            throw new Exception("Can not find producer " + uid);
        return p.getDebugState();
    }

    private Hashtable getDBInfo() 
        throws Exception
    {
        return Globals.getStore().getDebugState();
    }

    private Hashtable getTransactionInfo(TransactionUID uid)
        throws Exception
    {
        TransactionList[] tls = DL.getTransactionList(null);
        TransactionList tl = tls[0]; //PART
        Hashtable ht = null;
        if (uid == null) {
            ht = tl.getDebugState();
        } else {
            ht = tl.getDebugState(uid);
        }
        return ht;
    }

    private Hashtable getDestinationInfo(DestinationUID uid)
        throws Exception
    {
        Destination[] ds = DL.getDestination(null, uid);
        Destination d = ds[0];
        if (d == null) {
            throw new Exception("Can not find Destination " + uid);
        }
        return d.getDebugState();
    }

    private Hashtable getThreadPoolInfo(String svcname)
        throws Exception
    {        
        if (svcname == null) {
            Hashtable ht = new Hashtable();
            List l = Globals.getServiceManager().getAllActiveServiceNames();
            Iterator itr = l.iterator();
            while (itr.hasNext()) {
                String name = (String)itr.next();
                ht.put(name, getThreadPoolInfo(name));
            }
            return ht;

        }
        Service s = Globals.getServiceManager().getService(
                        svcname);
        if (s ==  null) {
            throw new Exception("unknown service " + svcname);
        }
        return ((com.sun.messaging.jmq.jmsserver.service.imq.IMQService)s).getPoolDebugState();
    }

    private Hashtable getClusterInfo() {
        Hashtable debugHash = new Hashtable();

        debugHash.put("Cluster Service", Globals.getClusterBroadcast().getAllDebugState());
        return  debugHash;
       
    }

    private Hashtable getBrokerInfo()
        throws Exception
    {
        Hashtable debugHash = new Hashtable();
        try {
            debugHash.put("Destinations", DL.getAllDebugState());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Destinations", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Producers", Producer.getAllDebugState());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Producers", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Consumers", Consumer.getAllDebugState());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Consumers", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Sessions", Session.getAllDebugState());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Sessions", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Connections[jms]", getAllCxnInfo("jms"));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Connections[jms]", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Connections[admin]", getAllCxnInfo("admin"));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Connections[admin]", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Config", getConfig());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Config", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Memory", getMemory());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Memory", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("DB", getDBInfo());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("DB", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Transactions", getTransactionInfo(null));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Transactions", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Cluster", getClusterInfo());
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Cluster", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Service[jms]", getSvcInfo("jms"));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Service[jms]", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("Service[admin]", getSvcInfo("admin"));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("Service[admin]", "Error retrieving state " + ex);
        }
        try {
            debugHash.put("threads", SupportUtil.getAllStackTraces(""));
        } catch (Exception ex) {
            logger.logStack(Logger.INFO,"getBrokerInfo:", ex);
            debugHash.put("threads", "Error retrieving state " + ex);
        }
        return debugHash;
    }

    private Hashtable getMemory() 
        throws Exception
    {
        MemoryManager mm = Globals.getMemManager();
        if (mm != null) {
            return mm.getDebugState();
        }
        return (new Hashtable());
    }


    private Hashtable getConfig() 
        throws Exception
    {
        Hashtable debugHash = new Hashtable();
        Iterator itr = Globals.getConfig().keySet().iterator();
        while (itr.hasNext()) {
            Object key = itr.next();
            debugHash.put(key, Globals.getConfig().get(key).toString());
        }

        return debugHash;
    }


}

