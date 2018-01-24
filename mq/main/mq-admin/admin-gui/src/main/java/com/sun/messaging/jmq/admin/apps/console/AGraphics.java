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
 * @(#)AGraphics.java	1.19 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;

import java.io.File;
import javax.swing.ImageIcon;

import com.sun.messaging.jmq.admin.util.Globals;
import com.sun.messaging.jmq.Version;


/** 
 * This class initializes all the images used by the iMQ admin
 * console.
 *
 * This class is also used to access the images. Here is an
 * example:
 * <PRE>
 *     JLabel l = new JLabel(AGraphics.adminImages[AGraphics.SPLASH_SCREEN]);
 * </PRE>
 */
public class AGraphics  {

    /*
     * Image file names.
     *
     * File names are relative to
     * IMQ_HOME/lib/images/admin
     * Defaults to openmq splash.
     */
    private static String imageFileNames[] = {
	    "AppIcon48x.gif",		/* DESKTOP_ICON */

	    "splash_openmq.gif",	/* SPLASH_SCREEN */

	    "folder.gif",		/* DEFAULT_FOLDER */
	    "dot.gif",			/* DEFAULT_LEAF */
	    "CollectionOfObjectStores16x.gif",		/* OBJSTORE_LIST */
	    "ObjectStore16x.gif",	/* OBJSTORE */
	    "ObjectStoreCFDestination16xList.gif",		/* OBJSTORE_DEST_LIST */
	    "ObjectStoreCFDestination16xList.gif",		/* OBJSTORE_CONN_FAC_LIST */
	    "CollectionOfBrokers16x.gif",		/* BROKER_LIST */
	    "Broker16X.gif",	/* BROKER */
	    "ServiceList16x.gif",	/* BROKER_SERVICE_LIST */
	    "dot.gif",			/* BROKER_SERVICE */
	    "BrokerDestinationList16x.gif",		/* BROKER_DEST_LIST */
	    "dot.gif",			/* BROKER_TOPIC */
	    "folder.gif",		/* BROKER_QUEUE_LIST */
	    "dot.gif",			/* BROKER_QUEUE */
	    "folder.gif",		/* BROKER_LOG_LIST */
	    "dot.gif",			/* BROKER_LOG */

	    "ObjectStoreX16X.gif",	/* OBJSTORE_DISCONNECTED */
	    "BrokerX16X.gif",	/* BROKER_DISCONNECTED */

	    "Add24.gif",		/* ADD */
	    "Delete24.gif",		/* DELETE */
	    "Preferences24.gif",	/* PREFERENCES */
	    "Pause24.gif",		/* PAUSE */
	    "Play24.gif",		/* RESUME */
	    "Properties24.gif",		/* PROPERTIES */
	    "Refresh24.gif",		/* REFRESH */
	    "Restart24x.gif",	/* RESTART */
	    "Shutdown24x.gif",	/* SHUTDOWN */
	    "ExpandAll24x.gif",	/* EXPAND_ALL */
	    "CollapseAll24x.gif",	/* COLLAPSE_ALL */
	    "AdminConnectToObjectStore24x.gif",	/* CONNECT_TO_OBJSTORE */
	    "AdminConnectBroker24x.gif",	/* CONNECT_TO_BROKER */
	    "AdminDisConnectToObjectStore24x.gif",	/* DISCONNECT_FROM_OBJSTORE */
	    "AdminDisConnectBroker24x.gif",	/* DISCONNECT_FROM_BROKER */
	    "Purge24x.gif",				/* PURGE */
	    "BrokerQuery24X.gif",			/* QUERY_BROKER */
	    "AboutBox48x.gif"				/* ABOUT_BOX */
    };

    /*
     * Indices for images
     */

    /*
     * Desktop icon
     */
    public final static int DESKTOP_ICON		= 0;

    /*
     * Splash screen
     */
    public final static int SPLASH_SCREEN		= 1;

    /*
     * Explorer pane tree icons
     */
    public final static int DEFAULT_FOLDER		= 2;
    public final static int DEFAULT_LEAF		= 3;
    public final static int OBJSTORE_LIST		= 4;
    public final static int OBJSTORE			= 5;
    public final static int OBJSTORE_DEST_LIST		= 6;
    public final static int OBJSTORE_DEST		= DEFAULT_LEAF;
    public final static int OBJSTORE_CONN_FAC_LIST	= 7;
    public final static int OBJSTORE_CONN_FAC		= DEFAULT_LEAF;
    public final static int BROKER_LIST			= 8;
    public final static int BROKER			= 9;
    public final static int BROKER_SERVICE_LIST		= 10;
    public final static int BROKER_SERVICE		= 11;
    public final static int BROKER_DEST_LIST		= 12;
    public final static int BROKER_DEST			= 13;
    public final static int BROKER_LOG_LIST		= 14;
    public final static int BROKER_LOG			= DEFAULT_LEAF;

    /*
     * Disconnected server icons
     */
    public final static int OBJSTORE_DISCONNECTED	= 18;
    public final static int BROKER_DISCONNECTED		= 19;

    /*
     * Toolbar/menu icons
     */
    public final static int ADD				= 20;
    public final static int DELETE			= 21;
    public final static int PREFERENCES			= 22;
    public final static int PAUSE			= 23;
    public final static int RESUME			= 24;
    public final static int PROPERTIES			= 25;
    public final static int REFRESH			= 26;
    public final static int RESTART			= 27;
    public final static int SHUTDOWN			= 28;
    public final static int EXPAND_ALL			= 29;
    public final static int COLLAPSE_ALL		= 30;
    public final static int CONNECT_TO_OBJSTORE		= 31;
    public final static int CONNECT_TO_BROKER		= 32;
    public final static int DISCONNECT_FROM_OBJSTORE	= 33;
    public final static int DISCONNECT_FROM_BROKER	= 34;
    public final static int PURGE			= 35;
    public final static int QUERY_BROKER		= 36;
    public final static int ABOUT_BOX			= 37;




    static ImageIcon	adminImages[];

    private static boolean	imagesLoaded = false;

    public static void loadImages() {
	int		imgTotal;
	String		imgRoot;
	Version		version;

	if (imagesLoaded)
	    return;

	imgTotal = imageFileNames.length;
	adminImages = new ImageIcon [ imgTotal ];
	version = new Version(false);

	/*
	System.out.println("Loading Images...");
	*/

	/*
         * File names are relative to
         * IMQ_HOME/lib/images/admin
	 */
	imgRoot = Globals.JMQ_LIB_HOME
			+ File.separator 
			+ "images"
			+ File.separator 
			+ "admin";

	/*
	 * When loading splash screen, check if commercial product
	 * and load splash_comm instead of default splash_openmq.
    	 */
	for (int i = 0; i < imgTotal; ++i)  {
	    String fileName;

	    if (i == SPLASH_SCREEN && version.isCommercialProduct()) {
	      fileName = imgRoot
			  + File.separator
			  + "splash_comm.gif";
	    } else {
	      fileName = imgRoot
			  + File.separator
			  + imageFileNames[i];
 	    }

	    /*
            System.err.println("loading: " + fileName);
	     */
	    
	    adminImages[i] = new ImageIcon(fileName);
	}

	/*
	System.out.println("  - Images loaded");
	*/
	imagesLoaded = true;
    }
}
