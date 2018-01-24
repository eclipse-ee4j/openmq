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

package com.sun.messaging.visualvm.datasource;

import com.sun.tools.visualvm.core.datasource.DataSource;
import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptor;
import com.sun.tools.visualvm.core.datasource.descriptor.DataSourceDescriptorFactory;
import com.sun.tools.visualvm.core.model.AbstractModelProvider;


public class MQDataSourceDescriptorProvider extends AbstractModelProvider<DataSourceDescriptor, DataSource> {

    final private static MQDataSourceDescriptorProvider INSTANCE = new MQDataSourceDescriptorProvider();

    private MQDataSourceDescriptorProvider() {
    }

    @Override
    public DataSourceDescriptor createModelFor(DataSource mqDS) {
        if (mqDS instanceof MQDataSource) {
            return ((MQDataSource) mqDS).getDescriptor();
        }
        return null;
    }

    public static void initialize() {
        DataSourceDescriptorFactory.getDefault().registerProvider(INSTANCE);
    }

    public static void shutdown() {
        DataSourceDescriptorFactory.getDefault().unregisterProvider(INSTANCE);
    }
}
