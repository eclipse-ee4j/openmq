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
 * @(#)JMQFileUserRepository.java	1.34 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.file;

import java.io.*;
import java.util.*;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.util.MD5;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.jaas.MQGroup;
import com.sun.messaging.jmq.auth.jaas.MQAdminGroup;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.auth.api.server.model.*;

/**
 * JMQ passwd user repository plugin
 */
public class JMQFileUserRepository implements UserRepository
{
    public static final String TYPE = "file";

    public static final String PROP_FILENAME_SUFFIX = TYPE + ".filename";
    public static final String PROP_DIRPATH_SUFFIX = TYPE + ".dirpath";
    public static final String DEFAULT_PW_FILENAME = "passwd";

    private static boolean DEBUG = false;
	private transient Logger logger = Globals.getLogger();

    private static String ADMINGROUP = "admin";
    private String authType;
    private Properties authProps = null;

    public JMQFileUserRepository() { }

    public String getType() {
        return TYPE;
    }

    public void open(String authType, Properties authProperties,
                     Refreshable cacheData) throws LoginException {
        this.authType = authType;
        this.authProps = authProperties;
    }

    /**
     * Find the user in the repository and compare the credential with
     * the user's  credential in database
     *
     * @param user the user name
     * @param credential password (String type) for "basic" is the password
     * @param extra null for basic, nonce if digest
     * @param matchType must be "basic" or "digest"
     *        
     * @return the authenticated subject  <BR>
     *         null if no match found <BR>
     *
     * @exception LoginException
     */
    public Subject findMatch(String user, Object credential,
                             Object extra, String matchType)
                             throws LoginException {
        if (matchType == null ||
            (!matchType.equals(AccessController.AUTHTYPE_BASIC) &&
             !matchType.equals(AccessController.AUTHTYPE_DIGEST))) {
            String matchtyp = (matchType == null) ? "null": matchType;
            String[] args = {matchtyp, authType, getType(),
                AccessController.AUTHTYPE_BASIC+":"+AccessController.AUTHTYPE_DIGEST};
            throw new LoginException(Globals.getBrokerResources().getKString(
                BrokerResources.X_UNSUPPORTED_USER_REPOSITORY_MATCHTYPE, args));
        }

        HashMap userPTable = new HashMap();
        HashMap userRTable = new HashMap();
        try {
            loadUserTable(userPTable, userRTable);
        } catch (IOException e) {
            logger.logStack(logger.ERROR, e.getMessage(), e);
            userPTable = null;
            userRTable = null;
            throw new LoginException(e.getMessage());
        }

        Subject subject = null; 
        if (matchType.equals(AccessController.AUTHTYPE_BASIC)) {
            subject = basicFindMatch(user, (String)credential, userPTable, userRTable);
        }
        else if (matchType.equals(AccessController.AUTHTYPE_DIGEST)) {
            subject = digestFindMatch(user, (String)credential, (String)extra, userPTable, userRTable); 
        }
        userPTable = null;
        userRTable = null;
        return subject;
    }
      
    private Subject basicFindMatch(String user, String userpwd, 
                      HashMap userPTable, HashMap userRTable) throws LoginException {
        Subject subject = null;
        String passwd = (String)userPTable.get(user);
        if (DEBUG) {
           logger.log(Logger.INFO, "basicFindMatch(user="+
               user+",userpwd="+userpwd+"), passwd="+passwd);
        }
        if (passwd != null) {
            String passwdhash = MD5.getHashString(user+":"+userpwd);
            if (passwd.equals(passwdhash)) {
                subject = getSubject(user, userRTable);
            } else {
                if (DEBUG) {
                    logger.log(Logger.INFO, "basicFindMatch(user="+
                        user+",userpwd="+userpwd+"), passwdhash="+passwdhash);
                }
            }
        }
        return subject;
    }

    private Subject digestFindMatch(String user, String credential, String nonce,
                      HashMap userPTable, HashMap userRTable) throws LoginException {
        Subject subject = null;
        String passwd = (String)userPTable.get(user);
        if (DEBUG) {
            logger.log(Logger.INFO, "digestFindMatch(user="+user+
                ",credential="+credential+",nonce="+nonce+"), passwd="+passwd);
        }
        if (passwd != null) {
            String passwdhash = MD5.getHashString(passwd+":"+nonce);
            if (credential.equals(passwdhash)) {
                subject =  getSubject(user, userRTable);
            } else {
                if (DEBUG) {
                    logger.log(Logger.INFO, "digestFindMatch(user="+user+
                    ",credential="+credential+",nonce="+nonce+"), passwdhash="+passwdhash);
                }
            }
        }
        return subject;
    }
            
    private Subject getSubject(String user, HashMap userRTable) {  
        Subject subject = null;
        final String rolestr = (String)userRTable.get(user);
        final String tempUser = user;
        subject = (Subject) java.security.AccessController.doPrivileged(
                new PrivilegedAction<Object>() {
                    public Object run(){
                        Subject tempSubject = new Subject();
                        tempSubject.getPrincipals().add(new MQUser(tempUser));
                        if (rolestr != null && !rolestr.trim().equals("")) {
                            tempSubject.getPrincipals().add(new MQGroup(rolestr));
                        }
                        if (rolestr != null && rolestr.equals(ADMINGROUP)) {
                            tempSubject.getPrincipals().add(
                                    new MQAdminGroup(ADMINGROUP));
                        }
                        return tempSubject;
                    }
                }
            );
        return subject;
    }

    private void loadUserTable(HashMap userPTable, HashMap userRTable) throws IOException {
        String rep = authProps.getProperty(
                         AccessController.PROP_AUTHENTICATION_PREFIX +
                         authType +AccessController.PROP_USER_REPOSITORY_SUFFIX);
        if (rep == null) {
            throw new IOException(Globals.getBrokerResources().getKString(
                BrokerResources.X_USER_REPOSITORY_NOT_DEFINED, authType));
        }
        if (!rep.equals(TYPE)) {
            String[] args = {rep, TYPE, this.getClass().getName()};
            throw new IOException(Globals.getBrokerResources().getKString(
                      BrokerResources.X_REPOSITORY_TYPE_MISMATCH, args));
        }

        File pwdfile = getPasswordFile(authProps, false);

        InputStreamReader fr = null;
        BufferedReader br = null;

        try {
        fr = new InputStreamReader(new FileInputStream(pwdfile), "UTF8");
        br = new BufferedReader(fr);

        String line, name, passwd, role, active;
        while ((line = br.readLine()) != null) {
            name = passwd = role = active = null;
            StringTokenizer st = new StringTokenizer(line, ":", false);
            if (st.hasMoreTokens()) {
                name = st.nextToken(); 
            }
            if (st.hasMoreTokens()) {
                passwd = st.nextToken(); 
            }
            if (st.hasMoreTokens()) {
                role = st.nextToken(); 
                if (role != null && role.trim().length() == 0) {
                    role = null;
                }
            }
            if (st.hasMoreTokens()) {
                active = st.nextToken(); 
            }
            if (DEBUG) {
            logger.log(Logger.INFO, "passwd entry "+name+":"+passwd+":"+role+":"+active);
            }
            if (name !=null && passwd != null &&
                active != null && active.equals("1")) {
                userPTable.put(name, passwd);
                if (role != null) {
                    userRTable.put(name, role);
                }
            }
        }

        br.close();
        fr.close();

        } catch (IOException ioe) {
        try {
        if (br != null) br.close();
        if (fr != null) fr.close();
        } catch (IOException e) {}
        IOException ex = new IOException(Globals.getBrokerResources().getKString(
                         BrokerResources.E_PW_FILE_READ_ERROR, pwdfile.toString(),
                         ioe.getMessage()));
        ex.initCause(ioe);

        throw ex;
        }
    }

    public Refreshable getCacheData() {
       return null;  
    }

    public void close() throws LoginException { }

    public static String getPasswordDirPath(Properties props, boolean fromUserManager) {
        String passwd_loc = props.getProperty(
                            AccessController.PROP_USER_REPOSITORY_PREFIX
                            +PROP_DIRPATH_SUFFIX, Globals.getInstanceEtcDir());
        if (fromUserManager) {
            passwd_loc = StringUtil.expandVariables(passwd_loc, props);
        }
        return passwd_loc;
    }

    public static File getPasswordFile(Properties props, boolean fromUserManager) {
        String passwd_loc = getPasswordDirPath(props, fromUserManager);
        String f = props.getProperty(
                   AccessController.PROP_USER_REPOSITORY_PREFIX
                   +PROP_FILENAME_SUFFIX, DEFAULT_PW_FILENAME);

        String pwdfile = passwd_loc +File.separator + f;
        return new File(pwdfile);
    }
}
