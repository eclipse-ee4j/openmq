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

package com.sun.messaging.ums.readonly.impl;

import com.sun.messaging.ums.readonly.DefaultReadOnlyService;
import com.sun.messaging.ums.readonly.ReadOnlyMessageFactory;
import com.sun.messaging.ums.readonly.ReadOnlyRequestMessage;
import com.sun.messaging.ums.readonly.ReadOnlyResponseMessage;
import com.sun.messaging.ums.readonly.ReadOnlyService;
import com.sun.messaging.ums.service.UMSServiceException;
import com.sun.messaging.ums.service.UMSServiceImpl;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author chiaming
 */
public class ping implements ReadOnlyService {
    
    private Properties initParams = null;
    private static long seq = 0;
    
    /**
     * initialize with the servlet init params.
     * @param props
     */
    public void init(Properties initParams) {
        this.initParams = initParams;
    }
    
    public ReadOnlyResponseMessage request (ReadOnlyRequestMessage request) {
        
        try {
            
            String respMsg = null;
            
            Map map = request.getMessageProperties();
            
            String destName = "PING_"+  nextSequence() + "_" + System.currentTimeMillis();
            String msg = destName;
            
            UMSServiceImpl service = (UMSServiceImpl) this.initParams.get(DefaultReadOnlyService.JMSSERVICE);
            
            long start = System.currentTimeMillis();
            
            service.sendText(null, false, destName, msg, map);
            
            String msg2 = service.receiveText(null, destName, false, 30000, map);
            
            long end = System.currentTimeMillis();
            
            if (msg2 != null) {
                respMsg = "Broker is alive, round trip = " + (end - start) + " milli secs.";
            } else {
                respMsg = "Broker is not responding for more than 30 seconds.";
            }
            
            ReadOnlyResponseMessage response = ReadOnlyMessageFactory.createResponseMessage();
            
            response.setResponseMessage(respMsg);
            
            return response;
            
        } catch (Exception e) {

            UMSServiceException umse = new UMSServiceException(e);

            throw umse;
        }
    }
    
    private static synchronized long nextSequence() {
        return ++seq;
    }

}
