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
 * @(#)DataEnumeration.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.persist.api.util;

import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import java.util.*;

/**
 * DataEnumeration represent an enumeration of some types of data.
 */
public class DataEnumeration implements Enumeration {

    private EnumerationStore store;
    private Object[] ids;
    private int index = 0;
    private Object next = null;

    public DataEnumeration(EnumerationStore store, Object[] ids) {
	if (ids == null)
	    ids = new Object[0];

	this.store = store;
	this.ids = ids;
    }

    public boolean hasMoreElements() {
	if (index >= ids.length)
	    return false;

	for (; index < ids.length && next == null; index++) {
	    try {
		next = store.getData(ids[index]);
	    } catch (BrokerException e) {
		// get as much as we can
		next = null;
	    }
	}

	if (next != null)
	    return true;
	else
	    return false;
    }

    public Object nextElement() {
	Object data = next;
	if (data != null) {
	    next = null;
	    return data;
	} else {
	    throw new NoSuchElementException();
	}
    }
}

