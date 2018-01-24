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
 * @(#)DestinationManagerConfig.java	1.21 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Properties;
import java.util.List;
import java.util.Date;

import javax.management.ObjectName;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.io.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.Version;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.jmsserver.config.ConfigListener;
import com.sun.messaging.jmq.jmsserver.config.PropertyUpdateException;
import com.sun.messaging.jmq.jmsserver.management.util.DestinationUtil;
import com.sun.messaging.jmq.jmsserver.core.Destination;

public class DestinationManagerConfig extends MQMBeanReadWrite 
					implements ConfigListener  {
    private Properties brokerProps = null;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(DestinationAttributes.AUTO_CREATE_QUEUES,
					Boolean.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_AUTO_CREATE_QUEUES),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS,
					Integer.class.getName(),
		        mbr.getString(mbr.I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS,
					Integer.class.getName(),
		        mbr.getString(mbr.I_DST_MGR_ATTR_AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.AUTO_CREATE_TOPICS,
					Boolean.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_AUTO_CREATE_TOPICS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.DMQ_TRUNCATE_BODY,
					Boolean.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_DMQ_TRUNCATE_BODY),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.LOG_DEAD_MSGS,
					Boolean.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_LOG_DEAD_MSGS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_BYTES_PER_MSG,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_MAX_BYTES_PER_MSG),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_NUM_MSGS,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_MAX_NUM_MSGS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_TOTAL_MSG_BYTES,
					Long.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_MAX_TOTAL_MSG_BYTES),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NUM_DESTINATIONS,
					Integer.class.getName(),
		                        mbr.getString(mbr.I_DST_MGR_ATTR_NUM_DESTINATIONS),
					true,
					false,
					false)
			};

    private static MBeanParameterInfo[] createSignature1 = {
	    new MBeanParameterInfo("destinationType", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_TYPE)),
	    new MBeanParameterInfo("destinationName", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_NAME))
		        };

    private static MBeanParameterInfo[] createSignature2 = {
	    new MBeanParameterInfo("destinationType", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_TYPE)),
	    new MBeanParameterInfo("destinationName", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_NAME)),
	    new MBeanParameterInfo("destinationAttrs", AttributeList.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_ATTRS))
		        };

    private static MBeanParameterInfo[] destroySignature = {
	    new MBeanParameterInfo("destinationType", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_TYPE)),
	    new MBeanParameterInfo("destinationName", String.class.getName(), 
		                        mbr.getString(mbr.I_DST_MGR_OP_PARAM_DEST_NAME))
		        };

    private static MBeanParameterInfo[] pauseSignature = {
	    new MBeanParameterInfo("pauseType", String.class.getName(), 
	        mbr.getString(mbr.I_DST_OP_PAUSE_PARAM_PAUSE_TYPE))
		        };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(DestinationOperations.CREATE,
		    mbr.getString(mbr.I_DST_MGR_OP_CREATE),
		    createSignature1,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.CREATE,
		    mbr.getString(mbr.I_DST_MGR_OP_CREATE),
		    createSignature2,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.COMPACT,
		    mbr.getString(mbr.I_DST_MGR_OP_COMPACT),
		    null, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.DESTROY,
		    mbr.getString(mbr.I_DST_MGR_OP_DESTROY),
		    destroySignature, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.GET_DESTINATIONS,
		    mbr.getString(mbr.I_DST_MGR_CFG_OP_GET_DESTINATIONS),
		    null , 
		    ObjectName[].class.getName(),
		    MBeanOperationInfo.INFO),

	    new MBeanOperationInfo(DestinationOperations.PAUSE,
		    mbr.getString(mbr.I_DST_MGR_OP_PAUSE_ALL),
		    null,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.PAUSE,
		    mbr.getString(mbr.I_DST_MGR_OP_PAUSE),
		    pauseSignature,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.RESUME,
		    mbr.getString(mbr.I_DST_MGR_OP_RESUME),
		    null,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION)
		};


    private static String[] attrChangeTypes = {
		    AttributeChangeNotification.ATTRIBUTE_CHANGE
		};

    private static MBeanNotificationInfo[] notifs = {
	    new MBeanNotificationInfo(
		    attrChangeTypes,
		    AttributeChangeNotification.class.getName(),
		    mbr.getString(mbr.I_ATTR_CHANGE_NOTIFICATION)
		    )
		};

    public DestinationManagerConfig()  {
	super();
	initProps();

	com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	cfg.addListener("imq.autocreate.queue", this);
	cfg.addListener("imq.autocreate.queue.maxNumActiveConsumers", this);
	cfg.addListener("imq.autocreate.queue.maxNumBackupConsumers", this);
	cfg.addListener("imq.autocreate.topic", this);
	cfg.addListener("imq.destination.DMQ.truncateBody", this);
	cfg.addListener("imq.destination.logDeadMsgs", this);
	cfg.addListener("imq.message.max_size", this);
	cfg.addListener("imq.system.max_count", this);
	cfg.addListener("imq.system.max_size", this);
    }

    public void setAutoCreateQueues(Boolean b) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.autocreate.queue", b.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.AUTO_CREATE_QUEUES, e);
	}
    }
    public Boolean getAutoCreateQueues() throws MBeanException  {
	String s = brokerProps.getProperty("imq.autocreate.queue");
	Boolean b = null;

	try  {
	    b = Boolean.valueOf(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.AUTO_CREATE_QUEUES,
		e);
	}

	return (b);
    }

    public void setAutoCreateQueueMaxNumActiveConsumers(Integer i) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.autocreate.queue.maxNumActiveConsumers", 
		i.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS, 
		e);
	}
    }
    public Integer getAutoCreateQueueMaxNumActiveConsumers() throws MBeanException  {
	String s = brokerProps.getProperty("imq.autocreate.queue.maxNumActiveConsumers");
	Integer i = null;

	try  {
	    i = new Integer(s);
	} catch (Exception e)  {
	    handleGetterException(DestinationAttributes.AUTO_CREATE_QUEUES, e);
	}

	return (i);
    }

    public void setAutoCreateQueueMaxNumBackupConsumers(Integer i) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.autocreate.queue.maxNumBackupConsumers", 
		i.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS, 
		e);
	}
    }
    public Integer getAutoCreateQueueMaxNumBackupConsumers() throws MBeanException  {
	String s = brokerProps.getProperty("imq.autocreate.queue.maxNumBackupConsumers");
	Integer i = null;

	try  {
	    i = new Integer(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS, 
		e);
	}

	return (i);
    }

    public void setAutoCreateTopics(Boolean b) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.autocreate.topic", b.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.AUTO_CREATE_TOPICS, e);
	}
    }
    public Boolean getAutoCreateTopics() throws MBeanException  {
	String s = brokerProps.getProperty("imq.autocreate.topic");
	Boolean b = null;

	try  {
	    b = Boolean.valueOf(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.AUTO_CREATE_TOPICS,
		e);
	}

	return (b);
    }

    public void setDMQTruncateBody(Boolean b) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.destination.DMQ.truncateBody", b.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.DMQ_TRUNCATE_BODY, e);
	}
    }
    public Boolean getDMQTruncateBody() throws MBeanException  {
	String s = brokerProps.getProperty("imq.destination.DMQ.truncateBody");
	Boolean b = null;

	try  {
	    b = Boolean.valueOf(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.DMQ_TRUNCATE_BODY,
		e);
	}

	return (b);
    }

    public void setLogDeadMsgs(Boolean b) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.destination.logDeadMsgs", b.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.LOG_DEAD_MSGS, e);
	}
    }
    public Boolean getLogDeadMsgs() throws MBeanException  {
	String s = brokerProps.getProperty("imq.destination.logDeadMsgs");
	Boolean b = null;

	try  {
	    b = Boolean.valueOf(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.LOG_DEAD_MSGS,
		e);
	}

	return (b);
    }

    public void setMaxBytesPerMsg(Long l) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.message.max_size", l.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.MAX_BYTES_PER_MSG, e);
	}
    }
    public Long getMaxBytesPerMsg() throws MBeanException  {
	String s = brokerProps.getProperty("imq.message.max_size");
	Long l = null;

	try  {
	    SizeString ss = new SizeString(s);
	    l = Long.valueOf(ss.getBytes());
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.MAX_BYTES_PER_MSG,
		e);
	}

	return (l);
    }

    public void setMaxNumMsgs(Long l) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.system.max_count", l.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.MAX_NUM_MSGS, e);
	}
    }
    public Long getMaxNumMsgs() throws MBeanException  {
	String s = brokerProps.getProperty("imq.system.max_count");
	Long l = null;

	try  {
	    l = new Long(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.MAX_NUM_MSGS,
		e);
	}

	return (l);
    }

    public void setMaxTotalMsgBytes(Long l) throws MBeanException  {
	Properties p = new Properties();
	p.setProperty("imq.system.max_size", l.toString());

	try  {
	    com.sun.messaging.jmq.jmsserver.config.BrokerConfig cfg = Globals.getConfig();
	    cfg.updateProperties(p, true);
	} catch (Exception e)  {
	    handleSetterException(DestinationAttributes.MAX_TOTAL_MSG_BYTES, e);
	}
    }
    public Long getMaxTotalMsgBytes() throws MBeanException  {
	String s = brokerProps.getProperty("imq.system.max_size");
	Long l = null;

	try  {
	    l = new Long(s);
	} catch (Exception e)  {
	    handleGetterException(
		DestinationAttributes.MAX_TOTAL_MSG_BYTES,
		e);
	}

	return (l);
    }

    public Integer getNumDestinations()  {
	List l = DestinationUtil.getVisibleDestinations();

	return (Integer.valueOf(l.size()));
    }

    public void create(String type, String name) throws MBeanException  {
        create(type, name, null);
    }

    public void create(String type, String name, AttributeList attrs) 
						throws MBeanException  {
	DestinationInfo info = 
		DestinationUtil.getDestinationInfoFromAttrs(type, 
				name, attrs);

	try  {
	    DestinationUtil.checkCreateDestinationAttrs(type, attrs);
	    DestinationUtil.createDestination(info);
	} catch (BrokerException e)  {
	    handleOperationException(DestinationOperations.CREATE, 
			e);
	}
    }

    public void compact() throws MBeanException  {
	try  {
	    DestinationUtil.compactAllDestinations();
	} catch (BrokerException e)  {
	    handleOperationException(DestinationOperations.COMPACT, 
			e);
	}
    }

    public void destroy(String type, String name) throws MBeanException  {
	try  {
	    DestinationUtil.checkDestType(type);

	    Destination[] ds = DL.removeDestination(null, name, 
		(type.equals(DestinationType.QUEUE)), "JMX API");
            Destination d = ds[0];
	    
	    if (d == null)  {
                String subError = rb.getString(rb.E_NO_SUCH_DESTINATION, 
				DestinationType.toStringLabel(type), name);
                String errMsg = rb.getString( rb.X_DESTROY_DEST_EXCEPTION, name, subError);
		
		throw new BrokerException(errMsg);
	    }
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.DESTROY, 
			e);
	}
    }

    public ObjectName[] getDestinations() throws MBeanException  {
	List dests = DestinationUtil.getVisibleDestinations();

	if (dests.size() == 0)  {
	    return (null);
	}

	ObjectName destONames[] = new ObjectName [ dests.size() ];

	for (int i =0; i < dests.size(); i ++) {
	    Destination d = (Destination)dests.get(i);

	    try  {
	        ObjectName o = MQObjectName.createDestinationConfig(
				d.isQueue() ? DestinationType.QUEUE : DestinationType.TOPIC,
				d.getDestinationName());

	        destONames[i] = o;
	    } catch (Exception e)  {
		handleOperationException(DestinationOperations.GET_DESTINATIONS, e);
	    }
        }

	return (destONames);
    }

    public void pause() throws MBeanException  {
	pause(DestinationPauseType.ALL);
    }

    public void pause(String pauseType) throws MBeanException  {
	try  {
            DestinationUtil.checkPauseType(pauseType);

	    logger.log(Logger.INFO, rb.I_PAUSING_ALL_DST_WITH_PAUSE_TYPE,
			pauseType);

	    DestinationUtil.pauseAllDestinations(
		DestinationUtil.toInternalPauseType(pauseType));
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.PAUSE, e);
	}
    }

    public void resume()  {
	logger.log(Logger.INFO, "Resuming all destinations");
	DestinationUtil.resumeAllDestinations();
    }

    public String getMBeanName()  {
	return ("DestinationManagerConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_DST_MGR_CFG_DESC));
    }

    public MBeanAttributeInfo[] getMBeanAttributeInfo()  {
	return (attrs);
    }

    public MBeanOperationInfo[] getMBeanOperationInfo()  {
	return (ops);
    }

    public MBeanNotificationInfo[] getMBeanNotificationInfo()  {
	return (notifs);
    }

    public void validate(String name, String value)
            throws PropertyUpdateException {
    }
            
    public boolean update(String name, String value) {
	Object newVal, oldVal;

	/*
        System.err.println("### cl.update called: "
            + name
            + "="
            + value);
	*/

	if (name.equals("imq.autocreate.queue"))  {
	    newVal = Boolean.valueOf(value);

	    try  {
	        oldVal = getAutoCreateQueues();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.AUTO_CREATE_QUEUES, e);
	        oldVal = null;
	    }

            notifyAttrChange(DestinationAttributes.AUTO_CREATE_QUEUES, 
				newVal, oldVal);
	} else if (name.equals("imq.autocreate.queue.maxNumActiveConsumers"))  {
	    try  {
	        newVal = Integer.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS
		    + ": " 
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getAutoCreateQueueMaxNumActiveConsumers();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(
		    DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS, e);
	        oldVal = null;
	    }

            notifyAttrChange(
		DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_ACTIVE_CONSUMERS, 
			newVal, oldVal);
	} else if (name.equals("imq.autocreate.queue.maxNumBackupConsumers"))  {
	    try  {
	        newVal = Integer.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS
		    + ": " 
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getAutoCreateQueueMaxNumBackupConsumers();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(
		    DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS, e);
	        oldVal = null;
	    }

            notifyAttrChange(
		DestinationAttributes.AUTO_CREATE_QUEUE_MAX_NUM_BACKUP_CONSUMERS, 
			newVal, oldVal);
	} else if (name.equals("imq.autocreate.topic"))  {
	    newVal = Boolean.valueOf(value);
	    try  {
	        oldVal = getAutoCreateTopics();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.AUTO_CREATE_TOPICS, e);
	        oldVal = null;
	    }

            notifyAttrChange(DestinationAttributes.AUTO_CREATE_TOPICS, 
				newVal, oldVal);
	} else if (name.equals("imq.destination.DMQ.truncateBody"))  {
	    newVal = Boolean.valueOf(value);
	    try  {
	        oldVal = getDMQTruncateBody();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.DMQ_TRUNCATE_BODY, e);
	        oldVal = null;
	    }

            notifyAttrChange(DestinationAttributes.DMQ_TRUNCATE_BODY, 
				newVal, oldVal);
	} else if (name.equals("imq.destination.logDeadMsgs"))  {
	    newVal = Boolean.valueOf(value);
	    try  {
	        oldVal = getLogDeadMsgs();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.LOG_DEAD_MSGS, e);
	        oldVal = null;
	    }

            notifyAttrChange(DestinationAttributes.LOG_DEAD_MSGS, 
				newVal, oldVal);
	} else if (name.equals("imq.message.max_size"))  {
	    try  {
		SizeString ss = new SizeString(value);
	        newVal = Long.valueOf(ss.getBytes());
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + DestinationAttributes.MAX_BYTES_PER_MSG
		    + ": " 
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getMaxBytesPerMsg();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.MAX_BYTES_PER_MSG, e);
	        oldVal = null;
	    }

            notifyAttrChange(
		DestinationAttributes.MAX_BYTES_PER_MSG, 
			newVal, oldVal);
	} else if (name.equals("imq.system.max_count"))  {
	    try  {
	        newVal = Long.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + DestinationAttributes.MAX_NUM_MSGS
		    + ": " 
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getMaxNumMsgs();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.MAX_NUM_MSGS, e);
	        oldVal = null;
	    }

            notifyAttrChange(
		DestinationAttributes.MAX_NUM_MSGS, 
			newVal, oldVal);
	} else if (name.equals("imq.system.max_size"))  {
	    try  {
	        newVal = Long.valueOf(value);
	    } catch (NumberFormatException nfe)  {
	        logger.log(Logger.ERROR,
		    getMBeanName()
		    + ": cannot parse internal value of "
		    + DestinationAttributes.MAX_TOTAL_MSG_BYTES
		    + ": " 
		    + nfe);
                newVal = null;
	    }

	    try  {
	        oldVal = getMaxTotalMsgBytes();
	    } catch(MBeanException e)  {
                logProblemGettingOldVal(DestinationAttributes.MAX_TOTAL_MSG_BYTES, e);
	        oldVal = null;
	    }

            notifyAttrChange(
		DestinationAttributes.MAX_TOTAL_MSG_BYTES, 
			newVal, oldVal);
        }

        initProps();
        return true;
    }

    public void notifyAttrChange(String attrName, Object newVal, Object oldVal)  {
	sendNotification(
	    new AttributeChangeNotification(this, 
			sequenceNumber++, 
			new Date().getTime(),
	                "Attribute change", attrName, 
                        newVal == null ? "" : newVal.getClass().getName(),
	                oldVal, newVal));
    }

    private void initProps() {
	brokerProps = Globals.getConfig().toProperties();
	Version version = Globals.getVersion();
	brokerProps.putAll(version.getProps());
    }
}
