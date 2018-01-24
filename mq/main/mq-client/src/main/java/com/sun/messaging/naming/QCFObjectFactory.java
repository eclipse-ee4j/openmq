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
 * @(#)QCFObjectFactory.java	1.5 07/02/07
 */ 

package com.sun.messaging.naming;

/**
 * <code>QCFObjectFactory</code> is the class named in
 * <code>javax.naming.Reference</code> objects created from JMQ1.1
 * <code>com.sun.messaging.QueueConnectionFactory</code> objects.
 * <p>
 * The parent class, <code>com.sun.messaging.naming.CFObjectFactory</code>
 * handles the generation of the current version of
 * <code>com.sun.messaging.QueueConnectionFactory</code> objects along
 * with any conversions required.
 * <p>
 * @since JMQ1.1
 */
 
public final class QCFObjectFactory extends CFObjectFactory {

}

