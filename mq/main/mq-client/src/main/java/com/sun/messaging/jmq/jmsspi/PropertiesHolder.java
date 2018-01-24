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

package com.sun.messaging.jmq.jmsspi;

import java.util.Properties;

/**
 * This interface provides a convenient way of passing a Properties object into a JMSAdmin
 * which can be regenerated (possibly giving different property names and values)
 * at a later stage. Whenever getProperties() is called, the properties
 * are regenerated and returned to the caller.
 */
public interface PropertiesHolder {
	
	/**
	 * Regenerate the Properties associated with this PropertiesHolder
	 * and return them to the caller
	 * @return
	 */
	public Properties getProperties();

}
