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
import com.sun.messaging.visualvm.datasource.ServicesDataSource;
import com.sun.messaging.visualvm.ui.ServiceConfigList;
import com.sun.messaging.visualvm.ui.ServiceConfigServiceList;
import com.sun.messaging.visualvm.ui.ServiceMonitorList;
import com.sun.messaging.visualvm.ui.ServiceMonitorServiceList;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.components.DataViewComponent;
import com.sun.tools.visualvm.tools.jmx.JmxModel;
import com.sun.tools.visualvm.tools.jmx.JmxModelFactory;

public class ServicesView extends DataSourceView {

    private DataViewComponent dvc;
    //Make sure there is an image at this location in your project:
    private static final Image NODE_ICON = ImageUtilities.loadImage(
            "com/sun/messaging/visualvm/ui/resources/services.gif", true);

    public ServicesView(ServicesDataSource dds) {
        // isCloseable=true
        super(dds, "Services", NODE_ICON, 60, true);
    }

    @Override
	protected DataViewComponent createComponent() {
        ServicesDataSource ds = (ServicesDataSource)getDataSource();
        Application app = ds.getApplication();

        MBeanServerConnection mbsc = ClusterAccessUtils.getMBeanServerConnection(app);

        // create the data area for the master view
        JEditorPane generalDataArea = new JEditorPane();
        generalDataArea.setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 8));
        
        // create the master view
        DataViewComponent.MasterView masterView = new DataViewComponent.MasterView("Services", null, generalDataArea);
        
        // create the configuration for the master view
        boolean isMasterAreaResizable = false;
        DataViewComponent.MasterViewConfiguration masterConfiguration = new DataViewComponent.MasterViewConfiguration(isMasterAreaResizable);
           
        // Add the master view and configuration view to the component:
        dvc = new DataViewComponent(masterView, masterConfiguration);
 
        // 1. create the JPanel for the "service monitor" detail view
        ServiceMonitorList serviceMonitorList = new ServiceMonitorList(dvc);
        serviceMonitorList.setMBeanServerConnection(mbsc);

        // add the "service monitor" detail view to the data view component representing the master view  
        //DataViewComponent.TOP_LEFT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Service manager (monitor)", null, 10, serviceMonitorList, null), serviceMonitorList.getCorner());	
 
        // 2. create the JPanel for the "service config" detail view
        ServiceConfigList serviceConfigList = new ServiceConfigList(dvc);
        serviceConfigList.setMBeanServerConnection(mbsc);

        // add the "service config" detail view to the data view component representing the master view  
        // DataViewComponent.TOP_RIGHT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Service manager (config)", null, 10, serviceConfigList, null), serviceConfigList.getCorner());
        
        // 3. create the JPanel for the "service list (monitor)" detail view
        ServiceMonitorServiceList serviceMonitorServiceList = new ServiceMonitorServiceList(dvc);
        serviceMonitorServiceList.setMBeanServerConnection(mbsc);

        // add the "service list (monitor)" detail view to the data view component representing the master view  
        // DataViewComponent.BOTTOM_LEFT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Service list (monitor)", null, 10, serviceMonitorServiceList, null), serviceMonitorServiceList.getCorner());	
 
        // 4. create the JPanel for the "service list (config)" detail view
        ServiceConfigServiceList serviceConfigServiceList = new ServiceConfigServiceList(dvc);
        serviceConfigServiceList.setMBeanServerConnection(mbsc);

        // add the "service list (config)" detail view to the data view component representing the master view  
        // DataViewComponent.BOTTOM_RIGHT
        dvc.addDetailsView(new DataViewComponent.DetailsView("Service list (config)", null, 10, serviceConfigServiceList, null), serviceConfigServiceList.getCorner() );

     
        
        return dvc;

    }
}
