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
 * @(#)LdapUserRepository.java	1.29 09/07/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.ldap;

import java.io.*;
import java.util.*;
import java.security.PrivilegedAction;
import javax.naming.Context;
import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.directory.SearchControls;
import javax.naming.directory.Attribute;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.naming.directory.Attributes;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.auth.jaas.MQUser;
import com.sun.messaging.jmq.auth.jaas.MQGroup;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.net.tls.TLSProtocol;
import com.sun.messaging.jmq.auth.api.server.model.*;
import com.sun.messaging.jmq.util.Password;

/**
 *
 */
public class LdapUserRepository implements UserRepository
{
    public static final String TYPE = "ldap";

    private static boolean DEBUG = false;
	private transient static final Logger logger = Globals.getLogger();
	private transient static final BrokerResources br = Globals.getBrokerResources();

    private String authType;
    private Properties authProps = null;

    private static String INITIAL_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    //private static String DEFAULT_SSLFACTORY = "javax.net.ssl.SSLSocketFactory";
    //private static String TRUST_SSLFACTORY = "com.sun.messaging.jmq.jmsserver.auth.ldap.TrustSSLSocketFactory"; 
    private static final int DEFAULT_TIMELIMIT = 180000; //milliseconds

	private static final String DN_USRFORMAT = "dn";

    private final static String PROP_SERVER_SUFFIX  =".server";
    public final static String PROP_BINDDN_SUFFIX  =".principal";
    public final static String PROP_BINDPW_SUFFIX  =".password";
    private final static String PROP_UIDATTR_SUFFIX =".uidattr";
	private final static String PROP_USRFORMAT_SUFFIX =".usrformat";
    private final static String PROP_USRFILTER_SUFFIX =".usrfilter";
    private final static String PROP_BASE_SUFFIX    =".base";
    private final static String PROP_GRPBASE_SUFFIX =".grpbase";
    private final static String PROP_GIDATTR_SUFFIX =".gidattr";
    private final static String PROP_MEMATTR_SUFFIX =".memattr";
    private final static String PROP_GRPFILTER_SUFFIX =".grpfilter";
    private final static String PROP_GRPSEARCH_SUFFIX =".grpsearch";
    private final static String PROP_TIMEOUT_SUFFIX   =".timeout";
    private final static String PROP_SSL_SUFFIX =".ssl.enabled";
    private final static String PROP_SSLFACTORY_SUFFIX =".ssl.socketfactory";
    private String server = null;
    private String bindDN = null;
    private String bindPW = null;
    private String base = null;
    private LdapName ldapbase = null;
    private String uidattr = null;
	private String usrformat = null;
    private String usrfilter = null;
    private int timelimit = DEFAULT_TIMELIMIT;
    private boolean grpsearch = true;
    private String grpbase = null;
    private String gidattr = null;
    private String memattr = null;
    private String grpfilter = null;
    private String repository = null;
    private boolean sslprotocol = false;
    private String sslfactory = null;

    public LdapUserRepository() { }

    public String getType() {
        return TYPE;
    }

    public void open(String authType, Properties authProperties,
                     Refreshable cacheData) throws LoginException {
        this.authType = authType;
        this.authProps = authProperties;
        String rep = authProps.getProperty(
              AccessController.PROP_AUTHENTICATION_PREFIX+authType+
                       AccessController.PROP_USER_REPOSITORY_SUFFIX);
        if (rep == null) {
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_USER_REPOSITORY_NOT_DEFINED, authType));
        }
        repository=rep;
        if (!rep.equals(TYPE)) {
        String[] args = {rep, TYPE, this.getClass().getName()};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_REPOSITORY_TYPE_MISMATCH, args));
        }
        String prefix = AccessController.PROP_USER_REPOSITORY_PREFIX+rep;
        server = authProps.getProperty(prefix+PROP_SERVER_SUFFIX);
        if (server == null || server.trim().equals("")) {
        String[] args = {authType, rep, PROP_SERVER_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        server = "ldap://"+server;
        bindDN = authProps.getProperty(prefix+PROP_BINDDN_SUFFIX);
        if (bindDN != null && !bindDN.trim().equals("")) {
            bindPW = authProps.getProperty(prefix+PROP_BINDPW_SUFFIX);
            int retry = 0;
            Password pw = null;
            boolean setProp = bindPW == null || bindPW.equals("");
            while ((bindPW == null || bindPW.trim().equals("")) && retry < 5) {
                pw = new Password();
                if (pw.echoPassword()) {
                    System.err.println(Globals.getBrokerResources().getString(BrokerResources.W_ECHO_PASSWORD));
                }
                System.err.print(Globals.getBrokerResources().getString(BrokerResources.M_ENTER_KEY_LDAP, bindDN));
                System.err.flush();

                bindPW = pw.getPassword();

                // Limit the number of times we try reading the passwd.
                // If the VM is run in the background the readLine()
                // will always return null and we'd get stuck in the loop
                retry++;
            }
            if (bindPW == null || bindPW.trim().equals("")) {
                logger.log(Logger.WARNING, BrokerResources.W_NO_LDAP_PASSWD, bindPW);
                bindDN = null;
            } else if (setProp) {
                authProps.put(prefix+PROP_BINDPW_SUFFIX, bindPW);
            }
        }
        else {
            bindDN = null;
        }
        usrformat = authProps.getProperty(prefix+PROP_USRFORMAT_SUFFIX);
        if (usrformat != null) {
            usrformat = usrformat.trim();
            if (usrformat.equals("")) {
                usrformat = null;
            } else if (!usrformat.trim().equals(DN_USRFORMAT)) {
                throw new LoginException(Globals.getBrokerResources().getKString(
                                         BrokerResources.X_UNSUPPORTED_PROPERTY_VALUE,
                                         ""+prefix+PROP_USRFORMAT_SUFFIX, usrformat));
            }
        }
        base = authProps.getProperty(prefix+PROP_BASE_SUFFIX);
        if (base != null && base.trim().equals("")) base = null;
        if (base == null && (usrformat == null || !usrformat.equals(DN_USRFORMAT))) {
        String[] args = {authType, rep, PROP_BASE_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        ldapbase = null;
        if (base != null && usrformat != null && usrformat.equals(DN_USRFORMAT)) {
            try {
            ldapbase = new LdapName(base);
            } catch (Exception e) {
            throw new LoginException(e.toString());
            }
        }
        uidattr = authProps.getProperty(prefix+PROP_UIDATTR_SUFFIX);
        if (uidattr == null || uidattr.trim().equals("")) {
        String[] args = {authType, rep, PROP_UIDATTR_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        usrfilter = authProps.getProperty(prefix+PROP_USRFILTER_SUFFIX);
        if (usrfilter != null && usrfilter.trim().equals("")) {
            usrfilter = null;
        }
        String tlimit = authProps.getProperty(prefix+PROP_TIMEOUT_SUFFIX);
        if (tlimit != null) {
            try {
            timelimit = Integer.parseInt(tlimit) * 1000;
            } catch (NumberFormatException e) {
            timelimit = -1;
            }
        }
        if (timelimit < 0) {
            timelimit = DEFAULT_TIMELIMIT;
        }

        String grpsrch = authProps.getProperty(prefix+PROP_GRPSEARCH_SUFFIX);
        if (grpsrch != null && grpsrch.equals("false")) {
            grpsearch = false;
        }
        if (grpsearch)  {

        grpbase = authProps.getProperty(prefix+PROP_GRPBASE_SUFFIX);
        if (grpbase == null || grpbase.trim().equals("")) {
        String[] args = {authType, rep, PROP_GRPBASE_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        gidattr = authProps.getProperty(prefix+PROP_GIDATTR_SUFFIX);
        if (gidattr == null || gidattr.trim().equals("")) {
        String[] args = {authType, rep, PROP_GIDATTR_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        memattr = authProps.getProperty(prefix+PROP_MEMATTR_SUFFIX);
        if (memattr == null || memattr.trim().equals("")) {
        String[] args = {authType, rep, PROP_MEMATTR_SUFFIX};
        throw new LoginException(Globals.getBrokerResources().getKString(
             BrokerResources.X_LDAP_REPOSITORY_PROPERTY_NOT_DEFINED, args));
        }
        grpfilter = authProps.getProperty(prefix+PROP_GRPFILTER_SUFFIX);
        if (grpfilter != null && grpfilter.trim().equals("")) {
            grpfilter = null;
        }

        } //if grpsearch

        String ssl = authProps.getProperty(prefix+PROP_SSL_SUFFIX);
        if (ssl != null && ssl.equals("true")) {
            sslprotocol = true;
            ssl = authProps.getProperty(prefix+PROP_SSLFACTORY_SUFFIX);
            if (ssl != null && !ssl.trim().equals("")) {
                sslfactory = ssl.trim();
            }
        }
    }

    /**
     * Find the user in the repository and compare the credential with
     * the user's  credential in database
     *
     * @param user the user name
     * @param credential password (String type) for "basic" is the password
     * @param extra null for basic, nonce if digest
     * @param matchType must be "basic"
     *        
     * @return the authenticated subject  <BR>
     *         null if no match found <BR>
     *
     * @exception LoginException
     */
    public Subject findMatch(String user, Object credential,
                             Object extra, String matchType) throws LoginException {
        if (matchType != null && matchType.equals(AccessController.AUTHTYPE_BASIC)) {
        return jmqbasicFindMatch(user, (String)credential);
        }
        String matchtyp = (matchType == null) ? "null": matchType;
        String[] args = {matchtyp, authType, getType(), AccessController.AUTHTYPE_BASIC};
        throw new LoginException(Globals.getBrokerResources().getKString(
              BrokerResources.X_UNSUPPORTED_USER_REPOSITORY_MATCHTYPE, args));
    }
      
    private Subject jmqbasicFindMatch(String user, String userpwd) throws LoginException {
        if (DEBUG) {
        logger.log(Logger.INFO, "Authenticate[basic] "+user+":"+userpwd+
                                  ((usrformat == null) ? ":":":usrformat="+usrformat));
        }
        /*
         * LDAP requires the password to be nonempty for simple authentication.
         * otherwise it automatically converts the authentication to "none"
         */
        if (userpwd == null || userpwd.trim().equals("")) {
        throw new LoginException(Globals.getBrokerResources().getKString(
                                 BrokerResources.X_PASSWORD_NOT_PROVIDED, user));
        }
        if (user == null || user.trim().equals("")) {
        throw new LoginException(Globals.getBrokerResources().getKString(
                                 BrokerResources.X_USERNAME_NOT_PROVIDED, user));
        }

        String url = server;
        if (DEBUG) {
        logger.log(Logger.INFO, "LDAP server: "+ url);
        }
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.REFERRAL, "follow"); //see JNDI doc
        if (sslprotocol) {
        env.put(Context.SECURITY_PROTOCOL,"ssl");
        if (sslfactory != null)
            env.put("java.naming.ldap.factory.socket", sslfactory);
        }

        String dnName = null;
        boolean dnformat = false;
        if (usrformat != null && usrformat.equals(DN_USRFORMAT)) {
            dnformat = true;
            dnName = user;
            user = handleDNusrformat(user);
        } else {
            dnName = searchDN(user, env);
        }
        DirContext ctx = null;
        Exception ee = null;
        try {
            if (!dnformat) {
                logger.log(Logger.INFO, br.getKString(
                           BrokerResources.I_AUTHENTICATE_USER_AS, user, dnName));
            } else {
                logger.log(Logger.INFO, br.getKString(
                           BrokerResources.I_AUTHENTICATE_AS_USER, dnName, user));
            }
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, dnName);
            env.put(Context.SECURITY_CREDENTIALS, userpwd);
            try {
                ctx = new InitialDirContext(env);
                ctx.close();

                Subject subject = new Subject();
                subject.getPrincipals().add(new MQUser(user));
                try {
                    findGroups(dnName, subject);
                } catch (NamingException e) {
                    String emsg = Globals.getBrokerResources().getKString(
                    BrokerResources.X_LDAP_GROUP_SEARCH_ERROR, user+" ["+dnName+"]");
                    logger.logStack(Logger.ERROR, emsg, e);
                    throw new LoginException(emsg+":"+e.getMessage());
                }
                return subject;
            } catch (javax.naming.AuthenticationException e) {
                if (DEBUG) {
                    logger.log(Logger.INFO, e.getMessage(), e);
                }
                throw new FailedLoginException(e.getMessage());
            }

        } catch (Exception e) {
            if (e instanceof FailedLoginException) throw (FailedLoginException)e;
            if (e instanceof LoginException) throw (LoginException)e;
            String emsg = null;
            if (e instanceof NamingException) {
                emsg = ((NamingException)e).toString(true);
            } else {
                emsg = e.toString();
            }
            logger.logStack(Logger.ERROR, emsg, e);
            throw new LoginException(emsg);
        } finally {
            try {
                if (ctx != null) ctx.close();
            } catch (NamingException ne) {
                /*ignore*/
            }
        }
    }

    private String searchDN(String user, Hashtable env) throws LoginException {

        if (bindDN != null && bindPW != null) {
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDN);
        env.put(Context.SECURITY_CREDENTIALS, bindPW);
        }

        DirContext ctx = null;
        try {

            ctx = new InitialDirContext(env);
            SearchControls ctls = new SearchControls();
            ctls.setReturningAttributes(new String[]{uidattr});
            ctls.setSearchScope(SearchControls.SUBTREE_SCOPE); //
            ctls.setTimeLimit(timelimit); //in milliseconds

            String filter = uidattr+"="+user;
            if (usrfilter != null) {
                filter = "(&("+usrfilter+")("+filter+"))";
            }
            if (DEBUG) {
            logger.log(Logger.INFO, "filter:"+filter+":");
            }
            NamingEnumeration enm = ctx.search(base, filter, ctls);
            int count = 0;
            String dnName = null;
            while (enm.hasMore()) {
                if (count != 0) {
                    enm.close();
                    throw new NamingException(Globals.getBrokerResources().getKString(
                                  BrokerResources.X_NOT_UNIQUE_USER, user, repository));
                }
                SearchResult sr = (SearchResult)enm.next();
                if (!sr.isRelative()) { //XXX ???
                    throw new NamingException(Globals.getBrokerResources().getKString(
                      BrokerResources.X_LDAP_SEARCH_RESULT_NOT_RELATIVE, sr.getName()));
                }
                Attributes attrs = sr.getAttributes();
                if (attrs == null) {
                    throw new NamingException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_DN_NOT_FOUND, user, repository)+
                        "[SearchResult.getAttributes()="+null+"]");
                }
                Attribute attr = attrs.get(uidattr);
                if (attr == null) {
                    throw new NamingException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_DN_NOT_FOUND, user, repository)+
                        "[Attribute.get("+uidattr+")="+null+"]");
                }
                if (!user.equals(attr.get())) {
                    throw new FailedLoginException(Globals.getBrokerResources().getKString(
                        BrokerResources.X_DN_NOT_FOUND, user, repository)+
                        "[Attribute.get("+uidattr+")="+attr+"]");
                }
                dnName = sr.getName() + ", " + base;
                count++;
            }
            ctx.close();
            if (dnName == null) {
                throw new FailedLoginException(Globals.getBrokerResources().getKString(
                                    BrokerResources.X_DN_NOT_FOUND, user, repository));
            }

            if (DEBUG) {
                logger.log(Logger.INFO, "dn="+dnName);
            }
            return dnName;

        } catch (Exception e) {
            if (e instanceof FailedLoginException) throw (FailedLoginException)e;
            String emsg = null;
            if (e instanceof NamingException) {
                emsg = ((NamingException)e).toString(true);
            } else {
                emsg = e.toString();
            }
            logger.logStack(Logger.ERROR, emsg, e);
            throw new LoginException(emsg);
        } finally {
            try {
                if (ctx != null) ctx.close();
            } catch (NamingException ne) {
                /*ignore*/
            }
        }
    }

   private String handleDNusrformat(String user) throws LoginException {
        String dnuser = user;
        try {
            LdapName ldapn = new LdapName(user);
            if (ldapbase != null && !ldapn.startsWith(ldapbase)) {
                throw new LoginException(
                br.getKString(br.X_DN_BASE_NOTMATCH, user, ldapbase.toString()));
            }
            Iterator itr = (ldapn.getRdns()).iterator();
            Attributes attrs = null;
            Attribute attr = null;
            Object attrv = null;
            while (itr.hasNext()) {
                attrs = (Attributes)((Rdn)itr.next()).toAttributes();
                attr = attrs.get(uidattr);
                if (attr == null) continue;
                attrv = attr.get(0);
                if (attrv == null) break;
                if (!(attrv instanceof String)) {
                    throw new LoginException(br.getKString(
                          BrokerResources.X_ATTRIBUTE_NOT_STRING_TYPE,
                          uidattr+"["+dnuser+"]", attrv.getClass().getName()));
                }
                if (((String)attrv).trim().equals("")) {
                    break;
                }
                return (String)attrv;
            }
            throw new LoginException(br.getKString(
                  BrokerResources.X_ATTRIBUTE_NOT_FOUND_IN, uidattr, dnuser));
        } catch (Exception e) {
            if (e instanceof LoginException) throw (LoginException)e;
            String emsg = null;
            if (e instanceof NamingException) {
                emsg = ((NamingException)e).toString(true);
            } else {
                emsg = e.toString();
            }
            logger.logStack(Logger.ERROR, emsg, e);
            throw new LoginException(e.getMessage());
        }
    }

    /**
     * @return list of groups the dn is a member 
     */
    private void findGroups(String dn, Subject subject) throws NamingException {
        if (!grpsearch) return;

        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, server);
        env.put(Context.REFERRAL, "follow");
        if (bindDN != null) {
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, bindDN);
        env.put(Context.SECURITY_CREDENTIALS, bindPW);
        }
        if (sslprotocol) {
        env.put(Context.SECURITY_PROTOCOL,"ssl");
        if (sslfactory != null)
            env.put("java.naming.ldap.factory.socket", sslfactory);
        }

        DirContext ctx = null;
        Exception ee = null;
        try {

        ctx = new InitialDirContext(env);
        SearchControls ctls = new SearchControls();
        String attr[] = new String[1];
        attr[0] = gidattr;
        ctls.setReturningAttributes(attr);
        ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE); 
        ctls.setTimeLimit(timelimit); //in milliseconds

        String filter = memattr+"="+dn; 
        if (grpfilter != null) { 
            filter = "(&("+grpfilter+")("+filter+"))";
        }
        if (DEBUG) {
        logger.log(Logger.INFO, "filter:"+filter+":");
        }
        NamingEnumeration em = ctx.search(grpbase, filter, ctls);

        SearchResult sr = null;
        Attributes attrs = null;
        Attribute grp = null;
        String group = null;
        while (em.hasMore()) {
            sr = (SearchResult)em.next();
            if (!sr.isRelative()) {
            throw new NamingException(Globals.getBrokerResources().getKString(
                BrokerResources.X_LDAP_SEARCH_RESULT_NOT_RELATIVE, sr.getName()));
            }
            attrs = sr.getAttributes();
            if (attrs != null) {
                grp = attrs.get(gidattr);
                if (grp != null) {
                    group = (String)grp.get(0);
                    if (group != null && !group.equals("")) {
                        if (DEBUG) {
                        logger.log(Logger.INFO, "found group:"+ group+":");
                        }
                        final Subject tempSubject = subject;
                        final String tempGroup = group;
                        java.security.AccessController.doPrivileged(
                            new PrivilegedAction<Object>() {
                                public Object run(){
                                    tempSubject.getPrincipals().add(new MQGroup(tempGroup));
                                    return null;
                                }
                            }
                        );
/*
//                        subject.getPrincipals().add(new MQGroup(group));
*/                    }
                }
            }
        }

        } catch (Exception e) {
            if (e instanceof NamingException) throw (NamingException)e;
            NamingException ne = new NamingException(e.toString());
            ne.initCause(e);
            throw ne;
        } finally {
            if (ctx != null) ctx.close(); 
        }
    }

    public Refreshable getCacheData() {
       return null;  //not cache
    }

    public void close() throws LoginException { }
}
