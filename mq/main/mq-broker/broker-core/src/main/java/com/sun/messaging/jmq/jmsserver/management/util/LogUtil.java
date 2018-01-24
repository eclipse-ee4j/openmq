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
 * @(#)LogUtil.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import com.sun.messaging.jmq.util.log.Logger;

public class LogUtil {

    public static String toExternalLogLevel(String internalLogLevel)  {
	if (internalLogLevel.equals("NONE"))  {
	    return (com.sun.messaging.jms.management.server.LogLevel.NONE);
	} else if (internalLogLevel.equals("ERROR"))  {
	    return (com.sun.messaging.jms.management.server.LogLevel.ERROR);
	} else if (internalLogLevel.equals("WARNING"))  {
	    return (com.sun.messaging.jms.management.server.LogLevel.WARNING);
	} else if (internalLogLevel.equals("INFO"))  {
	    return (com.sun.messaging.jms.management.server.LogLevel.INFO);
	} else  {
	    return (com.sun.messaging.jms.management.server.LogLevel.UNKNOWN);
	}
    }

    public static String toInternalLogLevel(String externalLogLevel)  {
	if (externalLogLevel.equals(com.sun.messaging.jms.management.server.LogLevel.NONE))  {
	    return ("NONE");
	} else if (externalLogLevel.equals(com.sun.messaging.jms.management.server.LogLevel.ERROR))  {
	    return ("ERROR");
	} else if (externalLogLevel.equals(com.sun.messaging.jms.management.server.LogLevel.WARNING))  {
	    return ("WARNING");
	} else if (externalLogLevel.equals(com.sun.messaging.jms.management.server.LogLevel.INFO))  {
	    return ("INFO");
	} else  {
	    return ("UNKNOWN");
	}
    }
}
