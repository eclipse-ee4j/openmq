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

package com.sun.messaging.ums.provider.openmq;

import com.sun.messaging.ums.common.Constants;
import java.util.Properties;
import javax.jms.JMSException;

import javax.management.*;
import javax.management.remote.*;
import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.jms.management.server.*;
import com.sun.messaging.ums.resources.UMSResources;
import com.sun.messaging.ums.service.SecuredSid;
import com.sun.messaging.ums.service.UMSServiceException;
import com.sun.messaging.ums.service.UMSServiceImpl;
import com.sun.messaging.ums.readonly.ReadOnlyRequestMessage;
import com.sun.messaging.ums.dom.util.XMLDataBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.text.DateFormat;


/**
 *
 * @author isa
 */
public class ProviderBrokerInfoService {
    
    //private com.sun.messaging.ConnectionFactory factory = null;
    
    private Logger logger = UMSServiceImpl.logger;
    
    private String brokerAddress = null;
    
    //private String user = null;
    
    //private String password = null;
    
    private AdminConnectionFactory acf;
    
    private boolean shouldAuthenticate = true;
    
    private boolean base64encoding = false;
    
    /**
     * Called by UMS immediately after constructed.
     * 
     * @param props properties used by the connection factory.
     * @throws javax.jms.JMSException
     */
    
