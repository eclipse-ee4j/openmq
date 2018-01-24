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

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class QueryDestinationDialog extends OneResourceDialog {

    public QueryDestinationDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
    }

    @Override
    public JPanel initResourcePanel() {
        OneDestinationAttrs oda = new OneDestinationAttrs(null);

        setResourcePanel(oda);

        return oda;
    }

    public void setDestinationObjectName(ObjectName o) {
        JPanel p = getResourcePanel();

        if (p == null) {
            return;
        }

        OneDestinationAttrs oda = (OneDestinationAttrs) p;

        oda.setDestinationObjectName(o);
        setResourceLabel(o.toString());
    }

    void setMBeanServerConnection(MBeanServerConnection mbsc) {
        JPanel p = getResourcePanel();

        if (p == null) {
            return;
        }

        OneDestinationAttrs oda = (OneDestinationAttrs) p;
        oda.setMBeanServerConnection(mbsc);
    }
}
