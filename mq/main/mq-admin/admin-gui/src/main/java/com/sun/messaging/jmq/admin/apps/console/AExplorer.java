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

/*
 * @(#)AExplorer.java	1.35 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;


import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseAdapter;

import java.util.Enumeration;

import javax.swing.JTree;
import javax.swing.JScrollPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.ImageIcon;
import javax.swing.ToolTipManager;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import com.sun.messaging.jmq.admin.event.AdminEvent;
import com.sun.messaging.jmq.admin.event.AdminEventListener;
import com.sun.messaging.jmq.admin.apps.console.event.SelectionEvent;

/** 
 * The explorer component lists the JMS object stores and JMQ
 * brokers that the admin console knows about currently.
 *
 */
public class AExplorer extends JScrollPane implements TreeSelectionListener  {

    private ActionManager		actionMgr;
    private EventListenerList	aListeners = new EventListenerList();
    private JTree tree;
    private ExplorerTreeModel		model;
    private Object			temp;
    private DefaultMutableTreeNode	root;
    private ObjStoreListCObj		objStoreListCObj;
    private BrokerListCObj		brokerListCObj;
    private boolean			scrollToPath;

    /**
     * Create/initialize the admin explorer GUI component.
     */
    public AExplorer(ActionManager actionMgr,
		ObjStoreListCObj objStoreListCObj, BrokerListCObj brokerListCObj) {
	super();

        this.objStoreListCObj = objStoreListCObj;
	this.brokerListCObj = brokerListCObj;
	this.actionMgr = actionMgr;

	initGui();
    } 

    /*
     * Selection management
     */
    public void select(ConsoleObj cObj)  {
	DefaultMutableTreeNode node = (DefaultMutableTreeNode)cObj;
	tree.setSelectionPath(new TreePath(node.getPath()));

	SelectionEvent se = new SelectionEvent(this, SelectionEvent.OBJ_SELECTED);
	se.setSelectedObj((ConsoleObj)node);
	fireAdminEventDispatched(se);
	
    }
    public void clearSelection() {
	tree.clearSelection();
    }

    /*
     * JMQ Object Administration
     */

    /*
     * Add to list of obj stores:
     *	- top level obj store node (e.g. "Test LDAP server")
     * 	- child nodes to support obj store tree infrastructure i.e.
     *		- "Destinations"
     *		- "ConnectionFactories"
     */
    public void addObjStore(ConsoleObj objStoreCObj)  {
        insertNewNode(objStoreListCObj, objStoreCObj);
    }

    public void loadObjStores(ConsoleObj objStoreCObj[])  {
    }

    public void deleteObjStore(ConsoleObj objStoreCObj)  {
    }

    public void addToParent(ConsoleObj parent, ConsoleObj child)  {
        insertNewNode(parent, child);
    }

    public void removeFromParent(ConsoleObj child) {
	if (child.getParent() != null)
            model.removeNodeFromParent(child);
    }

    public void addBroker(ConsoleObj brokerCObj)  {
        insertNewNode(brokerListCObj, brokerCObj);
    }

    public void removeBroker()  {
    }

    /**
     * Add an admin event listener to this admin UI component. 
     * @param l	admin event listener to add.
     */
    public void addAdminEventListener(AdminEventListener l)  {
	aListeners.add(AdminEventListener.class, l);
    }


    /**
     * Remove an admin event listener for this admin UI component. 
     * @param l	admin event listener to remove.
     */
    public void removeAdminEventListener(AdminEventListener l)  {
	aListeners.remove(AdminEventListener.class, l);
    }


