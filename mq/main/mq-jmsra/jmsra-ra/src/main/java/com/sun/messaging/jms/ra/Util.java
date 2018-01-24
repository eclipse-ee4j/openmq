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

package com.sun.messaging.jms.ra;

import javax.resource.spi.security.PasswordCredential;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.Set;

/**
 *  Util methods for the Sun MQ Resource Adapter for JMS
 */

public class Util
{
    /** Disable Constructor */
    private Util() {}

    /** Generic equals method
     *
     *  @param a object 1
     *  @param b object 2
     *
     *  @return true if objects a and b are equal;
     *          false otherwise
     */
    static public boolean
    isEqual(Object a, Object b)
    {
        if (a == null) {
            return (b == null);
        } else {
            return a.equals(b);
        }
    }

    /** Checks two PassWordCredential instances
     *  for equality
     *
     *  @param a PasswordCredential 1
     *  @param b PasswordCredential 2
     *
     *  @return true if PasswordCredential a and
     *          PasswordCredential b are equal;
     *          false otherwise
     */
    static public boolean
    isPasswordCredentialEqual(PasswordCredential a, PasswordCredential b)
    {
        if (a == b) {
            return true;
        }
        if ((a == null) && (b != null)) {
            return false;
        }
        if ((a != null) && (b == null))
            return false;
        if (!isEqual(a.getUserName(), b.getUserName())) {
            return false;
        }
 
        String p1 = null;
        String p2 = null;
 
        if (a.getPassword() != null) {
            p1 = new String(a.getPassword());
        }
        if (b.getPassword() != null) {
            p2 = new String(b.getPassword());
        }
        return (isEqual(p1, p2));
    }

    /** Returns a PasswordCredential by resolving
     *  the ManagedConnectionFactory, Subject, and
     *  ConnectionRequestInfo passed in.
     * 
     *  If the Subject is non-null, non-empty, it will be the src
     *  of the pc info.
     *  else, if it is empty, the MCF should be used
     *  else if it null, the CRI, is used if non-empty
     *  else the MCF is used.
     *
     *  @return The PasswordCredential
     */
    static public PasswordCredential
    getPasswordCredential(
        final com.sun.messaging.jms.ra.ManagedConnectionFactory mcf,
        final Subject subject,
        com.sun.messaging.jms.ra.ConnectionRequestInfo myinfo)
    throws javax.resource.ResourceException
    {
        String username2use = null;
        String password2use = null;

        PasswordCredential pc = null;

        //System.out.println("MQRA:U:getPC()-subject="+subject+":CRInfo="+myinfo);
        if (subject != null) {
            //System.out.println("MQRA:U:getPC:non-null subject");
            pc = (PasswordCredential)
                AccessController.doPrivileged(
                    new PrivilegedAction<Object>()
                    {
                        public Object
                        run()
                        {
                            Set creds = subject.getPrivateCredentials
                                (PasswordCredential.class);
                            if (creds == null) {
                                //System.out.println("MQRA:U:getPC:null creds-return null pc");
                                return null;
                            }
                            Iterator iter = creds.iterator();
                            while (iter.hasNext()) {
                                PasswordCredential temp =
                                    (PasswordCredential) iter.next();
                                if (temp != null) {
                                    //System.out.println("MQRA:U:getPC:pwc from subject="+temp.toString());
                                    //Sufficient if username is non-null; do not retrieve the pw
                                    if (temp.getUserName() != null) {
                                        //System.out.println("MQRA:U:getPC:un+pw exist;return pwc="+temp.toString());
                                        return temp;
                                    }
                                }
                            }
                            //System.out.println("MQRA:U:getPC:null or empty subject-return null pc");
                            return null;
                        }
                    }
                );
        }
        // Return only if a valid PasswordCredential is obtained
        if (pc != null) {
            //System.out.println("MQRA:U:getPC:-returning real pc from Subject");
            return pc;
        } else {
        // else need to construct a pc from CRI or MCF
            if (myinfo != null) {
                if (myinfo.getUserName() != null) {
                    //System.out.println("MQRA:U:getPC():non-null CRI:creating pwc from CRI");
                    username2use = myinfo.getUserName();
                    password2use = myinfo.getPassword();
                } else {
                   //System.out.println("MQRA:U:getPC():non-null CRI BUT un==null:creating pwc from MCF");
                   username2use = mcf.getUserName();
                   password2use = mcf.getPassword();
                }
            } else {
            // need to construct a pc from MCF
                //System.out.println("MQRA:U:getPC():null CRI:creating pwc from MCF");
                username2use = mcf.getUserName();
                password2use = mcf.getPassword();

            }
            char [] password = password2use.toCharArray();
            pc = new PasswordCredential(username2use, password);
            //System.out.println("MQRA:U:getPC:-returning pc from CRI/MCF");
            return pc;
        }
    }


