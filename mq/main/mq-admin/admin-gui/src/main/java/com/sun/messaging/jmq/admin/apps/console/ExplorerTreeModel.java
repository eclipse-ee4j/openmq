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

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * Tree model for explorer.
 * <P>
 * All this class does is tag certain ConsoleObj nodes as being leaf nodes so that their children are not rendered in
 * the JTree.
 */
class ExplorerTreeModel extends DefaultTreeModel {

    /**
     * 
     */
    private static final long serialVersionUID = 797941995460452105L;

    /**
     * Instantiate a ExplorerTreeModel.
     *
     * @param root The root node for the model.
     */
    ExplorerTreeModel(TreeNode root) {
        super(root);
    }

    /**
     * Returns true if node is a leaf in the JTree, false otherwise.
     * <P>
     * Leaf nodes in the admin console explorer are the following:
     * <UL>
     * <LI>Object Store Destination List
     * <LI>Object Store Connection Factory List
     * <LI>Broker Service List
     * <LI>Broker Destination List
     * <LI>Broker Log List
     * </UL>
     *
     * @return true if node is a leaf in the JTree, false otherwise.
     */
    @Override
    public boolean isLeaf(Object node) {
        if ((node instanceof ObjStoreDestListCObj) || (node instanceof ObjStoreConFactoryListCObj) || (node instanceof BrokerServiceListCObj)
                || (node instanceof BrokerDestListCObj) || (node instanceof BrokerLogListCObj)) {
            return (true);
        } else {
            return (((TreeNode) node).isLeaf());
        }
    }
}

