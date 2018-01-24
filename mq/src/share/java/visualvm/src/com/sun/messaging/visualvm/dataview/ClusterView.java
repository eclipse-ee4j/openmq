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

package com.sun.messaging.visualvm.dataview;

import java.awt.Image;

import javax.management.MBeanServerConnection;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;

import org.openide.util.ImageUtilities;

import com.sun.messaging.visualvm.datasource.ClusterAccessUtils;
import com.sun.messaging.visualvm.datasource.ClusterDataSource;
import com.sun.messaging.visualvm.ui.ClusterMonitorBrokerList;
import com.sun.messaging.visualvm.ui.ClusterMonitorList;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;
import com.sun.tools.visualvm.tools.jmx.JmxModel;
import com.sun.tools.visualvm.tools.jmx.JmxModelFactory;

public class ClusterView extends DataSourceView {

    private DataViewComponent dvc;
    //Make sure there is an image at this location in your project:
    private static final Image NODE_ICON = ImageUtilities.loadImage(
            "com/sun/messaging/visualvm/ui/resources/consumers.gif", true);

    public ClusterView(ClusterDataSource cds) {
        // isCloseable=true
        super(cds, "Cluster", NODE_ICON, 60, true);
    }

    @Override
	protected DataViewComponent createComponent() {
        ClusterDataSource ds = (ClusterDataSource)getDataSource();
        Application app = ds.getApplication();

        MBeanServerConnection mbsc = ClusterAccessUtils.getMBeanServerConnection(app);

        // create the data area for the master view
        JEditorPane generalDataArea = new JEditorPane();
        generalDataArea.setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 8));
        
        // create the master view
        DataViewComponent.MasterView masterView = new DataViewComponent.MasterView("Cluster", null, generalDataArea);
        
        // create the configuration for the master view
        boolean isMasterAreaResizable = false;
        DataViewComponent.MasterViewConfiguration masterConfiguration = new DataViewComponent.MasterViewConfiguration(isMasterAreaResizable);
           
        // Add the master view and configuration view to the component:
        dvc = new DataViewComponent(masterView, masterConfiguration);
        
        // 1. create the JPanel for the "Cluster manager (monitor)" detail view
    	ClusterMonitorList clusterMonitorList = new ClusterMonitorList(dvc);
    	clusterMonitorList.setMBeanServerConnection(mbsc);
    	
        // add the "Cluster manager (monitor)" detail view to the data view component representing the master view
    	// DataViewComponent.TOP_LEFT
    	dvc.addDetailsView(new DataViewComponent.DetailsView("Cluster manager (monitor)", null, 10, clusterMonitorList, null), clusterMonitorList.getCorner());  
    	        
        // 2. create the JPanel for the "Brokers in cluster (monitor)" detail view generated from the cluster monitor
        ClusterMonitorBrokerList clusterMonitorBrokerList = new ClusterMonitorBrokerList(dvc);
        clusterMonitorBrokerList.setMBeanServerConnection(mbsc);

        // add the "Brokers in cluster (monitor)" detail view to the data view component representing the master view  
        // DataViewComponent.BOTTOM_RIGHT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Brokers in cluster (monitor)", null, 10, clusterMonitorBrokerList, null), clusterMonitorBrokerList.getCorner());	         
        
        return dvc;

    }
}
