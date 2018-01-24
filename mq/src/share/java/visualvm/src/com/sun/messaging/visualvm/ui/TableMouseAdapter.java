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

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JTable;
import javax.swing.event.EventListenerList;

public class TableMouseAdapter extends MouseAdapter {

    private JTable table = null;
    private EventListenerList mqUIListeners = new EventListenerList();

    public TableMouseAdapter(JTable table) {
        this.table = table;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
            if (table != null) {
                int selectedIndex = -1;

                selectedIndex = table.getSelectedRow();

                if (selectedIndex != -1) {
                    MQResourceListTableModel model = (MQResourceListTableModel) table.getModel();

                    if (model != null) {
                        Object obj = model.getRowData(selectedIndex);
                        MQUIEvent me = new MQUIEvent(this, MQUIEvent.ITEM_SELECTED);
                        me.setSelectedObject(obj);
                        fireMQUIEventDispatched(me);
                    }
                }
            }
        }
    }

    public void addMQUIEventListener(MQUIEventListener l) {
        mqUIListeners.add(MQUIEventListener.class, l);
    }

    public void removeMQUIEventListener(MQUIEventListener l) {
        mqUIListeners.remove(MQUIEventListener.class, l);
    }

    public void fireMQUIEventDispatched(MQUIEvent me) {
        Object[] l = mqUIListeners.getListenerList();

        for (int i = l.length - 2; i >= 0; i -= 2) {
            if (l[i] == MQUIEventListener.class) {
                ((MQUIEventListener) l[i + 1]).mqUIEventDispatched(me);
            }
        }
    }
}
