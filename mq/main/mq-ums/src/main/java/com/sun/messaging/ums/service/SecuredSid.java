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

import com.sun.messaging.jmq.util.BASE64Decoder;
import com.sun.messaging.jmq.util.BASE64Encoder;
//import java.io.IOException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
//import java.security.SignatureException;
import java.util.UUID;
import java.util.logging.Logger;
import javax.jms.JMSException;

/**
 *
 * @author chiaming
 */
public class SecuredSid {
    
    private PrivateKey privateKey = null;
    
    private PublicKey publicKey = null;
    
    private Signature signer = null;
    
    private Signature verifier = null;
    
    private SecureRandom srandom = null;
    
    private long sequence = 0;
    
    private static BASE64Encoder encoder = null;
    
    private static BASE64Decoder decoder = null;
    
    private Logger logger = UMSServiceImpl.logger;
    
    private static final String UTF8 = "UTF-8";
    
    
    static {
        encoder = new BASE64Encoder();
        decoder = new BASE64Decoder();
    }
    
    public SecuredSid() throws JMSException {
        try {
            init();
        } catch (Exception e) {
            JMSException jmse = new JMSException (e.getMessage());
            jmse.setLinkedException(e);
            
            throw jmse;
        }
    }
    
    private void init() throws NoSuchAlgorithmException, InvalidKeyException {
        
        srandom = SecureRandom.getInstance("SHA1PRNG");
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");

        keyGen.initialize(1024, srandom);
    
        KeyPair pair = keyGen.generateKeyPair();
        
        //my private key to sign sid
        privateKey = pair.getPrivate();
        
        //my pub key to verify sid signature
        publicKey = pair.getPublic();
        
        signer = Signature.getInstance("SHA1withDSA"); 
        
        verifier = Signature.getInstance("SHA1withDSA");
        
        //init signature object -- i am ready to sign
        signer.initSign(privateKey);
        
        verifier.initVerify(this.publicKey);
    
    }
    
    /**
     * sid = sequence + "-" + (signature of sequence)
     * 
     * 
     * 
     * @return
     * @throws javax.jms.JMSException
     */
    public synchronized String nextSid() throws JMSException {
        
        String sid = null;
        
        try {
        //1. generate a secure random - 20 bytes
        //byte[] bytes = new byte[20];
        //this.srandom.nextBytes(bytes);
       
        if (this.sequence == Long.MAX_VALUE) {
            this.sequence = 0;
        }    
        
        this.sequence ++;
       
        //unique string in my domain
        String prefix = String.valueOf(sequence);
           
        //byte[] data = prefix.getBytes(UTF8);
        
        //This makes the original string hard to guess
        byte[] data = UUID.randomUUID().toString().getBytes(UTF8);
        
        //update what to sign
        signer.update(data);
        
        //sign data -- a secured string
        byte[] signature = this.signer.sign();
        
        //encode to base64
        String sigstr = encoder.encode(signature);
        
        //use signature hash
        int hash = sigstr.hashCode();
        byte[] scode = Integer.toString(hash).getBytes(UTF8);
        sigstr = encoder.encode(scode);
        //end hash
        
        //compose sid -- the sid is unique and secure!
        sid = prefix + "-" + sigstr;
        
        } catch (Exception e) {
            JMSException jmse = new JMSException (e.getMessage());
            jmse.setLinkedException(e);
            
            throw jmse;
        }
        
        if (UMSServiceImpl.debug) {
            logger.info("**** sid =" + sid + ", size=" + sid.length());
        }
        
        return sid;
    }
    
    public synchronized void verifySid (String sid) throws JMSException {
           
        try {
            
            //get sequence index
            int index = sid.indexOf('-');
            
            //get sequence
            String seq = sid.substring(0, index);
            
            index ++;
            
            //get signature string - base 64
            String sigstr = sid.substring(index);
            
            if (UMSServiceImpl.debug) {
                 logger.info ("*** verifying sid, seq=" + seq + ",sig=" + sigstr + ", len=" + sigstr.length());
            }
            
            //get sequence bytes
            byte[] data = seq.getBytes(UTF8);
            
            //update what to verify
            this.verifier.update(data, 0, data.length);
            
            //decode signature from base64 to byte[]
            byte[] signature = decoder.decodeBuffer(sigstr);
            
            //verify signature
            boolean isvalid = this.verifier.verify(signature, 0, signature.length);
            
            if (isvalid == false) {
                throw new SecurityException ("Invalid sid., sid = " + sid);
            }
            
            if (UMSServiceImpl.debug) {
                logger.info ("*** sid is verified:" + isvalid + ", seq=" + seq + ",sig=" + sigstr);
            }
            
            //return isvalid;
            
        } catch (Exception e) {
            
            e.printStackTrace();
            JMSException jmse = new JMSException (e.getMessage());
            
            jmse.setLinkedException(e);
            
            throw jmse;
        }    
    }
    
    public static String decode (String encodedString) throws JMSException {
        
        try {
        
            byte[] data = decoder.decodeBuffer(encodedString);
        
            String plain = new String (data, UTF8);
            
            return plain;
            
        } catch (IOException e) {
            
            JMSException jmse = new JMSException (e.getMessage());
            
            jmse.setLinkedException(e);
            
            throw jmse;
        }
    }
    
    public static void main (String[] args) throws Exception {
        
        SecuredSid ssid = new SecuredSid();
        
        for (int i=0; i<1; i++) {
            String sid = ssid.nextSid();
            System.out.println ("**** sid = " + sid);
            
            //sid = sid + 1;
            //sid = 1 + sid;
            
            //int index = sid.indexOf('-');
            
            //get sequence
            //String sequence = sid.substring(0, index);
            
            //index ++;
            
            //get signature string - base 64
            //String sigstr = sid.substring(index);
            
            //sigstr = 1+sigstr;
            
            //String badsid = sequence + "-" + sigstr;
            
            //ssid.verifySid(sid);  
            //ssid.verifySid(badsid);
            
            //Thread.sleep (100);
            //System.out.println ("**** sid verified, sid= " + sid);
        }
        
    }
    

}
