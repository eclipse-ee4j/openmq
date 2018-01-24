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
 * @(#)DestinationConfig.java	1.26 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.mbeans;

import java.util.Date;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanException;
import javax.management.InvalidAttributeValueException;

import com.sun.messaging.jms.management.server.*;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.Destination;
import com.sun.messaging.jmq.jmsserver.management.util.DestinationUtil;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.ClusterDeliveryPolicy;
import com.sun.messaging.jmq.util.SizeString;
import com.sun.messaging.jmq.util.admin.DestinationInfo;
import com.sun.messaging.jmq.util.log.Logger;

public class DestinationConfig extends MQMBeanReadWrite  {
    private Destination d = null;

    private static MBeanAttributeInfo[] attrs = {
	    new MBeanAttributeInfo(DestinationAttributes.CONSUMER_FLOW_LIMIT,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_CONSUMER_FLOW_LIMIT),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.LOCAL_ONLY,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_LOCAL_ONLY),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.LIMIT_BEHAVIOR,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_LIMIT_BEHAVIOR),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.LOCAL_DELIVERY_PREFERRED,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_LOCAL_DELIVERY_PREFERRED),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_BYTES_PER_MSG,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_BYTES_PER_MSG),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_NUM_ACTIVE_CONSUMERS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_NUM_BACKUP_CONSUMERS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_NUM_MSGS,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_NUM_MSGS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_NUM_PRODUCERS,
					Integer.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_NUM_PRODUCERS),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.MAX_TOTAL_MSG_BYTES,
					Long.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_MAX_TOTAL_MSG_BYTES),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.NAME,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_NAME),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.TYPE,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_TYPE),
					true,
					false,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.USE_DMQ,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_USE_DMQ),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.VALIDATE_XML_SCHEMA_ENABLED,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_VALIDATE_XML_SCHEMA_ENABLED),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.XML_SCHEMA_URI_LIST,
					String.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_XML_SCHEMA_URI_LIST),
					true,
					true,
					false),

	    new MBeanAttributeInfo(DestinationAttributes.RELOAD_XML_SCHEMA_ON_FAILURE,
					Boolean.class.getName(),
					mbr.getString(mbr.I_DST_ATTR_RELOAD_XML_SCHEMA_ON_FAILURE),
					true,
					true,
					false)
			};

    private static MBeanParameterInfo[] pauseSignature = {
	    new MBeanParameterInfo("pauseType", String.class.getName(), 
		  mbr.getString(mbr.I_DST_OP_PAUSE_PARAM_PAUSE_TYPE))
		        };

    private static MBeanOperationInfo[] ops = {
	    new MBeanOperationInfo(DestinationOperations.COMPACT,
		    mbr.getString(mbr.I_DST_OP_COMPACT),
		    null, 
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.PAUSE,
		    mbr.getString(mbr.I_DST_OP_PAUSE_ALL),
		    null,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.PAUSE,
		    mbr.getString(mbr.I_DST_OP_PAUSE),
		    pauseSignature,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.PURGE,
		    mbr.getString(mbr.I_DST_OP_PURGE),
		    null,
		    Void.TYPE.getName(),
		    MBeanOperationInfo.ACTION),

	    new MBeanOperationInfo(DestinationOperations.RESUME,
		    mbr.getString(mbr.I_DST_OP_RESUME),
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

    public DestinationConfig(Destination dest)  {
	super();
	this.d = dest;
    }

    public void setConsumerFlowLimit(Long l) throws MBeanException  {
	try  {
            checkLongNegOneAndUp(l, DestinationAttributes.CONSUMER_FLOW_LIMIT);

	    d.setMaxPrefetch(l.intValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.CONSUMER_FLOW_LIMIT, e);
	}
    }
    public Long getConsumerFlowLimit()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (Long.valueOf(di.maxPrefetch));
    }

    public Boolean getLocalOnly()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di.isDestinationLocal())  {
	    return (Boolean.TRUE);
	}
	return (Boolean.FALSE);
    }
    public Boolean isLocalOnly()  {
	return (getLocalOnly());
    }


    public void setLimitBehavior(String s) throws MBeanException  {
	try  {
	    checkLimitBehavior(s);
	    d.setLimitBehavior(DestinationUtil.toInternalDestLimitBehavior(s));
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.LIMIT_BEHAVIOR, e);
	}
    }
    public String getLimitBehavior()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (DestinationUtil.toExternalDestLimitBehavior(di.destLimitBehavior));
    }

    private void checkLimitBehavior(String s) throws InvalidAttributeValueException  {
	if (s.equals(DestinationLimitBehavior.FLOW_CONTROL) ||
	    s.equals(DestinationLimitBehavior.REMOVE_OLDEST) ||
	    s.equals(DestinationLimitBehavior.REJECT_NEWEST) ||
	    s.equals(DestinationLimitBehavior.REMOVE_LOW_PRIORITY))  {

	    return;
	}

	throw new InvalidAttributeValueException(
	    "Invalid value for Destination LimitBehavior specified: " + s);
    }

    public void setLocalDeliveryPreferred(Boolean b) throws MBeanException  {
	try  {
	    int cdp;

	    if (b.booleanValue())  {
		cdp = ClusterDeliveryPolicy.LOCAL_PREFERRED;
	    } else  {
		cdp = ClusterDeliveryPolicy.DISTRIBUTED;
	    }

	    d.setClusterDeliveryPolicy(cdp);
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.LOCAL_DELIVERY_PREFERRED, e);
	}
    }
    public Boolean getLocalDeliveryPreferred()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	if (di.destCDP == ClusterDeliveryPolicy.LOCAL_PREFERRED)  {
	    return (Boolean.TRUE);
	}
	return (Boolean.FALSE);
    }

    public void setMaxBytesPerMsg(Long l) throws MBeanException  {
	try  {
            checkLongNegOneAndUp(l, DestinationAttributes.MAX_BYTES_PER_MSG);

	    SizeString ss = new SizeString();
	    ss.setBytes(l.longValue());
	    d.setMaxByteSize(ss);
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_BYTES_PER_MSG, e);
	}
    }
    public Long getMaxBytesPerMsg()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (checkLongUnlimitedZero(Long.valueOf(di.maxMessageSize)));
    }

    public void setMaxNumActiveConsumers(Integer i) throws MBeanException  {
	try  {
            checkIntNegOneAndUp(i, DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS);

	    d.setMaxActiveConsumers(i.intValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_NUM_ACTIVE_CONSUMERS, e);
	}
    }
    public Integer getMaxNumActiveConsumers()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (Integer.valueOf(di.maxActiveConsumers));
    }

    public void setMaxNumBackupConsumers(Integer i) throws MBeanException  {
	try  {
            checkIntNegOneAndUp(i, DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS);

	    d.setMaxFailoverConsumers(i.intValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_NUM_BACKUP_CONSUMERS, e);
	}
    }
    public Integer getMaxNumBackupConsumers()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (Integer.valueOf(di.maxFailoverConsumers));
    }


    public void setMaxNumMsgs(Long l) throws MBeanException  {
	try  {
            checkLongNegOneAndUp(l, DestinationAttributes.MAX_NUM_MSGS);

	    d.setCapacity(l.intValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_NUM_MSGS, e);
	}
    }
    public Long getMaxNumMsgs()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (checkLongUnlimitedZero(Long.valueOf(di.maxMessages)));
    }

    public void setMaxNumProducers(Integer i) throws MBeanException  {
	try  {
            checkIntNegOneAndUp(i, DestinationAttributes.MAX_NUM_PRODUCERS);

	    d.setMaxProducers(i.intValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_NUM_PRODUCERS, e);
	}
    }
    public Integer getMaxNumProducers()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (Integer.valueOf(di.maxProducers));
    }

    public void setMaxTotalMsgBytes(Long l) throws MBeanException  {
	try  {
            checkLongNegOneAndUp(l, DestinationAttributes.MAX_TOTAL_MSG_BYTES);

	    SizeString ss = new SizeString();
	    ss.setBytes(l.longValue());

	    d.setByteCapacity(ss);
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.MAX_TOTAL_MSG_BYTES, e);
	}
    }
    public Long getMaxTotalMsgBytes()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (checkLongUnlimitedZero(Long.valueOf(di.maxMessageBytes)));
    }

    public String getName()  {
	return (d.getDestinationName());
    }

    public String getType()  {
	return (d.isQueue() ? 
	    DestinationType.QUEUE : DestinationType.TOPIC);
    }

    public void setUseDMQ(Boolean b) throws MBeanException  {
	try  {
	    d.setUseDMQ(b.booleanValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.USE_DMQ, e);
	}
    }
    public Boolean getUseDMQ()  {
	DestinationInfo di = DestinationUtil.getDestinationInfo(d);

	return (Boolean.valueOf(di.useDMQ));
    }

    public void setValidateXMLSchemaEnabled(Boolean b) throws MBeanException  {
	try  {
	    d.setValidateXMLSchemaEnabled(b.booleanValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.VALIDATE_XML_SCHEMA_ENABLED, e);
	}
    }
    public Boolean getValidateXMLSchemaEnabled()  {
	return (Boolean.valueOf(d.validateXMLSchemaEnabled()));
    }

    public void setXMLSchemaURIList(String s) throws MBeanException  {
	try  {
	    d.setXMLSchemaUriList(s);
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.XML_SCHEMA_URI_LIST, e);
	}
    }
    public String getXMLSchemaURIList()  {
	return (d.getXMLSchemaUriList());
    }


    public void setReloadXMLSchemaOnFailure(Boolean b) throws MBeanException  {
	try  {
	    d.setReloadXMLSchemaOnFailure(b.booleanValue());
	    d.update();
	} catch (Exception e)  {
	    handleSetterException(
		DestinationAttributes.RELOAD_XML_SCHEMA_ON_FAILURE, e);
	}
    }
    public Boolean getReloadXMLSchemaOnFailure()  {
	return (Boolean.valueOf(d.reloadXMLSchemaOnFailure()));
    }

    public void compact() throws MBeanException {
	try  {
            if (!d.isPaused()) {
                String msg = rb.getString(rb.E_DESTINATION_NOT_PAUSED);
                String errMsg = rb.getString(rb.X_COMPACT_DST_EXCEPTION,
                            getName(), msg);

		throw (new BrokerException(errMsg));
            }

            d.compact();
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.COMPACT, e);
	}
    }

    public void pause() throws MBeanException  {
	pause(DestinationPauseType.ALL);
    }

    public void pause(String pauseType) throws MBeanException  {
	try  {
            DestinationUtil.checkPauseType(pauseType);

	    logger.log(Logger.INFO, rb.I_PAUSING_DST_WITH_PAUSE_TYPE,
			getType() + ":" + getName(), 
			pauseType);

	    int pauseVal = DestinationUtil.toInternalPauseType(pauseType);
	    d.pauseDestination(pauseVal);
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.PAUSE, e);
	}
    }

    public void purge() throws MBeanException  {
	try  {
            String criteria_str = Globals.getBrokerResources().getKString(
				   rb.I_ALL_PURGE_CRITERIA);
	    logger.log(Logger.INFO, rb.I_PURGING_DESTINATION, getName(),
				criteria_str);

	    d.purgeDestination();
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.PURGE, e);
	}
    }

    public void resume() throws MBeanException  {
	try  {
	    logger.log(Logger.INFO, rb.I_RESUMING_DST, getName());

	    d.resumeDestination();
	} catch (Exception e)  {
	    handleOperationException(DestinationOperations.RESUME, e);
	}
    }

    public String getMBeanName()  {
	return ("DestinationConfig");
    }

    public String getMBeanDescription()  {
	return (mbr.getString(mbr.I_DST_CFG_DESC));
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

    public void notifyDestinationAttrUpdated(int attr, Object oldVal, Object newVal)  {
	String attrName;

	attrName = DestinationUtil.getAttrNameFromDestinationInfoAttr(attr);

	if (attrName != null)  {
            notifyAttrChange(attrName, newVal, oldVal);
	} else  {
            logger.log(Logger.WARNING, 
			getMBeanDescription()
			+
			": Unknown attribute updated in destination "
			+ d.toString());
	}
    }

    /*
     * There are 3 attributes that represent 'unlimited' as the value
     * zero (0) internally:
     *
     *	MaxNumMsgs
     *  MaxTotalMsgBytes
     *  MaxBytesPerMsg
     *
     * This internal value is always zero even if we set it to -1.
     * For these attributes (internally, at least), setting them to
     * 0 or -1 is equivalent i.e. both mean unlimited.
     * 
     * To be consistent, it is necessary to intercept get requests
     * on these attributes and return -1 whenever we see 0.
     * This is done similarly in imqcmd.
     *
     * It is important to no blatantly use this method everywhere
     * because for some attributes, 0 does not mean unlimited.
     */
    private Long checkLongUnlimitedZero(Long l)  {
	if (l.longValue() == 0)  {
	    return (Long.valueOf(-1));
	}

	return (l);
    }

    private void checkLongNegOneAndUp(Long l, String attrName) 
				throws InvalidAttributeValueException  {
	if (l.longValue() >= -1)  {
	    return;
	}

	throw new InvalidAttributeValueException(
	    "Invalid value for attribute "
		+ attrName
		+ ": "
		+ l
		+ ". Please use a positive number or -1");
    }

    private void checkIntNegOneAndUp(Integer i, String attrName) 
				throws InvalidAttributeValueException  {
	if (i.intValue() >= -1)  {
	    return;
	}

	throw new InvalidAttributeValueException(
	    "Invalid value for attribute "
		+ attrName
		+ ": "
		+ i
		+ ". Please use a positive number or -1");
    }


    private void notifyAttrChange(String attrName, Object newVal, Object oldVal)  {
	sendNotification(
	    new AttributeChangeNotification(this, sequenceNumber++, new Date().getTime(),
	        "Attribute change", attrName, newVal.getClass().getName(),
	        oldVal, newVal));
    }
}
