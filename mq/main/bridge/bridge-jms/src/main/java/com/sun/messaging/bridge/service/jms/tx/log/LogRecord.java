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

package com.sun.messaging.bridge.service.jms.tx.log;

import java.io.*;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Collection;
import com.sun.messaging.bridge.service.jms.tx.GlobalXid;
import com.sun.messaging.bridge.service.jms.tx.BranchXid;
import com.sun.messaging.bridge.service.jms.tx.XAParticipant;


/**
 * @author amyk
 */ 

public class LogRecord implements Externalizable {

    private GlobalXidDecision _gxidd = null;
    private BranchXidDecision[] _bxidds = null;

    public LogRecord() {}

    public LogRecord(GlobalXid gxid, 
                     Collection<XAParticipant> parties,
                     int decision)
                     throws Exception {

        _gxidd = new GlobalXidDecision(gxid, decision);

        _bxidds = new BranchXidDecision[parties.size()];
        XAParticipant party = null;
        Iterator<XAParticipant> itr = parties.iterator();
        int i = 0;
        while( itr.hasNext()) {
            party = itr.next();
            _bxidds[i] = new BranchXidDecision(party.getBranchXid(), decision);
            i++;
        }
    }

    public GlobalXid getGlobalXid() {
        return _gxidd.getGlobalXid();
    }

    public int getGlobalDecision() {
        return _gxidd.getGlobalDecision();
    }

    public BranchXidDecision[] getBranchXidDecisions() {
        return _bxidds;
    }

    public int getBranchCount() {
        return _bxidds.length;
    }

    public void setBranchHeurCommit(BranchXid bxid) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                _bxidds[i].setBranchDecision(BranchXidDecision.HEUR_COMMIT);
            }
        }
        throw new NoSuchElementException("Branch "+bxid+" not found in "+_gxidd); 
    }

    public void setBranchHeurRollback(BranchXid bxid) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                _bxidds[i].setBranchDecision(BranchXidDecision.HEUR_ROLLBACK);
            }
        }
        throw new NoSuchElementException("Branch "+bxid+" not found in "+_gxidd); 
    }

    public void setBranchHeurMixed(BranchXid bxid) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                _bxidds[i].setBranchDecision(BranchXidDecision.HEUR_MIXED);
            }
        }
        throw new NoSuchElementException("Branch "+bxid+" not found in "+_gxidd); 
    }

    public boolean isHeuristicBranch(BranchXid bxid) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                return (_bxidds[i].isHeuristic() || (_bxidds[i].getBranchDecision() != _gxidd.getGlobalDecision()));
            }
        }
        throw new NoSuchElementException(
        "Branch "+bxid+" not found in global transaction "+_gxidd); 
    }

    public int getBranchDecision(BranchXid bxid) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
               return _bxidds[i].getBranchDecision();
            }
        }
        throw new NoSuchElementException("Branch "+bxid+" not found in "+_gxidd); 
    }

    public void setBranchDecision(BranchXid bxid, int d) throws Exception {
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                _bxidds[i].setBranchDecision(d);
            }
        }
        throw new NoSuchElementException("Branch "+bxid+" not found in "+_gxidd); 
    }

    protected void updateClientDataFromBranch(byte[] cd, BranchXid bxid) throws Exception { 
        for (int i = 0; i < _bxidds.length; i++) {
            if (_bxidds[i].getBranchXid().equals(bxid)) {
                cd[i] = (byte) _bxidds[i].getBranchDecision();
            }
        }
        throw new NoSuchElementException(
        "Branch "+bxid+" not found in global transaction "+_gxidd); 
    }

    protected void updateBranchFromClientData(byte[] cd) throws Exception { 
        for (int i = 0; i < _bxidds.length; i++) {
             _bxidds[i].setBranchDecision(cd[i]);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {

        out.writeObject(_gxidd);
        out.writeObject(_bxidds);
    }

    public void readExternal(ObjectInput in) throws IOException,
                                        ClassNotFoundException {

        _gxidd = (GlobalXidDecision)in.readObject();
        _bxidds = (BranchXidDecision[])in.readObject();
        for (int i = 0; i < _bxidds.length; i++) { 
            _bxidds[i].getBranchXid().setFormatId(
                       _gxidd.getGlobalXid().getFormatId());
            _bxidds[i].getBranchXid().setGlobalTransactionId(
                       _gxidd.getGlobalXid().getGlobalTransactionId());
        }
    }

    public byte[] toBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        ObjectOutputStream bos = new ObjectOutputStream(baos);
        bos.writeObject(this);
        bos.close();

        return baos.toByteArray();
    }

    public String toString() {

        StringBuffer sb = new StringBuffer();
        int i = 0;
        for (i = 0; i <_bxidds.length; i++) {
            if (i == 0) sb.append("[");
            if (i > 0) sb.append(", ");
            sb.append(_bxidds[i].toString());
            sb.append(_bxidds[i].toString());
        }
        if (i > 0) sb.append("]");
        return _gxidd.toString()+sb.toString();
    }
}
