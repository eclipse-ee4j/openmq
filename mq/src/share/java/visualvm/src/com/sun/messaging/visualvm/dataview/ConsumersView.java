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
import com.sun.messaging.visualvm.datasource.ConsumersDataSource;
import com.sun.messaging.visualvm.datasource.ServicesDataSource;
import com.sun.messaging.visualvm.ui.ClusterMonitorList;
import com.sun.messaging.visualvm.ui.ConsumerMonitorConsumerList;
import com.sun.messaging.visualvm.ui.ConsumerMonitorList;
import com.sun.messaging.visualvm.ui.ServiceConfigServiceList;
import com.sun.messaging.visualvm.ui.ServiceMonitorServiceList;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;
import com.sun.tools.visualvm.tools.jmx.JmxModel;
import com.sun.tools.visualvm.tools.jmx.JmxModelFactory;

public class ConsumersView extends DataSourceView {

    private DataViewComponent dvc;
    //Make sure there is an image at this location in your project:
    private static final Image NODE_ICON = ImageUtilities.loadImage(
            "com/sun/messaging/visualvm/ui/resources/consumers.gif", true);

    public ConsumersView(ConsumersDataSource dds) {
        // isCloseable=true
        super(dds, "Consumers", NODE_ICON, 60, true);
    }

    @Override
	protected DataViewComponent createComponent() {
        ConsumersDataSource ds = (ConsumersDataSource)getDataSource();
        Application app = ds.getApplication();

        MBeanServerConnection mbsc = ClusterAccessUtils.getMBeanServerConnection(app);

        // create the data area for the master view
        JEditorPane generalDataArea = new JEditorPane();
        generalDataArea.setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 8));
        
        // create the master view
        DataViewComponent.MasterView masterView = new DataViewComponent.MasterView("Consumers", null, generalDataArea);
        
        // create the configuration for the master view
        boolean isMasterAreaResizable = false;
        DataViewComponent.MasterViewConfiguration masterConfiguration = new DataViewComponent.MasterViewConfiguration(isMasterAreaResizable);
           
        // Add the master view and configuration view to the component:
        dvc = new DataViewComponent(masterView, masterConfiguration);
        
        // 1. create the JPanel for the "Consumer manager (monitor)" view
        ConsumerMonitorList consumerMonitorList = new ConsumerMonitorList(dvc);
        consumerMonitorList.setMBeanServerConnection(mbsc);
    	
        // add the "Consumer manager (monitor)" detail view to the data view component representing the master view  AND HIDE IT
        // DataViewComponent.TOP_LEFT
    	dvc.addDetailsView(new DataViewComponent.DetailsView("Consumer manager (monitor)", null, 10, consumerMonitorList, null), consumerMonitorList.getCorner());     
    	dvc.hideDetailsArea(DataViewComponent.TOP_LEFT);
 
        // 2. create the JPanel for the "consumer monitor consumer list" detail view
        ConsumerMonitorConsumerList consumerMonitorConsumerList = new ConsumerMonitorConsumerList(dvc);
        consumerMonitorConsumerList.setMBeanServerConnection(mbsc);

        // add the "Consumer list (monitor)" detail view to the data view component representing the master view  
        // DataViewComponent.BOTTOM_LEFT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Consumer list (monitor)", null, 10, consumerMonitorConsumerList, null), consumerMonitorConsumerList.getCorner());	   
        
        return dvc;

    }
}
