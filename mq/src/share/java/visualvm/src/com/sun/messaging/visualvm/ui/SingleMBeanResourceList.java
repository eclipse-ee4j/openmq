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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import com.sun.messaging.jms.management.server.ConsumerInfo;
import com.sun.messaging.jms.management.server.TransactionInfo;
import com.sun.messaging.visualvm.chart.ChartPanel;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 * This is the abstract superclass for those resource lists 
 * for which a single manager MBean, holds information about all the individual resources in the list
 * (i.e. there are not individual MBeans for each resource)
 * 
 * In practice this means:
 * 
 * The producer manager monitor MBean  
 * The consumer manager monitor MBean
 * The transaction manager monitor MBean
 *
 */
@SuppressWarnings("serial")
public abstract class SingleMBeanResourceList extends MQResourceList {
	
    public SingleMBeanResourceList(DataViewComponent dvc) {
		super(dvc);
	}

	/**
     * Returns the name of the manager MBean for this resource list.
     * @return 
     */
    protected abstract String getManagerMBeanName();
    
    /**
     * Returns the name of the operation which, if applied to the manager MBean,
     * will return a CompositeInfo[] containing information about the individual resources
     * (e.g. getConsumerInfo, getProducerInfo, getTransactionInfo etc)
     * @return
     */
    protected abstract String getGetSubitemInfoOperationName();
    
    /**
     * Return the name of the lookup key in the CompositeInfo[] that can be used as a key
     * @return
     */
    protected abstract String getSubitemIdName();

	@Override
	public void initTableModel() {
        if (getTableModel() == null) {
            MQResourceListTableModel tm = new MQResourceListTableModel() {

                @Override
                public List loadData() {
                    List<Map.Entry<String, CompositeData>> list = null;

                    try {
                        ObjectName mgrMonitorObjName = new ObjectName(getManagerMBeanName());

                        CompositeData cds[]=null;
                        try {
                            cds = (CompositeData[]) getMBeanServerConnection().invoke(mgrMonitorObjName,
                            		getGetSubitemInfoOperationName(), null, null);
                        } catch (InstanceNotFoundException ex) {
                            // manager monitor MBean not found, probably because there is no broker running in this JVM
                             return new ArrayList();
                        }
                        if (cds == null) {
                        	return new ArrayList();
                        }

                        // build a map with key = the defined resource ID and value = CompositeData
                        SortedMap<String, CompositeData> map = new TreeMap<String, CompositeData>();
                         
                        for (int i = 0; i < cds.length; i++) {
                            if (cds[i] != null) {
                                String id = (String) cds[i].get(getSubitemIdName());
                                map.put(id, cds[i]);
                            }
                        }
                        
                        Set<Map.Entry<String, CompositeData>> set = map.entrySet();
                        list = new ArrayList<Map.Entry<String, CompositeData>>(set);
                    } catch (MBeanException ex) {
                        Exceptions.printStackTrace(ex);
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
                    if (l == null) {
                        return (null);
                    }

                    Map.Entry me = (Map.Entry) l.get(row);
                    CompositeData cd = (CompositeData) me.getValue();
                    String attrName = getColumnName(col);
                    Object v=null;
                    Object obj = null;

                    obj = cd.get(attrName);
                    if (obj != null) {
                        if ((attrName.equals(ConsumerInfo.LAST_ACK_TIME))|attrName.equals(TransactionInfo.CREATION_TIME)) {
                            Long ackTime = (Long) obj;
                            if (ackTime.longValue() == 0) {
                                v = "0";
                            } else {
                                v = checkNullAndPrintTimestamp(ackTime);
                            }
                        } else if (obj instanceof String[]) {
                        	// String array
                            String sa[] = (String[]) obj;

                            v = "";

                            for (int i = 0; i < sa.length; ++i) {
                                v = v + sa[i];

                                if (i < (sa.length - 1)) {
                                    v = v + ",";
                                }
                            }
                        } else {
                            v = obj;
                        }
                    }
                    return (v);
                }

				@Override
				public void updateCharts() {
					updateRegisteredCharts();
				}
            };
            tm.setAttributes(getinitialDisplayedAttrsList());
            setTableModel(tm);
        }
    }
	
    protected static String checkNullAndPrintTimestamp(Long timestamp) {
        if (timestamp != null) {
            String ts;
            Date d = new Date(timestamp.longValue());
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            ts = df.format(d);
            return (ts);
        } else {
            return ("");
        }
    }
    
    public String getTooltipForColumn(int columnIndex){
    	String columnName = getTableModel().getColumnName(columnIndex);
    	String key = this.getClass().getName() + "." + columnName;

    	String tooltip ="";
    	try {
    		tooltip = NbBundle.getMessage (SingleMBeanResourceList.class, key);
    	} catch (MissingResourceException mre){
    		tooltip = "Cannot find text for "+mre.getKey();
        	System.out.println(tooltip);
    	}

    	if (tooltip!=null){
    		return tooltip;
    	} else {
    		return "";
    	}
    }
        
}

