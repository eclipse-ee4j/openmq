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

package com.sun.messaging.visualvm.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.openide.util.Exceptions;

import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 * Abstract superclass representing lists which display the attributes of a single resource
 * 
 * There are two columns, one showing attribute names and one showing the corresponding attribute values
 *
 */
@SuppressWarnings("serial")
public abstract class SingleResourceAttributeList extends MQAttributeList {
    
	public SingleResourceAttributeList(DataViewComponent dvc) {
		super(dvc);
	}

	abstract String getMBeanObjectName();
	
	// array of attribute names
	String[] attributeNames = null;
	
	// map whose key is an attribute name and whose corresponding value is the attribute description 
	Map<String, String> attributeDescriptions = null;
    
	@Override
	public void initTableModel() {
        if (getTableModel() == null) {
            MQResourceListTableModel tm = new MQResourceListTableModel() {

				@Override
                public List loadData() {
                    List list = new ArrayList();
                    AttributeList attrList = null;
                    try {
                    	ObjectName mbeanObjName = new ObjectName(getMBeanObjectName());
	                    if ((getMBeanServerConnection() == null) || (mbeanObjName == null)) {
	                        return null;
	                    }
                        attrList = getMBeanServerConnection().getAttributes(mbeanObjName, getAttributeNames(mbeanObjName));
                        for (Object object : attrList) {
							Attribute attr = (Attribute)object;
							if (!shouldExclude(attr)){
								list.add(attr);
							}
						}
                    } catch (InstanceNotFoundException ex) {
                        // we've probably lost connection to the broker
                        return new ArrayList();
                    } catch (ReflectionException ex) {
                        Exceptions.printStackTrace(ex);
                    } catch (IOException ex) {
                        // we've probably lost connection to the broker
                        return new ArrayList();
                    } catch (MalformedObjectNameException ex) {
						Exceptions.printStackTrace(ex);
					} catch (NullPointerException ex) {
						Exceptions.printStackTrace(ex);
					}

                    return list;
                }

                @Override
                public Object getDataValueAt(List l, int row, int col) {
                    //String value = null;

                    try {
                    	ObjectName mbeanObjName = new ObjectName(getMBeanObjectName());
                    	if ((getMBeanServerConnection() == null) || (mbeanObjName == null)) {
                    		return null;
                    	}                    	
                    } catch (MalformedObjectNameException ex) {
                    	Exceptions.printStackTrace(ex);
					}

                    Attribute attr = (Attribute) l.get(row);

                    if (col == 0) {
                        return attr.getName();
                    } else if (col == 1) {
                        return attr.getValue();
                    } else {
                        return null;
                    }
                }

				@Override
				public void updateCharts() {
					updateRegisteredCharts();
				}
            };
            
            tm.setAttrsInColumn(false);
            setTableModel(tm);
        }
    }   
	    
	/**
	 * Return whether we want to exclude the specified attribute from the list
	 * 
	 * @param attr
	 * @return
	 */
    protected abstract boolean shouldExclude(Attribute attr);

	/**
     * Return the readable attributes of the specified MBean 
     * 
     * If an error occurs then an empty array is returned
     * 
     * @param objectName
     */
	private String[] getAttributeNames(ObjectName mbeanObjName) {
		
		if (attributeNames!=null){
			return attributeNames;
		}
				
		attributeNames = new String[0];
		attributeDescriptions = new HashMap<String, String>();
		try {
			MBeanServerConnection mbsc = getMBeanServerConnection();
			if (mbsc==null){
				return attributeNames;
			}
			MBeanInfo thisMBeanInfo = mbsc.getMBeanInfo(mbeanObjName);
			MBeanAttributeInfo[] thisMBeanAttributeInfo = thisMBeanInfo.getAttributes();
			ArrayList<String> readableAttributeNames = new ArrayList<String>();
			for (int j = 0; j < thisMBeanAttributeInfo.length; j++) {
				if (thisMBeanAttributeInfo[j].isReadable()){
					String thisAttributeName = thisMBeanAttributeInfo[j].getName();    
					String thisAttributeDescription = thisMBeanAttributeInfo[j].getDescription();
					readableAttributeNames.add(thisAttributeName);
					attributeDescriptions.put(thisAttributeName, thisAttributeDescription);
				}                           	
			}
			attributeNames = new String[readableAttributeNames.size()];
			for (int k = 0; k < attributeNames.length; k++) {
				attributeNames[k]=readableAttributeNames.get(k);
			}
		} catch (NullPointerException e) {
			Exceptions.printStackTrace(e);
		} catch (InstanceNotFoundException e) {
            // MBean not found, probably because there is no broker running in this JVM
			return new String[0];
		} catch (IntrospectionException e) {
			Exceptions.printStackTrace(e);
		} catch (ReflectionException e) {
			Exceptions.printStackTrace(e);
		} catch (IOException e) {
			// we can't connect to the broker: broker has probably terminated
			return new String[0];
		}
		return attributeNames;
	}    
	    
    @Override
    public void handleItemQuery(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getDescriptionForAttribute(String attributeName){
    	return attributeDescriptions.get(attributeName);
    }
    
}

