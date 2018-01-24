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

package com.sun.messaging.ums.service;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 *
 * This class is not used yet.  
 * 
 * @author chiaming
 */
public class ClientTable {
    
    private Hashtable<String, Client> clients = new Hashtable <String, Client>();
    
    public void put(String sid, Client client) {

        //String seq = this.getSequence(sid);
        this.getSequence(sid);
        
        clients.put(sid, client);
    }
    
    private String getSequence (String sid) {
        //get sequence index
        int index = sid.indexOf('-');

        //get sequence
        String seq = sid.substring(0, index);
        
        return seq;
    }
    
    public Client get (String sid) {
        
        String seq = this.getSequence(sid);
        
        Client client = clients.get(seq);
        
        return client;
    }
    
    public int size() {
        return clients.size();
    }
    
    public Collection values() {
        return clients.values();
    }
    
    public void clear() {
        clients.clear();
    }
    
    public Client remove (String sid) {
        
        Client client = clients.remove(sid);
        
        return client;
    }
    
    public Enumeration keys() {
        return clients.keys();
    }

}
