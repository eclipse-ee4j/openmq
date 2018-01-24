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

package com.sun.messaging.jms.ra.api;

public class JMSRAResourceAdapter 
{

	/**
	 * Specifies whether the XAResource implementations provided by this RA
	 * should return isSameRM()=true if the resources are capable of being
	 * joined.
	 * 
	 * This field is initialised from a system property in ResourceAdapter.start()
	 * 
	 */
	private static boolean isSameRMAllowed = true;

	/**
	 * Specifies whether we should revert to the original behaviour rather than
	 * apply the workarounds implemented in RFE 6882044 in which various calls
	 * to XAResource.start() and XAResource.end() were not sent to the broker.
	 */
	private static boolean isRevert6882044 = false;

	public static void init() {

		isSameRMAllowed = Boolean.valueOf(System.getProperty("imq.jmsra.isSameRMAllowed", "true"));

		isRevert6882044 = Boolean.valueOf(System.getProperty("imq.jmsra.isRevert6882044", "false"));
        }

	/**
	 * Returns whether the XAResource implementations provided by this RA should
	 * return isSameRM()=true if the resources are capable of being joined.
	 * 
	 * @return
	 */
	public static boolean isSameRMAllowed() {
		return isSameRMAllowed;
	}

	/**
	 * Return whether we should revert to the original behaviour rather than
	 * apply the workarounds implemented in RFE 6882044 in which various calls
	 * to XAResource.start() and XAResource.end() were not sent to the broker.
	 * 
	 * @return
	 */
	public static boolean isRevert6882044() {
		return isRevert6882044;
	}

}
