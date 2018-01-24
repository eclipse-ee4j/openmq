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
 * @(#)DedicatedServiceFactory.java	1.5 06/29/07
 */ 

package com.sun.messaging.jmq.jmsserver.service.imq.dedicated;


import java.io.IOException;
import com.sun.messaging.jmq.jmsserver.service.imq.*;
import com.sun.messaging.jmq.jmsserver.net.*;
import com.sun.messaging.jmq.jmsserver.data.PacketRouter;


public class DedicatedServiceFactory extends IMQIPServiceFactory
{

    @Override
    public void checkFactoryHandlerName(String handlerName)
    throws IllegalAccessException {
        String myname = "dedicated";
        if (!myname.equals(handlerName)) {
            throw new IllegalAccessException(
            "Unexpected service Handler name "+handlerName+", expected "+myname);
        }
    }

    protected IMQService createService(String instancename, 
           Protocol proto, PacketRouter router, int type, 
           int min, int max)
        throws IOException
    {
/* LKS
        proto.configureBlocking(true);
*/

        return new DedicatedService(instancename, proto, type, router,
              min, max);
    }

    

}
/*
 * EOF
 */
