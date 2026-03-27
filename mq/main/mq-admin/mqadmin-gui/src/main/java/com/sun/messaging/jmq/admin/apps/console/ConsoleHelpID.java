/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
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

/*
 * This package contains all the HelpSet IDs.
 */

final class ConsoleHelpID {
    private ConsoleHelpID() {
        throw new UnsupportedOperationException();
    }

    /*
     * All the specific help ids available in the helpset(s).
     */
    static final String INTRO = "overview";

    static final String ADD_OBJECT_STORE = "add_object_store";
    static final String CONNECT_OBJECT_STORE = "conndis_object_store";
    static final String ADD_DEST_OBJECT = "add_destination_obj";
    static final String ADD_CF_OBJECT = "add_connection_fact";
    static final String OBJECT_STORE_PROPS = "object_store_properties";
    static final String DEST_OBJECT_PROPS = "destination_obj_properties";
    static final String CF_OBJECT_PROPS = "connection_factory_prop";

    static final String ADD_BROKER = "add_broker";
    static final String CONNECT_BROKER = "conndis_broker";
    static final String ADD_BROKER_DEST = "add_broker_destination";
    static final String BROKER_PROPS = "broker_information";
    static final String QUERY_BROKER = "broker_configuration";
    static final String SERVICE_PROPS = "service_properties";
    static final String BROKER_DEST_PROPS = "broker_destination_properties";

}
