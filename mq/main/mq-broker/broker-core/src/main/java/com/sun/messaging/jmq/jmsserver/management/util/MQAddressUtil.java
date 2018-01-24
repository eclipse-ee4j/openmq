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
 * @(#)MQAddressUtil.java	1.5 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.management.util;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.io.MQAddress;

public class MQAddressUtil {
    private static boolean DEBUG = false;

    /**
     * Return portmapper MQAddress. This MQAddress does not include the service name
     * and is of the form mq://host:port
     *
     * @param port	Portmapper port
     * @return		Portmapper address. This MQAddress does not include the 
     *			service name. It is of the form mq://host:port.
     */
    public static MQAddress getPortMapperMQAddress(Integer port)  {
	MQAddress	addr = null;

	if (port == null)  {
	    if (DEBUG)  {
	        Logger logger = Globals.getLogger();
                logger.log(Logger.DEBUG, "Null port passed in to getPortMapperMQAddress()");
	    }
	    return (null);
	}

	try  {
	    String url = Globals.getMQAddress().getHostName() + ":"  + port.toString();
	    addr = PortMapperMQAddress.createAddress(url);
	} catch (Exception e)  {
	    if (DEBUG)  {
	        Logger logger = Globals.getLogger();
                logger.log(Logger.DEBUG, "Failed to create portmapper address", e);
	    }
	}

	return (addr);
    }

    /**
     * Return connection service MQAddress. Connection service addresses
     * can have 2 forms and the bypassPortmapper parameter allows the caller
     * to select which one is desired. The 2 forms depend on whether the client
     * will contact the portmapper (mq://host:port) or the connection service 
     * directly ({mqtcp,mqssl}://host:port/svcname).
     *
     * @param svcName	Connection service name.
     * @param port	Portmapper port or connection service port.
     * @param bypassPortmapper	Boolean to indicate which type of address is desired.
     *				If the value for bypassPortmapper is false, the address
     *				will be of the form mq://host:port/svcName. If the value
     *				is true, the scheme will be one of mqtcp or mqssl. The 
     *				address will be of the form scheme://host:svc_port/svc_name.
     *				e.g. mqtcp://myhost:87635/jms
     * @return		Connection service address.
     */
    public static MQAddress getServiceMQAddress(String svcName, Integer port, 
				boolean bypassPortmapper)  {
	MQAddress addr = null;
	String scheme = "mq";
	Logger logger = Globals.getLogger();

	if ((svcName == null) || (svcName.equals("")) || (port == null))  {
	    if (DEBUG)  {
                logger.log(Logger.DEBUG, "Null service name and/or port passed in to getServiceMQAddress()");
	    }
	    return (null);
	}

	if (bypassPortmapper)  {
	    scheme = getScheme(svcName);
	}

	if (scheme == null)  {
	    return (null);
	}

	if (bypassPortmapper)  {
	    try  {
	        String url = scheme 
			+ "://" 
			+ Globals.getMQAddress().getHostName() 
			+ ":"  
			+ port.toString() 
			+ "/" + svcName;
	        addr = MQAddress.getMQAddress(url);
	    } catch (Exception e)  {
		if (DEBUG)  {
                    logger.log(Logger.DEBUG, "Failed to create service address", e);
		}
	    }
	} else  {
	    try  {
	        String url = Globals.getMQAddress().getHostName()
				+ ":"  
				+ port.toString()
				+ "/"
				+ svcName;
	        addr = PortMapperMQAddress.createAddress(url);
	    } catch (Exception e)  {
		if (DEBUG)  {
                    logger.log(Logger.DEBUG, "Failed to create service address", e);
		}
	    }
	}

	return (addr);
    }

    private static String getScheme(String svcName)  {
        String proto = Globals.getConfig().getProperty(Globals.IMQ + "." + svcName + ".protocoltype");
        String scheme = null;

	if (proto.equals("tcp"))  {
	    scheme = "mqtcp";
	} else if (proto.equals("tls"))  {
	    scheme = "mqssl";
	}

	return (scheme);
    }

}