    /** Returns whether a Subject has a valid PasswordCredential
     *  or not. A valid PWC is a non-null, non-empty username in the PWC
     *  of the subject
     *  
     *  @return true if subject has a valid PWC; false otherwise
     */
    static public boolean
    isPasswordCredentialValid(final Subject subject)
    throws javax.resource.ResourceException
    {
        if (subject == null) {
            return false;
        }
        Boolean pwcValid = (Boolean) AccessController.doPrivileged(
            new PrivilegedAction<Object>()
            {
                public Object
                run()
                {
                    Set creds = subject.getPrivateCredentials(PasswordCredential.class);
                    if (creds == null) {
                        return Boolean.valueOf(false);
                    }
                    Iterator iter = creds.iterator();
                    String un;
                    while (iter.hasNext()) {
                        PasswordCredential temp = (PasswordCredential) iter.next();
                        if (temp != null) {
                            un = temp.getUserName();
                            if (un != null && !("".equals(un))) {
                                return Boolean.valueOf(true);
                            }
                        }
                    }
                    return Boolean.valueOf(false);
                }
            }
        );
        return pwcValid.booleanValue();
    }


    /** Returns a PasswordCredential by resolving
     *  the ManagedConnectionFactory, Subject, and
     *  ConnectionRequestInfo passed in.
     * 
     *  If the Subject is non-null, non-empty, it will be the src
     *  of the pc info.
     *  else, if it is empty, the MCF should be used
     *  else if it null, the CRI, is used if non-empty
     *  else the MCF is used.
     *
     *  @return The PasswordCredential
     */
    static public PasswordCredential
    getPasswordCredentialOld(
        final com.sun.messaging.jms.ra.ManagedConnectionFactory mcf,
        final Subject subject,
        com.sun.messaging.jms.ra.ConnectionRequestInfo myinfo)
    throws javax.resource.ResourceException
    {
        //System.out.println("MQRA:U:getPC()-"+subject+":CRInfo="+myinfo);
        if (subject == null) {
            //System.out.println("MQRA:U:getPC:-null subject");
            if (myinfo == null) {
                //System.out.println("MQRA:U:getPC:-no crinfo;returning null");
                return null;
            } else {
                // Can't create a PC with null values
                if (myinfo.getUserName() == null || myinfo.getPassword() == null) {
                    //System.out.println("MQRA:U:getPC()-null un+pw;returning null");
                    return null;
                }
                char [] password = myinfo.getPassword().toCharArray();
 
                PasswordCredential pc =
                    new PasswordCredential(myinfo.getUserName(), password);
 
                pc.setManagedConnectionFactory((javax.resource.spi.ManagedConnectionFactory)mcf);
                //System.out.println("MQRA:U:getPC:-returning real pc");
                return pc;
            }
        } else {
            //System.out.println("MQRA:U:getPC:non-null subject");
            PasswordCredential pc = (PasswordCredential)
                AccessController.doPrivileged(
                    new PrivilegedAction<Object>()
                    {
                        public Object
                        run()
                        {
                            Set creds = subject.getPrivateCredentials
                                (PasswordCredential.class);
                            Iterator iter = creds.iterator();
                            while (iter.hasNext()) {
                                PasswordCredential temp =
                                    (PasswordCredential) iter.next();
                                //if (temp != null) {
                                    //System.out.println("MQRA:U:getPC:pwc from subject="+temp.toString());
                                //}
                                if (temp != null && temp.getManagedConnectionFactory() != null &&
                                    temp.getManagedConnectionFactory().equals(mcf)) {
                                    //System.out.println("MQRA:U:getPC:mcf == subject mcf-return real pc");
                                    return temp;
                                }
                            }
                            //System.out.println("MQRA:U:getPC:mcf != subject mcf-return null pc");
                            return null;
                        }
                    }
                );
            if (pc == null) {
                //System.out.println("MQRA:U:getPC:null pc;throw exc-null credentials");
                throw new javax.resource.spi.SecurityException("MQRA:U:getPC:Null credentials");
            } else {
                //System.out.println("MQRA:U:getPC():returning a valid pc");
                return pc;
            }
        }
    }

    public static Object jndiLookup(String jndiName) throws NamingException {
        InitialContext ic = null;
        Object obj = null;
        try {
            ic = new InitialContext();
            obj = ic.lookup(jndiName);
        } finally {
            if (ic != null) {
                try {
                    ic.close();
                } catch (Exception e) {}
            }
        }
        return obj;
    }
}