    public void expandAll() {
        //int rowCount = tree.getRowCount();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    public void collapseAll() {
        //int rowCount = tree.getRowCount();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.collapseRow(i);
        }
    }
 
    public void nodeChanged(DefaultMutableTreeNode node) {
        model.nodeChanged(node);
    }

    /*
     * BEGIN INTERFACE TreeSelectionListener
     */
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = 
	    (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
	SelectionEvent se;

	if (node == null)  {
	    /*
	     * We don't support this yet.
	     * Except at startup, an object will always be selected.
	    se = new SelectionEvent(this, SelectionEvent.CLEAR_SELECTION);
	    */
	} else  {
	    /*
	    System.err.println("node selected class: " + node.getClass().getName());
	    System.err.println("node selected userObject class: " + node.getClass().getName());
	    */

	    se = new SelectionEvent(this, SelectionEvent.OBJ_SELECTED);
	    se.setSelectedObj((ConsoleObj)node);
	    fireAdminEventDispatched(se);
	}

    }
    /*
     * END INTERFACE TreeSelectionListener
     */

    /*
     * Fire off/dispatch an admin event to all the listeners.
     */
    private void fireAdminEventDispatched(AdminEvent ae)  {
	Object[] l = aListeners.getListenerList();

	for (int i = l.length-2; i>=0; i-=2)  {
	    if (l[i] == AdminEventListener.class)  {
		((AdminEventListener)l[i+1]).adminEventDispatched(ae);
	    }
	}
    }

    private void initGui()  {
        root = new DefaultMutableTreeNode("JMQ Administration");

	model = new ExplorerTreeModel(root);

	/*
	 * Create JTree to display object/broker admin objects
	 */
        tree = new JTree(model);
        tree.getSelectionModel().setSelectionMode
                (TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.addTreeSelectionListener(this);

        String lineStyle = "Angled"; 
        tree.putClientProperty("JTree.lineStyle", lineStyle);
	tree.setShowsRootHandles(true);
	tree.setRootVisible(false);

	ToolTipManager.sharedInstance().registerComponent(tree);
	tree.setCellRenderer(new ExplorerTreeCellRenderer());

	MouseListener ml = new ExplorerMouseAdapter(actionMgr, tree) ;

	tree.addMouseListener(ml);

	/*
	 * Add 2 top level children to tree
	 */
	model.insertNodeInto(objStoreListCObj, root, 0);
	model.insertNodeInto(brokerListCObj, root, 1);
	model.reload();

	setViewportView(tree);

        setPreferredSize(new Dimension(210, 50));
    }

    /*
     * Adding this call for bug fix 4526701.
     * This method is called from AController.init() to turn
     * off setScrollToVisible() if we're initializing the tree nodes
     * during startup.  Otherwise, tree gets confused and can't
     * display root node after all the nodes are created.
     */
    public void setScrollToPath(boolean scroll) {
	scrollToPath = scroll;
    }

    private void insertNewNode(ConsoleObj parent, ConsoleObj child)  {
	int newIndex;

	/*
	 * Insert top level obj store node into tree.
	 */
	newIndex = model.getChildCount(parent);
	model.insertNodeInto(child, parent, newIndex);

	/*
	 * Make sure we can see this newly created node and it's children.
	 */
	Enumeration e = child.children();
	if (e.hasMoreElements()) {
	    DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.nextElement();
	    // We only want to call this scrollPathToVisible() only
	    // when the user manually adds a node -- not during
	    // initialization when we read in the objstore/broker properties
	    // because then the tree scrolls down to the bottom and
	    // we can't set it back to the root without some strange 
	    // behavior. Fix for 4526701.
	    if (scrollToPath)
	        tree.scrollPathToVisible(new TreePath(node.getPath()));
	}

    }

    /*
     * Not used
    private void insertNewNode(ConsoleObj parent, ConsoleObj child, 
				int index)  {

	// Insert top level obj store node into tree.
	model.insertNodeInto(child, parent, index);

	// Make sure we can see this newly created node and it's children.
	Enumeration e = child.children();
	if (e.hasMoreElements()) {
	    DefaultMutableTreeNode node = (DefaultMutableTreeNode)e.nextElement();
	    // We only want to call this scrollPathToVisible() only
	    // when the user manually adds a node -- not during
	    // initialization when we read in the objstore/broker properties
	    // because then the tree scrolls down to the bottom and
	    // we can't set it back to the root without some strange 
	    // behavior.  Fix for 4526701.
	    if (scrollToPath)
	    if (scrollToPath)
	        tree.scrollPathToVisible(new TreePath(node.getPath()));
	}

    }
    */
}