    public void init (Properties props) throws JMSException {
        
        // get connection factory
        acf = new AdminConnectionFactory();

        brokerAddress = props.getProperty(Constants.IMQ_BROKER_ADDRESS);

        if (brokerAddress != null) {
            acf.setProperty(AdminConnectionConfiguration.imqAddress, brokerAddress);
        }
            
        String tmp = props.getProperty(Constants.JMS_AUTHENTICATE, Constants.JMS_AUTHENTICATE_DEFAULT_VALUE);
        
        this.shouldAuthenticate = Boolean.parseBoolean(tmp);
        
        tmp = props.getProperty(Constants.BASIC_AUTH_TYPE, Constants.BASIC_AUTH_TYPE_DEFAULT_VALUE);
        
        this.base64encoding = Boolean.parseBoolean(tmp);
        
        String msg = UMSResources.getResources().getKString(UMSResources.UMS_DEST_SERVICE_INIT, brokerAddress, String.valueOf(shouldAuthenticate));
       
        logger.info(msg);
       
        msg = UMSResources.getResources().getKString(UMSResources.UMS_AUTH_BASE64_ENCODE, base64encoding);
        logger.info(msg);
        
        //logger.info ("broker addr=" + brokerAddress + ", shouldAuth=" + this.shouldAuthenticate + ", base64encode=" + this.base64encoding);
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection();
     * 
     * @return
     * @throws javax.jms.JMSException
     */
    private JMXConnector createConnection() throws JMException {
        return acf.createConnection();
    }
    
    /**
     * Same as JMS ConnectionFactory.createConnection(String user, String password);
     * 
     * @param user
     * @param password
     * @return
     * @throws javax.jms.JMSException
     */
    private JMXConnector createConnection(String user, String password) throws JMException, JMSException {
        
        JMXConnector jmxc = null;
        
        if (this.shouldAuthenticate == false) {
            jmxc = acf.createConnection();
        } else {
            
            if (this.base64encoding) {
                
                if (password == null) {
                    throw new UMSServiceException ("Password is required for user=" + user);
                }
                
                password = SecuredSid.decode(password);
            }
            
            jmxc =acf.createConnection(user, password);
        }
        
        return jmxc;
    }
    
    public String getBrokerInfo(ReadOnlyRequestMessage request, String user, String password) {
	//String reqURL = request.getMessageProperty ("requestURL");
	String ret = "hello";
	JMXConnector jmxc = null;


	try  {
            if (user == null) {
                jmxc = createConnection();
            } else {
                jmxc = createConnection(user, password);
            }

	    /*
	     * Get MBeanServer interface.
	     */
	    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

	    // Build XML
	    Document doc = XMLDataBuilder.newUMSDocument();
	    Element root = XMLDataBuilder.getRootElement(doc);

	    Element brokerInfoElement = XMLDataBuilder.createUMSElement(doc, "BrokerInfo");
	    XMLDataBuilder.addChildElement(root, brokerInfoElement);

            generateHeaderElement(request, mbsc, doc, brokerInfoElement);
            generateBodyElement(request, mbsc, doc, brokerInfoElement);

	    //transform xml document to a string
	    ret = XMLDataBuilder.domToString(doc);
	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	} finally  {
            try {
                if (jmxc != null) {
                    jmxc.close();
                }
            } catch (Exception e) {
            }
	}

	return ret;
    }

    public void generateHeaderElement(ReadOnlyRequestMessage request,
				MBeanServerConnection mbsc, 
				Document doc,
				Element parentXMLElement) {
	//String reqURL = request.getMessageProperty ("requestURL");

	try  {
	    Element headerElement = XMLDataBuilder.createUMSElement(doc, "Header");
	    XMLDataBuilder.addChildElement(parentXMLElement, headerElement);

	    Element genDateElement = XMLDataBuilder.createUMSElement(doc, "GeneratedDate");
	    XMLDataBuilder.addChildElement(headerElement, genDateElement);

	    long curTime = System.currentTimeMillis();

	    Element msElement = XMLDataBuilder.createUMSElement(doc, "Milliseconds");
	    XMLDataBuilder.setElementValue(doc, msElement, String.valueOf(curTime));
	    XMLDataBuilder.addChildElement(genDateElement, msElement);

	    Element dateStringElement = XMLDataBuilder.createUMSElement(doc, "DateString");
	    Date        d = new Date(curTime);
	    DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
	    String ds = df.format(d);
	    XMLDataBuilder.setElementValue(doc, dateStringElement, ds);
	    XMLDataBuilder.addChildElement(genDateElement, dateStringElement);


	    /*
	    Element queryStringElement = XMLDataBuilder.createUMSElement(doc, "QueryString");
	    XMLDataBuilder.addChildElement(headerElement, queryStringElement);
	    XMLDataBuilder.setElementValue(doc, queryStringElement, reqURL);
	    */

	    Element reqPropElement = XMLDataBuilder.createUMSElement(doc, "RequestProperties");
	    XMLDataBuilder.addChildElement(headerElement, reqPropElement);

	    /*
	    Map msgProps = request.getMessageProperties();
	    Iterator keyI = msgProps.keySet().iterator();
	    while (keyI.hasNext()) {
		String oneKey =  (String)keyI.next();

		if (oneKey.equals("cmd"))  {
		}

		String vals[] = (String[])msgProps.get(oneKey);

		if (vals != null)  {

	            XMLDataBuilder.setElementAttribute(reqPropElement, 
				oneKey.toString(), vals[0]);
		}
	    }
	    */



	    String propName, val;

	    propName = "cmd";
	    val = request.getMessageProperty (propName);
	    if ((val != null) && (val.length() > 0))  {
	        XMLDataBuilder.setElementAttribute(reqPropElement, propName, val);
	    }

	    propName = "dest-elements";
	    val = request.getMessageProperty (propName);
	    if ((val != null) && (val.length() > 0))  {
	        XMLDataBuilder.setElementAttribute(reqPropElement, propName, val);
	    }

            addBrokerToXML(mbsc, doc, headerElement);

	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }

    public void generateBodyElement(ReadOnlyRequestMessage request,
				MBeanServerConnection mbsc, 
				Document doc,
				Element parentXMLElement) {
	//String reqURL = request.getMessageProperty ("requestURL");
	String cmds = request.getMessageProperty ("cmd");

	try  {
	    Element bodyElement = XMLDataBuilder.createUMSElement(doc, "Body");
	    XMLDataBuilder.addChildElement(parentXMLElement, bodyElement);

	    if ((cmds != null) && (cmds.length() > 0))  {
	        StringTokenizer st = new StringTokenizer(cmds, ",");
		while (st.hasMoreTokens()) {
		    String oneCmd = st.nextToken();

		    if (oneCmd.equals("getDestinations"))  {
                        addDestinationsToXML(request, mbsc, doc, bodyElement);
		    }
		}
	    }
	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }

    public void addBrokerToXML(MBeanServerConnection mbsc, 
				Document doc,
				Element parentXMLElement) {
	try  {
	    Element bkrElement = XMLDataBuilder.createUMSElement(doc, "Broker");
	    XMLDataBuilder.addChildElement(parentXMLElement, bkrElement);

	    /*
	     * Create object name of broker monitor MBean.
	     */
	    ObjectName objName
		= new ObjectName(MQObjectName.BROKER_MONITOR_MBEAN_NAME);

            addMBeanAttrsToXML(mbsc, objName, null, doc, bkrElement);

	    /*
	     * Create object name of broker config MBean to get value of imq.varhome
	     * TBD: Add VarHome attribute to both Broker Config/Monitor MBeans ??
	     */
	    objName = new ObjectName(MQObjectName.BROKER_CONFIG_MBEAN_NAME);

	    Element varHomeElement = XMLDataBuilder.createUMSElement(doc, "VarHome");
	    XMLDataBuilder.addChildElement(bkrElement, varHomeElement);
	    Object params[] = { "imq.varhome" };
	    String signature[] = { String.class.getName() };
	    String varHome = (String)mbsc.invoke(objName, BrokerOperations.GET_PROPERTY, 
			params, signature);
	    XMLDataBuilder.setElementValue(doc, varHomeElement, 
			(varHome == null) ? "" : varHome);
	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }

    public void addDestinationsToXML(ReadOnlyRequestMessage request,
				MBeanServerConnection mbsc, Document doc,
				Element parentXMLElement) {
	try  {

	    Element destsElement = XMLDataBuilder.createUMSElement(doc, "Destinations");
	    XMLDataBuilder.addChildElement(parentXMLElement, destsElement);


	    /*
	     * Create object name of destination monitor mgr MBean.
	     */
	    ObjectName objName
		= new ObjectName(MQObjectName.DESTINATION_MANAGER_MONITOR_MBEAN_NAME);

	    ObjectName destinationObjNames[] = 
                (ObjectName[])mbsc.invoke(objName, DestinationOperations.GET_DESTINATIONS, null, null);
	    MBeanInfo mbInfo = null;
	    String attrsToGet[] = null;
	    String opsToGet[] = null;

	    String propName = "dest-elements";
	    String tmpVal = request.getMessageProperty (propName);
	    ArrayList<String> destElements = null;

	    if ((tmpVal != null) && (tmpVal.length() > 0))  {
		destElements = new ArrayList<String>();

	        StringTokenizer st = new StringTokenizer(tmpVal, ",");
		while (st.hasMoreTokens()) {
		    String oneElement = st.nextToken();
		    destElements.add(oneElement);
		}
	    }

            for (int i = 0; i < destinationObjNames.length; ++i)  {
                ObjectName oneDestObjName = destinationObjNames[i];

		if (attrsToGet == null)  {
		    if (mbInfo == null)  {
		        mbInfo = mbsc.getMBeanInfo(oneDestObjName);
		    }
		    MBeanAttributeInfo mbAttrInfo[] = mbInfo.getAttributes();

		    ArrayList<String> tmpArray = new ArrayList<String>();

		    for (int j = 0; j < mbAttrInfo.length; ++j)  {
			String oneAttrName = mbAttrInfo[j].getName();

			if ((destElements == null) || (destElements.size() == 0))  {
			    tmpArray.add(oneAttrName);
			} else  {
			    if (destElements.contains(oneAttrName))  {
			        tmpArray.add(oneAttrName);
			    }
			}
		    }

		    attrsToGet = new String [ tmpArray.size() ];
		    tmpArray.toArray(attrsToGet);
		}

		if (opsToGet == null)  {
		    if (mbInfo == null)  {
		        mbInfo = mbsc.getMBeanInfo(oneDestObjName);
		    }
		    MBeanOperationInfo mbOpInfo[] = mbInfo.getOperations();

		    ArrayList<String> tmpArray = new ArrayList<String>();

		    for (int j = 0; j < mbOpInfo.length; ++j)  {
			MBeanOperationInfo oneOpInfo = mbOpInfo[j];
			if (oneOpInfo.getImpact() != MBeanOperationInfo.INFO)  {
			    continue;
			}

			if ((oneOpInfo.getSignature() != null) && (oneOpInfo.getSignature().length > 0))  {
			    continue;
			}

			String oneOpName = oneOpInfo.getName();

			if ((destElements == null) || (destElements.size() == 0))  {
			    tmpArray.add(oneOpName);
			} else  {
			    if (destElements.contains(opNameToElementName(oneOpName)))  {
			        tmpArray.add(oneOpName);
			    }
			}
		    }

		    if (tmpArray.size() > 0)  {
		        opsToGet = new String [ tmpArray.size() ];
			tmpArray.toArray(opsToGet);
		    }
		}

	        Element destElement = XMLDataBuilder.createUMSElement(doc, "Destination");
	        XMLDataBuilder.addChildElement(destsElement, destElement);

		addDestNameTypeToDestElement(mbsc, oneDestObjName, destElement);

		if (attrsToGet != null)  {
                    addMBeanAttrsToXML(mbsc, oneDestObjName,
				attrsToGet, doc, destElement);
		}

		if (opsToGet != null)  {
                    addMBeanOpsToXML(mbsc, oneDestObjName,
				opsToGet, doc, destElement);
		}
	    }
	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }

    public void addDestNameTypeToDestElement(MBeanServerConnection mbsc, 
				ObjectName objName,
				Element targetElement) {
	try  {
            if (mbsc == null) {
		return;
            }

	    String attrsToGet[] = {
			DestinationAttributes.NAME,
			DestinationAttributes.TYPE
				};

	    AttributeList attrList = mbsc.getAttributes(objName, attrsToGet);

	    for (int j = 0; j < attrList.size(); ++j)  {
	        Attribute oneAttr = (Attribute)attrList.get(j);
	        String val = "";
	        Object valObj = oneAttr.getValue();

	        if (valObj != null)  {
	            val = oneAttr.getValue().toString();
		}

	        XMLDataBuilder.setElementAttribute(targetElement, oneAttr.getName(), val);
	    }

	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }
    
    public void addMBeanAttrsToXML(MBeanServerConnection mbsc, 
				ObjectName objName,
				String attrsToGet[],
				Document doc,
				Element targetElement) {
	try  {
            if (mbsc == null) {
		return;
            }

	    if (attrsToGet == null)  {
	        MBeanInfo mbInfo = mbsc.getMBeanInfo(objName);
	        MBeanAttributeInfo mbAttrInfo[] = mbInfo.getAttributes();

	        attrsToGet = new String [ mbAttrInfo.length ];

	        for (int j = 0; j < mbAttrInfo.length; ++j)  {
		    attrsToGet[j] = mbAttrInfo[j].getName();
	        }
	    }


	    AttributeList attrList = mbsc.getAttributes(objName, attrsToGet);

	    for (int j = 0; j < attrList.size(); ++j)  {
	        Attribute oneAttr = (Attribute)attrList.get(j);
	        Element oneAttrElement = XMLDataBuilder.createUMSElement(doc, 
						oneAttr.getName());
	        String val = "";
	        Object valObj = oneAttr.getValue();

	        if (valObj != null)  {
	            val = oneAttr.getValue().toString();
		}
	        XMLDataBuilder.setElementValue(doc, oneAttrElement, val);
	        XMLDataBuilder.addChildElement(targetElement, oneAttrElement);
	    }

	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }
    
    public void addMBeanOpsToXML(MBeanServerConnection mbsc, 
				ObjectName objName,
				String opsToGet[],
				Document doc,
				Element targetElement) {
	try  {
            if (mbsc == null) {
		return;
            }

	    if (opsToGet == null)  {
		MBeanInfo mbInfo = mbsc.getMBeanInfo(objName);

	        MBeanOperationInfo mbOpInfo[] = mbInfo.getOperations();

	        ArrayList<String> tmpArray = new ArrayList<String>();

	        for (int j = 0; j < mbOpInfo.length; ++j)  {
		    MBeanOperationInfo oneOpInfo = mbOpInfo[j];
		    int impact = oneOpInfo.getImpact();
		    MBeanParameterInfo sig[] = oneOpInfo.getSignature();

		    if (impact != MBeanOperationInfo.INFO)  {
			continue;
		    }

		    if ((sig != null) && (sig.length > 0))  {
			continue;
		    }

		    tmpArray.add(oneOpInfo.getName());
	        }

	        if (tmpArray.size() > 0)  {
	            opsToGet = new String [ tmpArray.size() ];
		    tmpArray.toArray(opsToGet);
	        }
	    }

	    for (int i = 0; i < opsToGet.length; ++i)  {
		String opName = opsToGet[i];
		String groupElementName, memberElementName;
	        Object opReturn = 
                    mbsc.invoke(objName, opName, null, null);
		
		if (opName.startsWith("get"))  {
		    groupElementName = opName.substring("get".length());
		} else  {
		    groupElementName = opName;
		}

		if (groupElementName.endsWith("s"))  {
		    memberElementName = groupElementName.substring(0, 
				groupElementName.length()-1);
		} else  {
		    memberElementName = groupElementName;
		}

		Element groupElement = XMLDataBuilder.createUMSElement(doc, groupElementName);
	        XMLDataBuilder.addChildElement(targetElement, groupElement);

		if (opReturn != null)  {
		    if (opReturn.getClass().isArray())  {
			Object array[] = (Object[])opReturn;

			for (int j = 0; j < array.length; ++j)  {
			    Object val = array[j];
		            Element memberElement = XMLDataBuilder.createUMSElement(doc, 
							memberElementName);
	                    XMLDataBuilder.setElementValue(doc, memberElement, val.toString());
	                    XMLDataBuilder.addChildElement(groupElement, memberElement);
			}
		    }
		}
	    }
	} catch(Exception e)  {
	    throw new UMSServiceException (e);
	}
    }

    public String opNameToElementName(String opName)  {
	if (opName == null)  {
	    return ("");
	}

        if (opName.startsWith("get"))  {
            return(opName.substring("get".length()));
        } else  {
            return(opName);
        }
    }
    
    
    /**
     * XXX: review
     * @param user
     * @param pass
     * @throws javax.jms.JMSException
     * @throws javax.management.JMException
     * @throws java.io.IOException
     */
    public void authenticate (String user, String pass) throws IOException {
        
        JMXConnector jmxc = null;
        
        try {
        
        if (user == null) {
            this.createConnection();
        } else {
            this.createConnection(user, pass);
        } 
        
        } catch (Exception e) {
            
            throw new RuntimeException (e.getMessage());
        
        } finally {
            
            if (jmxc != null) {
                jmxc.close();
            }
        }
    }
    
    /*
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        
        props.setProperty(Constants.IMQ_BROKER_ADDRESS, "niagra2:7676");
        
        ProviderDestinationService ds = new ProviderDestinationService();
        ds.init(props);
        
        //ds.listDestinations(null, null);
        
       ds.queryDestination("simpleQ", "queue", null, null);
    }
    */
    
    
}
