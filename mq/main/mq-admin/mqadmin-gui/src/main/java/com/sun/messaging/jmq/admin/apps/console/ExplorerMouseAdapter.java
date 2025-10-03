/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

import javax.swing.JTree;
import javax.swing.JPopupMenu;

import javax.swing.tree.TreePath;

class ExplorerMouseAdapter extends MouseAdapter {
    private ActionManager actionMgr;
    private JTree tree;

    ExplorerMouseAdapter(ActionManager actionMgr, JTree tree) {
        this.tree = tree;
        this.actionMgr = actionMgr;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        /*
         * System.err.println("\n**MouseClicked:");
         */

        doPopup(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        /*
         * System.err.println("\n**MousePressed:");
         */

        doPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        /*
         * System.err.println("\n**MouseRelease:");
         */

        doPopup(e);
    }

    private void doPopup(MouseEvent e) {
        int selRow = tree.getRowForLocation(e.getX(), e.getY());
        TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());

        if (!e.isPopupTrigger()) {
            /*
             * System.err.println("Will not show popup !");
             */
            return;
        }

        /*
         * System.err.println("Show popup !");
         */

        if (selRow != -1) {
            JPopupMenu popup;

            Object obj = selPath.getLastPathComponent();
            // String item = selPath.getLastPathComponent().toString();

            /*
             * System.err.println("last select path component: " + item); System.err.println("\t class: " +
             * obj.getClass().getName());
             */

            if (!(obj instanceof ConsoleObj)) {
                return;
            }

            ConsoleObj conObj = (ConsoleObj) obj;

            tree.addSelectionPath(selPath);

            popup = conObj.getExporerPopupMenu(actionMgr);

            if (popup != null) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }
}

