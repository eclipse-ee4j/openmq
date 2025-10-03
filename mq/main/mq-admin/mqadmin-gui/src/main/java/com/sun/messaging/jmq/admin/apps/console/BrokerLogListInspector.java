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

package com.sun.messaging.jmq.admin.apps.console;

import java.io.Serial;
import javax.swing.JPanel;

/**
 * Inspector panel for the broker log list.
 *
 * @see InspectorPanel
 * @see AInspector
 * @see ConsoleObj
 */
public class BrokerLogListInspector extends InspectorPanel {
    @Serial
    private static final long serialVersionUID = 436146525652212035L;

    @Override
    public JPanel createWorkPanel() {
        return (null);
    }

    @Override
    public void inspectorInit() {
    }

    @Override
    public void clearSelection() {
    }

    @Override
    public void selectedObjectUpdated() {
    }
}
