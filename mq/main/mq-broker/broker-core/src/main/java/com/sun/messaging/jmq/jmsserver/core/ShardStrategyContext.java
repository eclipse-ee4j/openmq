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
 */ 

package com.sun.messaging.jmq.jmsserver.core;

import com.sun.messaging.jmq.jmsserver.persist.api.PartitionedStore;

/**
 * to be extended
 */
public interface ShardStrategyContext
{
    /**
     * @return the PartitionedStore associated with this context
     */
    public PartitionedStore getPartitionedStore();

    /**
     * @return the number of connections currently assigned to this context
     */
    public int getConnectionCount();

    /**
     * @return the number persistent messages in this context
     */
    public long getPersistMessageCount();
}