class ExplorerMouseAdapter extends MouseAdapter  {
    private ActionManager              actionMgr;
    private JTree tree;

    public ExplorerMouseAdapter(ActionManager actionMgr, JTree tree)  {
	this.tree = tree;
	this.actionMgr = actionMgr;
    }

    public void mouseClicked(MouseEvent e)  {
	/*
        System.err.println("\n**MouseClicked:");
	*/

	doPopup(e);
    }

    public void mousePressed(MouseEvent e)  {
	/*
        System.err.println("\n**MousePressed:");
	*/

	doPopup(e);
    }

    public void mouseReleased(MouseEvent e) {
	/*
	System.err.println("\n**MouseRelease:");
	*/

	doPopup(e);
    }

    private void doPopup(MouseEvent e)  {
	int selRow = tree.getRowForLocation(e.getX(), e.getY());
	TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());

	if (!e.isPopupTrigger())  {
	    /*
	    System.err.println("Will not show popup !");
	    */
	    return;
	}

	/*
	System.err.println("Show popup !");
	*/

	if (selRow != -1) {
	    JPopupMenu popup; 

	    Object obj = selPath.getLastPathComponent();
	    //String item = selPath.getLastPathComponent().toString();

	    /*
	    System.err.println("last select path component: " + item);
	    System.err.println("\t class: " + obj.getClass().getName());
	    */

	    if (!(obj instanceof ConsoleObj))  {
		return;
	    }

	    ConsoleObj conObj = (ConsoleObj)obj;

	    tree.addSelectionPath(selPath);

	    popup = conObj.getExporerPopupMenu(actionMgr);

	    if (popup != null)  {
	        popup.show(e.getComponent(), e.getX(), e.getY());
	    }
	}
    }
}

class ExplorerTreeCellRenderer extends DefaultTreeCellRenderer  {
    ImageIcon	leafIcon,
		parentIcon;

    public Component getTreeCellRendererComponent(JTree tree, Object value,
                            boolean sel, boolean expanded, 
			    boolean leaf, int row, 
			    boolean hasFocus)  {
        super.getTreeCellRendererComponent(tree, value, sel, 
				expanded, leaf, row, hasFocus);
	
	/*
	System.err.println("getTreeCellRendererComponent: " + value);
	System.err.println("\tvalue class: " +
			value.getClass().getName());
	*/

	if (value instanceof DefaultMutableTreeNode)  {
	    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;

	    if (treeNode instanceof ConsoleObj)  {
		ConsoleObj node = (ConsoleObj)treeNode;
	        ImageIcon ic = node.getExplorerIcon();
	        if (ic == null)  {
		    if (leaf)  {
		        ic = AGraphics.adminImages[AGraphics.DEFAULT_LEAF];
		    } else  {
		        ic = AGraphics.adminImages[AGraphics.DEFAULT_FOLDER];
		    }
		}

	        setIcon(ic);

		String tooltip = node.getExplorerToolTip();

	        setToolTipText(tooltip);
	    } else  {
	        setIcon(leafIcon);
	    }
	}

	return (this);
    }
}

/**
 * Tree model for explorer.
 * <P>
 * All this class does is tag certain ConsoleObj nodes as being
 * leaf nodes so that their children are not rendered in the JTree.
 */
class ExplorerTreeModel extends DefaultTreeModel  {

    /**
     * Instantiate a ExplorerTreeModel.
     *
     * @param root The root node for the model.
     */
    public ExplorerTreeModel(TreeNode root)  {
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
    public boolean isLeaf(Object node) {
	if ((node instanceof ObjStoreDestListCObj) ||
	    (node instanceof ObjStoreConFactoryListCObj) ||
	    (node instanceof BrokerServiceListCObj) ||
	    (node instanceof BrokerDestListCObj) ||
	    (node instanceof BrokerLogListCObj))  {
	    return (true);
	} else  {
	    return (((TreeNode)node).isLeaf());
	}
    }
}
