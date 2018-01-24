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

package com.sun.messaging.ums.readonly;

import com.sun.messaging.ums.simple.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 *
 * @author chiaming
 */
public class ReadOnlyMessageFactory {
         
    public static ReadOnlyRequestMessage 
            createRequestMessage (Map props, InputStream in) throws IOException {
        
        String body = SimpleMessageFactory.readHttpBody(props, in);
        
        ReadOnlyRequestMessage message = new ReadOnlyRequestMessage(props, body);
        
        
        return message;
    }
    
    public static ReadOnlyResponseMessage createResponseMessage () {
        
        ReadOnlyResponseMessage message = new ReadOnlyResponseMessage();
        
        return message;
    }
   
}
