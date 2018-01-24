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

import com.sun.messaging.visualvm.datasource.TransactionsDataSource;
import com.sun.tools.visualvm.core.ui.DataSourceView;
import com.sun.tools.visualvm.core.ui.DataSourceViewProvider;
import com.sun.tools.visualvm.core.ui.DataSourceViewsManager;

public class TransactionsViewProvider extends DataSourceViewProvider<TransactionsDataSource> {

    private static DataSourceViewProvider<TransactionsDataSource> instance = new TransactionsViewProvider();

    @Override
    public boolean supportsViewFor(TransactionsDataSource dds) {
        return (true);
    }

    @Override
    protected DataSourceView createView(TransactionsDataSource dds) {
        //printjmxinfo("HelloWorldViewProvider", "createView", application);

        return new TransactionsView(dds);
    }

    public static void initialize() {
        DataSourceViewsManager.sharedInstance().addViewProvider(instance, TransactionsDataSource.class);
    }

    public static void unregister() {
        DataSourceViewsManager.sharedInstance().removeViewProvider(instance);
    }
}
