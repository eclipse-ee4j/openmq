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
 * @(#)Utils.java	1.8 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.objmgr;

import com.sun.messaging.jmq.admin.resources.AdminResources;
import com.sun.messaging.jmq.admin.util.Globals;

/** 
 * This is a class that contains util methods for ObjMgr
 *
 */
public class Utils implements ObjMgrOptions  {

    private static AdminResources ar = Globals.getAdminResources();

    public static String getObjTypeString(String type)  {
	if (type == null)  {
	    return (null);
	}

	if (type.equals(OBJMGR_TYPE_TOPIC))
	    return (ar.getString(ar.I_TOPIC));

	if (type.equals(OBJMGR_TYPE_QUEUE))
	    return (ar.getString(ar.I_QUEUE));

	if (type.equals(OBJMGR_TYPE_QCF))
	    return (ar.getString(ar.I_QCF));

	if (type.equals(OBJMGR_TYPE_TCF))
	    return (ar.getString(ar.I_TCF));

	if (type.equals(OBJMGR_TYPE_CF))
	    return (ar.getString(ar.I_CF));

	if (type.equals(OBJMGR_TYPE_XQCF))
	    return (ar.getString(ar.I_XQCF));

	if (type.equals(OBJMGR_TYPE_XTCF))
	    return (ar.getString(ar.I_XTCF));

	if (type.equals(OBJMGR_TYPE_XCF))
	    return (ar.getString(ar.I_XCF));

	return (null);
    }

    public static boolean isValidObjType(String type)  {
	if (type == null)  {
	    return (false);
	}

	if (type.equals(OBJMGR_TYPE_TOPIC) ||
	    type.equals(OBJMGR_TYPE_QUEUE) ||
	    type.equals(OBJMGR_TYPE_QCF) ||
	    type.equals(OBJMGR_TYPE_TCF) ||
	    type.equals(OBJMGR_TYPE_CF) ||
	    type.equals(OBJMGR_TYPE_XQCF) ||
	    type.equals(OBJMGR_TYPE_XTCF) ||
	    type.equals(OBJMGR_TYPE_XCF)) {
	    return (true);
	}
	
	return (false);
    }

    public static boolean isDestObjType(String type)  {
	if (!isValidObjType(type))  {
	    return (false);
	}

	if (type.equals(OBJMGR_TYPE_TOPIC) ||
	    type.equals(OBJMGR_TYPE_QUEUE))  {
	    return (true);
	}
	
	return (false);
    }

    public static boolean isFactoryObjType(String type)  {
	if (!isValidObjType(type))  {
	    return (false);
	}

	if (type.equals(OBJMGR_TYPE_QCF) ||
	    type.equals(OBJMGR_TYPE_TCF) ||
	    type.equals(OBJMGR_TYPE_CF)  ||
	    type.equals(OBJMGR_TYPE_XQCF) ||
	    type.equals(OBJMGR_TYPE_XTCF) ||
	    type.equals(OBJMGR_TYPE_XCF))  {
	    return (true);
	}
	
	return (false);
    }

}
