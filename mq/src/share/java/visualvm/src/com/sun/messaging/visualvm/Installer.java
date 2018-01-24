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

package com.sun.messaging.visualvm;

import com.sun.messaging.visualvm.datasource.MQDataSourceDescriptorProvider;
import com.sun.messaging.visualvm.datasource.MQModelProvider;
import com.sun.messaging.visualvm.dataview.BrokerViewProvider;
import com.sun.messaging.visualvm.dataview.ClusterViewProvider;
import com.sun.messaging.visualvm.dataview.ClusteredBrokerViewProvider;
import com.sun.messaging.visualvm.dataview.ConnectionsViewProvider;
import com.sun.messaging.visualvm.dataview.ConsumersViewProvider;
import com.sun.messaging.visualvm.dataview.DestinationsViewProvider;
import com.sun.messaging.visualvm.dataview.LogViewProvider;
import com.sun.messaging.visualvm.dataview.ProducersViewProvider;
import com.sun.messaging.visualvm.dataview.ServicesViewProvider;
import com.sun.messaging.visualvm.dataview.TransactionsViewProvider;
import com.sun.tools.visualvm.application.Application;
import com.sun.tools.visualvm.application.type.ApplicationTypeFactory;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.DataSourceViewProvider;
import com.sun.tools.visualvm.core.ui.DataSourceViewsManager;
import org.openide.modules.ModuleInstall;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {

   private static OpenMQBrokerApplicationTypeFactory INSTANCE = new OpenMQBrokerApplicationTypeFactory();

    // note that I have had to make some classes public ands their initialize methods public

    @Override
    public void restored() {
        ApplicationTypeFactory.getDefault().registerProvider(INSTANCE);
        
        MQDataSourceDescriptorProvider.initialize();
        MQModelProvider.initialize();
        
        // register the view providers for the sub-applications that we will create under the broker or glassfish process
        BrokerViewProvider.initialize();
        ClusterViewProvider.initialize();
        DestinationsViewProvider.initialize();
        ConnectionsViewProvider.initialize();
        ConsumersViewProvider.initialize();
        ProducersViewProvider.initialize();
        ServicesViewProvider.initialize();
        TransactionsViewProvider.initialize();
        LogViewProvider.initialize();
        ClusteredBrokerViewProvider.initialize();

//        DataSourceViewsManager.sharedInstance().addViewProvider(
//            new ChartViewProvider(), Application.class);
    }

    @Override
    public void uninstalled() {
        ApplicationTypeFactory.getDefault().unregisterProvider(INSTANCE);

        MQDataSourceDescriptorProvider.shutdown();
        MQModelProvider.shutdown();

        BrokerViewProvider.unregister();
        ClusterViewProvider.unregister();
        DestinationsViewProvider.unregister();
        ConnectionsViewProvider.unregister();
        ConsumersViewProvider.unregister();
        ProducersViewProvider.unregister();
        ServicesViewProvider.unregister();
        TransactionsViewProvider.unregister();
        LogViewProvider.unregister();
        ClusteredBrokerViewProvider.unregister();
    }


}
