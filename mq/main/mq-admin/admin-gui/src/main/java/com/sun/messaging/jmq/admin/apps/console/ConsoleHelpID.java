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
 * @(#)ConsoleHelpID.java	1.5 06/27/07
 */ 

package com.sun.messaging.jmq.admin.apps.console;
/*
 * This package contains all the HelpSet IDs.
 */

public interface ConsoleHelpID {

    /*
     * All the specific help ids available in the helpset(s).
     */
    public static String INTRO 		      	= "overview";

    public static String ADD_OBJECT_STORE  	= "add_object_store";
    public static String CONNECT_OBJECT_STORE  	= "conndis_object_store";
    public static String ADD_DEST_OBJECT   	= "add_destination_obj";
    public static String ADD_CF_OBJECT     	= "add_connection_fact";
    public static String OBJECT_STORE_PROPS 	= "object_store_properties";
    public static String DEST_OBJECT_PROPS 	= "destination_obj_properties";
    public static String CF_OBJECT_PROPS   	= "connection_factory_prop";

    public static String ADD_BROKER	   	= "add_broker";
    public static String CONNECT_BROKER	   	= "conndis_broker";
    public static String ADD_BROKER_DEST   	= "add_broker_destination";
    public static String BROKER_PROPS 	   	= "broker_information";
    public static String QUERY_BROKER	   	= "broker_configuration";
    public static String SERVICE_PROPS 	   	= "service_properties";
    public static String BROKER_DEST_PROPS 	= "broker_destination_properties";

}
