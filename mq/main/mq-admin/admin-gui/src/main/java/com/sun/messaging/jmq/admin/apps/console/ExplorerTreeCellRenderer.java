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

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.ImageIcon;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

class ExplorerTreeCellRenderer extends DefaultTreeCellRenderer {
    /**
     * 
     */
    private static final long serialVersionUID = -8842768979030693460L;
    ImageIcon leafIcon, parentIcon;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        /*
         * System.err.println("getTreeCellRendererComponent: " + value); System.err.println("\tvalue class: " +
         * value.getClass().getName());
         */

        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;

            if (treeNode instanceof ConsoleObj) {
                ConsoleObj node = (ConsoleObj) treeNode;
                ImageIcon ic = node.getExplorerIcon();
                if (ic == null) {
                    if (leaf) {
                        ic = AGraphics.adminImages[AGraphics.DEFAULT_LEAF];
                    } else {
                        ic = AGraphics.adminImages[AGraphics.DEFAULT_FOLDER];
                    }
                }

                setIcon(ic);

                String tooltip = node.getExplorerToolTip();

                setToolTipText(tooltip);
            } else {
                setIcon(leafIcon);
            }
        }

        return (this);
    }
}

