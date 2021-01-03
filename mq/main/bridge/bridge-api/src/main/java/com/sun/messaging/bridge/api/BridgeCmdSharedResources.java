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

/*
 */

package com.sun.messaging.bridge.api;

/**
 * This interface is shared by imqbridgemgr, BridgeServiceManager and individual services. It contains imqbridgemgr
 * string resource keys that are referenced by all.
 *
 * The properties for the keys in this file are defined in imqbridgemgr resource properties file
 *
 * @author amyk
 *
 */

public interface BridgeCmdSharedResources {

    // 1000-1999 Informational Messages
    String I_BGMGR_TITLE_BRIDGE_NAME = "BS1000";
    String I_BGMGR_TITLE_BRIDGE_TYPE = "BS1001";
    String I_BGMGR_TITLE_BRIDGE_STATE = "BS1002";

    String I_BGMGR_TITLE_NUM_LINKS = "BS1003";

    String I_BGMGR_TITLE_LINK_NAME = "BS1004";
    String I_BGMGR_TITLE_LINK_STATE = "BS1005";
    String I_BGMGR_TITLE_SOURCE = "BS1006";
    String I_BGMGR_TITLE_TARGET = "BS1007";
    String I_BGMGR_TITLE_TRANSACTED = "BS1008";

    String I_BGMGR_TITLE_TRANSACTIONS = "BS1009";

    String I_BGMGR_TITLE_POOLED = "BS1010";
    String I_BGMGR_TITLE_NUM_INUSE = "BS1011";
    String I_BGMGR_TITLE_NUM_IDLE = "BS1012";
    String I_BGMGR_TITLE_IDLE = "BS1013";
    String I_BGMGR_TITLE_TIMEOUT = "BS1014";
    String I_BGMGR_TITLE_MAX = "BS1015";
    String I_BGMGR_TITLE_RETRIES = "BS1016";
    String I_BGMGR_TITLE_RETRY = "BS1017";
    String I_BGMGR_TITLE_INTERVAL = "BS1018";

    String I_BGMGR_TITLE_SHARED = "BS1019";
    String I_BGMGR_TITLE_REF = "BS1020";
    String I_BGMGR_TITLE_COUNT = "BS1021";

    String I_STATE_UNINITIALIZED = "BS1500";
    String I_STATE_STARTING = "BS1501";
    String I_STATE_STARTED = "BS1502";
    String I_STATE_STOPPING = "BS1503";
    String I_STATE_STOPPED = "BS1504";
    String I_STATE_PAUSING = "BS1505";
    String I_STATE_PAUSED = "BS1506";
    String I_STATE_RESUMING = "BS1507";

}
