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

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.openide.util.Exceptions;

import com.sun.messaging.visualvm.chart.ChartPanel;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;

/**
 * This is the abstract superclass for those resource lists for which a single
 * manager MBean, representing a type of resource, owns a set of individual
 * MBeans for each resource
 * 
 * In practice this means:
 * 
 * Service config: The service manager configuration MBean owns multiple service
 * configuration MBeans Connection config: The connection manager configuration
 * MBean owns multiple connection configuration MBeans Destination config: The
 * destination manager configuration MBean owns multiple destination
 * configuration MBeans
 * 
 * (and also the corresponding monitor MBeans)
 * 
 */
@SuppressWarnings("serial")
public abstract class MultipleMBeanResourceList extends MQResourceList {

	Map attributeDescriptions = new HashMap();

	/**
	 * Returns the name of the manager MBean for this resource list. This is the
	 * top-level MBean which owns a set of individual resource MBeans
	 * 
	 * @return
	 */
	protected abstract String getManagerMBeanName();

	/**
	 * Returns the name of the operation which, if applied to the manager MBean,
	 * will return the names of the individual resource MBeans (e.g.
	 * getConnections, getDestinations etc)
	 * 
	 * @return
	 */
	protected abstract String getGetSubMbeanOperationName();

	MultipleMBeanResourceList(DataViewComponent dvc) {
		super(dvc);
	}

	@Override
	public void initTableModel() {
		if (getTableModel() == null) {
			MQResourceListTableModel tm = new MQResourceListTableModel() {

				@Override
				public List loadData() {

					List<RowData> list = new ArrayList<RowData>();
					try {

						ObjectName objName = null;
						ObjectName[] objNames;

						if (getMBeanServerConnection() == null) {
							return new ArrayList();
						}

						objName = new ObjectName(getManagerMBeanName());
						try {
							objNames = (ObjectName[]) getMBeanServerConnection().invoke(objName,
									getGetSubMbeanOperationName(), null, null);
						} catch (InstanceNotFoundException ex) {
							// manager MBean not found, probably because there
							// is no broker running in this JVM
							return new ArrayList();
						}

						if (objNames == null) {
							return new ArrayList();
						}

						// loop through the resource mbeans
						for (int i = 0; i < objNames.length; i++) {
							RowData thisRowData = new RowData(objNames[0]);
							ObjectName thisObjectName = objNames[i];
							// loop through all the possible attributes
							for (int j = 0; j < getCompleteAttrsList().length; j++) {
								Object thisAttributeValue = null;
								try {
									thisAttributeValue = getMBeanServerConnection().getAttribute(thisObjectName,
											getCompleteAttrsList()[j]);
								} catch (InstanceNotFoundException ex) {
									// MBean probably no longer exists
									thisAttributeValue = null;
								}
								thisRowData.put(getCompleteAttrsList()[j], thisAttributeValue);

							}
							list.add(thisRowData);
						}

						// if there is one or more resource MBean, get its
						// MBeanInfo
						if (objNames.length > 0) {
							ObjectName firstObjectName = objNames[0];
							MBeanInfo mbeanInfo;
							MBeanAttributeInfo[] attributeInfo = null;
							try {
								mbeanInfo = getMBeanServerConnection().getMBeanInfo(firstObjectName);
								attributeInfo = mbeanInfo.getAttributes();
							} catch (InstanceNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (IntrospectionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							if (attributeInfo != null && attributeInfo.length > 0) {
								for (int i = 0; i < attributeInfo.length; i++) {
									attributeDescriptions.put(attributeInfo[i].getName(), attributeInfo[i]
											.getDescription());
								}
							}
						}

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
					} catch (AttributeNotFoundException ex) {
						Exceptions.printStackTrace(ex);
					}

					return list;
				}

				@Override
				public Object getDataValueAt(List l, int row, int col) {
					String attributeName = getColumnName(col);
					RowData rowData = (RowData) l.get(row);
					Object attributeValue = rowData.get(attributeName);
					return attributeValue;
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

	public String getTooltipForColumn(int columnIndex) {
		String columnName = getTableModel().getColumnName(columnIndex);
		String description = (String) attributeDescriptions.get(columnName);
		if (description != null) {
			return description;
		} else {
			return "";
		}
	}


}
