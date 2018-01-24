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
import java.util.List;
import java.util.MissingResourceException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import com.sun.messaging.jms.management.server.BrokerOperations;
import com.sun.messaging.jms.management.server.MQObjectName;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 * This panel displays a list of property values for the broker
 * 
 * There are two columns, one showing property description and one showing the corresponding property values
 *
 */
@SuppressWarnings("serial")
public class BrokerPropertyList extends MQAttributeList {
    
	public BrokerPropertyList(DataViewComponent dvc) {
		super(dvc);
	}


	private static String[] propertyNames = {
		"imq.varhome",
		"imq.authentication.basic.user_repository",
		"imq.persist.store",
		"imq.cluster.ha",
		"imq.cluster.brokerlist.active"
		};
    
	@Override
	public void initTableModel() {
        if (getTableModel() == null) {
            MQResourceListTableModel tm = new MQResourceListTableModel() {

				@Override
                public List loadData() {
                  
                    MBeanServerConnection mbsc = getMBeanServerConnection();
                    if ((mbsc == null)) {
                        return null;
                    }
                    return constructPropertyList(mbsc);
                }

                @Override
                public Object getDataValueAt(List l, int row, int col) {
                    Object value=null;
                	String[] entry = (String[]) l.get(row);
                    if (col == 0) {
                        value = entry[0];
                    } else if (col == 1) {
                        value = entry[1];
                    }
                    return value;
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
	

	private List constructPropertyList(MBeanServerConnection mbsc) {
		List list = new ArrayList();
		try {
			for (int i = 0; i < propertyNames.length; i++) {
	        	String thisPropertyName = propertyNames[i];
				String thisPropertyValue = getBrokerPropValue(mbsc,thisPropertyName);
				addEntry(list, thisPropertyName, thisPropertyValue);
				// special handling for certain properties
				if (thisPropertyName.equals("imq.authentication.basic.user_repository")){
					if (thisPropertyValue.equals("ldap")){
						String ldapServerPropName = "imq.user_repository.ldap.server";
		            	String ldapServer = getBrokerPropValue(mbsc, ldapServerPropName);
		            	addEntry(list, ldapServerPropName, ldapServer);
					}
				} else if (thisPropertyName.equals("imq.persist.store")){
					if (thisPropertyValue.equals("jdbc")){
						String jdbcVendorPropName =  "imq.persist.jdbc.dbVendor";
		            	String jdbcVendor = getBrokerPropValue(mbsc, jdbcVendorPropName);
		            	addEntry(list, jdbcVendorPropName, jdbcVendor);
		            	if ((jdbcVendor != null) && (!jdbcVendor.equals(""))) {
							String dburlPropName = "imq.persist.jdbc." + jdbcVendor + ".opendburl";
			            	String dburl = getBrokerPropValue(mbsc, dburlPropName);
			            	addEntry(list, dburlPropName, dburl);
		            	}
					}
				}
			}
		} catch (IOException e){
			// we can't connect to the broker: broker has probably terminated
			return new ArrayList();
		}
		return list;
	}


	private void addEntry(List list, String thisPropertyName, String thisPropertyValue) {
		String[] entry = new String[2];
		entry[0]=thisPropertyName;
		entry[1]=thisPropertyValue;
		list.add(entry);
	}

    private String getBrokerPropValue(MBeanServerConnection mbsc, String propName) throws IOException {
        String ret = "";

        try {
            ObjectName bkrCfg = new ObjectName(MQObjectName.BROKER_CONFIG_MBEAN_NAME);

            //Setup parameters and signature for getProperty operation.
            Object params[] = {propName};
            String signature[] = {String.class.getName()};
            
            try {
                ret = (String) mbsc.invoke(bkrCfg, BrokerOperations.GET_PROPERTY,  params, signature);
            } catch (InstanceNotFoundException ex) {
                return "";
            } catch (MBeanException ex) {
                Exceptions.printStackTrace(ex);
            } catch (ReflectionException ex) {
                Exceptions.printStackTrace(ex);
            }

        } catch (MalformedObjectNameException ex) {
            Exceptions.printStackTrace(ex);
        } catch (NullPointerException ex) {
            Exceptions.printStackTrace(ex);
        }

        return (ret);
    }
    
    @Override
    public void handleItemQuery(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


	@Override
	protected String getDescriptionForAttribute(String attributeName) {
		
		 //look up the tooltip for this attribute name
		 String key = this.getClass().getName() + "." + attributeName;
		 String tooltip ="";
		 try {
			 tooltip = NbBundle.getMessage (MQAttributeList.class, key);
		 } catch (MissingResourceException mre){
			 tooltip = "Cannot find text for "+mre.getKey();
			 System.out.println(tooltip);
		 }
		
		return tooltip;
	}


	@Override
	public int getCorner() {
		return DataViewComponent.BOTTOM_LEFT;
	}

}

