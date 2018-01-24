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
 * @(#)JMQFileAccessControlModel.java	1.26 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth.acl;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.URL;
import java.security.Principal;
import java.security.AccessControlException;
import javax.security.auth.Subject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.DOMException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXParseException;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.DestType;
import com.sun.messaging.jmq.io.PacketType;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.core.DestinationUID;
import com.sun.messaging.jmq.jmsserver.auth.AccessController;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.auth.api.server.*;
import com.sun.messaging.jmq.auth.api.server.model.*;

/**
 * JMQFileAccessControlModel 
 */

public class JMQFileAccessControlModel implements AccessControlModel {

    public static final String VERSION = "JMQFileAccessControlModel/100";
    public static final String TYPE = "file";

    public static final String PROP_FILENAME_SUFFIX = TYPE + ".filename";
    public static final String PROP_DIRPATH_SUFFIX = TYPE + ".dirpath";
    public static final String PROP_URL_SUFFIX = TYPE + ".url";
    public static final String DEFAULT_ACL_FILENAME = "accesscontrol.properties";

    private static boolean DEBUG = false;
    private Logger logger = Globals.getLogger();

    private static final String VERSION_PROPNAME ="version";

    private static final String ALLOW_SUFFIX =".allow";
    private static final String DENY_SUFFIX =".deny";
    private static final String USER_SUFFIX = ".user";
    private static final String GROUP_SUFFIX = ".group";
    private static final String ALL = "*";
    private static final String WILDCARD = "*";

    private static final int ALLOW_BIT = 0;
    private static final int DENY_BIT = 1;
    private String type;
    private Properties authProps;

    private String aclfname = null;

    private Properties acs = null;
    private long acsTimestamp = 0;
    private String aclfileSave = null;

    private Class userClass = null;
    private Class groupClass = null;

    public String getType() {
        return TYPE;
    }

    /**
     * This method is called immediately after this AccessControlModel
     * has been instantiated and prior to any calls to its other public
     * methods.
     *
	 * @param type the jmq.accesscontrol.type
     * @param authProperties broker auth properties
     */
    public void initialize(String type, Properties authProperties)
                                     throws AccessControlException {
        this.type = type;
        if (!type.equals(TYPE)) {
            String[] args = {type, TYPE, this.getClass().getName()};
            String emsg = Globals.getBrokerResources().getKString(
                       BrokerResources.X_ACCESSCONTROL_TYPE_MISMATCH, args);
            logger.log(Logger.ERROR, emsg);
            throw new AccessControlException(emsg);
        }
        authProps = authProperties;

        String authtyp = authProps.getProperty(
                         AccessController.PROP_AUTHENTICATION_TYPE);
        assert ( authtyp != null );
        String rep = authProps.getProperty(
                     AccessController.PROP_AUTHENTICATION_PREFIX+authtyp+
                     AccessController.PROP_USER_REPOSITORY_SUFFIX);
        assert ( rep != null );
        String uprop = AccessController.PROP_USER_REPOSITORY_PREFIX+
                          rep+AccessController.PROP_USER_PRINCIPAL_CLASS_SUFFIX;
        String uclass = authProps.getProperty(uprop);
        String gprop = AccessController.PROP_USER_REPOSITORY_PREFIX+
                          rep+AccessController.PROP_GROUP_PRINCIPAL_CLASS_SUFFIX;
        String gclass = authProps.getProperty(gprop);
        try {
            if (uclass != null) userClass = Class.forName(uclass);
            if (gclass != null) groupClass = Class.forName(gclass);
        } catch (ClassNotFoundException e) {
        logger.log(Logger.ERROR, e.getMessage(), e);
        throw new AccessControlException("ClassNotFoundException: "+e.getMessage());
        }

        load();
    }

    private static final int MAX_RECURSIONS = 25;
    private static final String LDAP_MULTILINE_SEPARATOR = "$";

    private boolean travelChildren(Node elem, int cnt, URL au) throws Exception {
        NodeList nodes = elem.getChildNodes();
        if (DEBUG) {
        logger.log(logger.INFO,  "FileACL.travelChildren("+elem+", "+cnt+")"+
                   elem.getNodeType()+"#children:"+nodes.getLength());
        }
        if (cnt > MAX_RECURSIONS) {
            throw new DOMException(DOMException.DOMSTRING_SIZE_ERR, 
                "Maximum "+cnt+" nested elements exceeded: "+elem);
        }
        Node nod = null;
        for (int i=0; i< nodes.getLength(); i++){
            nod = nodes.item(i);
            if (travelChildren(nod, cnt+1, au)) {
                return true;
            }
        }
        ByteArrayInputStream bais = null;
        String data = elem.getNodeValue();
        if (data != null && !data.trim().equals("")) {
            if (DEBUG) {
               logger.log(logger.INFO,  "FileACL.travelChildren.load data: "+data);
            }
            acs = new Properties();
            bais = new ByteArrayInputStream(data.getBytes("UTF-8"));
            acs.load(bais);
            if (checkVersion(acs, au.toString(), false)) {
                return true;
            }
            if (acs.size() == 1) {
                String ver = acs.getProperty(VERSION_PROPNAME);
                if (ver != null && data.contains(VERSION)) {
                    acs.clear();
                    acs = StringUtil.toProperties(data, LDAP_MULTILINE_SEPARATOR, acs);  
                    if (checkVersion(acs, au.toString(), false)) {
                        return true;
                    }
                }
            }
            acs = new Properties();
            bais.close();
        }
        return false;
    }

    DocumentBuilder      docBuilder = null;  
    boolean doXMLOnly = false;

    private void loadAsXML(URL au) throws Exception {
        if (docBuilder == null) {
            docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        }
        InputStream ins = null;
        try {
            ins = au.openStream();
            InputSource is =new InputSource(ins); 
            Document doc = docBuilder.parse(is);
            Element root = doc.getDocumentElement();
            root.normalize();
            if (DEBUG) {
                logger.log(logger.INFO, "FileACL.loadAsXML("+au+"), root="+
                                         root.getNodeName());
            }
            if (!travelChildren(root, 0, au)) {
                throw new DOMException(DOMException.NOT_FOUND_ERR, 
                    "FileACL: Expected data not found from "+au);
            }

        } catch (SAXParseException e) {
            acs = null;
            Exception   x = e.getException();
            logger.log(logger.ERROR,  
                "FileACL: parsing error: " +e.getMessage()
                          + ", line " + e.getLineNumber()
                          + ", uri " + e.getSystemId(), x);

            logger.logStack(logger.ERROR, "FileACL: error in processing "+au, e); 
            throw e;
        } finally {
            try {
            if (ins != null) ins.close();
            } catch (Exception e) {}
        }
    }

    private void loadAsProperties(URL au) throws Exception {
        InputStream is = null;
        BufferedInputStream bis = null;
        try {
            acs = new Properties();
            is = au.openStream();
            bis = new BufferedInputStream(is);
            acs.load(bis);
        } finally {
            if (bis != null) {
                try {
                bis.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
            if (is != null) {
                try {
                is.close();
                } catch (Exception e) {
                /* ignore */
                }
            }
        }
    }

    public void load() throws AccessControlException {
        String aclurl = authProps.getProperty(AccessController.PROP_ACCESSCONTROL_PREFIX+PROP_URL_SUFFIX);
        String serviceLevelfn = authProps.getProperty(Globals.IMQ+"."+
                   authProps.getProperty(AccessController.PROP_SERVICE_NAME)+"."+
                   AccessController.PROP_ACCESSCONTROL_AREA+"."+PROP_FILENAME_SUFFIX);
        if (serviceLevelfn != null) aclurl = null;

        if (aclurl != null) {
            try {
                URL au = new URL(aclurl);
                boolean ok = false;
                if (!doXMLOnly) {
                    loadAsProperties(au);
                    ok = checkVersion(acs, aclurl, false);
                }
                if (!ok) { 
                    loadAsXML(au);
                    checkVersion(acs, aclurl, true);
                    doXMLOnly = true;
                }
                if (DEBUG) {
                    logger.log(logger.INFO, "FileACL.loaded: "+acs);
                }
            } catch (Exception e) {
                acs = null;
                logger.log(Logger.ERROR, e.getMessage(), e);
                throw new AccessControlException(Globals.getBrokerResources().getKString(
                  BrokerResources.X_FAILED_TO_LOAD_ACCESSCONTROL, aclurl) + " - " + e.getMessage());
            }
            return;
        }

        String acl_loc = authProps.getProperty(
				AccessController.PROP_ACCESSCONTROL_PREFIX
					+ PROP_DIRPATH_SUFFIX, Globals.getInstanceEtcDir());
        aclfname = authProps.getProperty(
				AccessController.PROP_ACCESSCONTROL_PREFIX
					+ PROP_FILENAME_SUFFIX, DEFAULT_ACL_FILENAME);
        if (aclfname == null) {
            String emsg = Globals.getBrokerResources().getKString(
                      BrokerResources.X_ACCESSCONTROL_NOT_DEFINED, type);
            logger.log(Logger.ERROR, emsg);
            throw new AccessControlException(emsg);
        }
        String aclfile = acl_loc +File.separator + aclfname;
        File file = null;
        long timestamp = 0L;
        FileInputStream  fis = null;
        BufferedInputStream  bis = null;
        try {
            file = new File(aclfile);
            timestamp = file.lastModified();
            if (acs != null && aclfileSave != null && aclfile.equals(aclfileSave)) {
                if (timestamp > 0 && timestamp == acsTimestamp) { 
                return;
                }
            }
            if (DEBUG) {
            logger.log(logger.INFO,  "Loading access control "+aclfile + " ...");
            }
            acs = new Properties();
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            acs.load(bis);
            bis.close();
            fis.close();
            checkVersion(acs, aclfile, true);
            aclfileSave = aclfile;
            acsTimestamp = timestamp;
        } catch (IOException e) {
        acs = null;
        try {
        if (bis != null) bis.close();
        if (fis != null) fis.close();
        } catch (IOException ioe) {}
        logger.log(Logger.ERROR, e.getMessage(), e);
        throw new AccessControlException(Globals.getBrokerResources().getKString(
          BrokerResources.X_FAILED_TO_LOAD_ACCESSCONTROL, aclfile) + " - " + e.getMessage());
        }
    }

    private boolean checkVersion(Properties acl, String aclfile, boolean throwexp)
        throws AccessControlException {

        String version = acl.getProperty(VERSION_PROPNAME);
        if (version == null || !version.equals(VERSION)) {
            version = (version == null) ? "null" : version;
            String[] args = {VERSION_PROPNAME, version, aclfile+" "+acl.keySet(),
                             VERSION, this.getClass().getName()};
            String emsg = Globals.getBrokerResources().getKString(
                          BrokerResources.X_ACCESSCONTROL_FILE_MISMATCH, args);
            if (throwexp)  {
                logger.log(Logger.ERROR, emsg);
                throw new AccessControlException(emsg);
            } else {
                if (DEBUG) {
                    logger.log(Logger.INFO, emsg);
                }
                return false;
            }
        }
        return true;
    }

   /**
    *
    * Check connection permission 
    *
    * @param clientUser The Principal represents the client user that is
    *                   associated with the subject
    * @param serviceName the service instance name  (eg. "broker", "admin")
    * @param serviceType the service type for the service instance 
    *                    ("NORMAL" or "ADMIN")
    * @param subject the authenticated subject
    *
    * @exception AccessControlException 
    */
    public void checkConnectionPermission(Principal clientUser, 
                                          String serviceName, 
                                          String serviceType,
                                          Subject subject) 
                                          throws AccessControlException {

       checkPermission(clientUser, subject, "connection", serviceType, null, false); 
    }

   /**
    * Check permission for an operation on a destination for this role
    *
    * @param clientUser The Principal represents the client user that is
    *                   associated with the subject
    * @param serviceName the service instance name  (eg. "broker", "admin")
    * @param serviceType the service type for the service instance 
    *                    ("NORMAL" or "ADMIN")
    * @param subject the authenticated subject
    * @operation the operaction 
    * @destination the destination
    *
    * @exception AccessControlException 
    */
    public void checkDestinationPermission(Principal clientUser,
                                           String serviceName,
                                           String serviceType,
                                           Subject subject,
                                           String operation,
                                           String destination,
                                           String destinationType)
                                           throws AccessControlException {
       boolean supportWildDest = !DestType.isQueueStr(destinationType);
       checkPermission(clientUser, subject, destinationType, 
           destination, operation, supportWildDest); 
    }

    private void checkPermission(Principal clientUser, Subject subject, 
                        String prefix, String variant, String suffix, boolean wild)
                                                     throws AccessControlException {
        Set groups = null;
        Set users = null;
        if (groupClass != null) groups = subject.getPrincipals(groupClass);
        if (userClass != null) users = subject.getPrincipals(userClass);
        if (users == null || users.size() == 0) {
            users = new HashSet();
            users.add(clientUser);
        }

        load();

        Principal user = null; 
        StringBuffer exceptionMsg = null;
        boolean computed = false;
        Iterator itr =  users.iterator();
        while (itr.hasNext()) {
            user = (Principal)itr.next();
            if (user == null) continue;
            validate(user.getName(), groups);

            ArrayList list = getRules(prefix, variant, suffix, wild); 
            try {
            computePermission(clientUser.getName(), user.getName(), groups, list, GROUP_SUFFIX);
            computed = true;
            } catch (AccessControlException e) {

            if (DEBUG) {
            logger.log(logger.INFO, clientUser+"["+user.getName()+"]AccessControlException: "+e.getMessage());
            }

            if (exceptionMsg == null)  exceptionMsg = new StringBuffer();
            exceptionMsg.append(e.getMessage());
            exceptionMsg.append(", ");
            
            continue;
            }
        }
        if (exceptionMsg != null) {
            throw new AccessControlException(
                      Globals.getBrokerResources().getKString(
                              BrokerResources.X_FORBIDDEN, exceptionMsg));
        }

        if (!computed) {
            throw new AccessControlException(
            Globals.getBrokerResources().getKString(BrokerResources.X_USER_NOT_DEFINED));
        }
    }

   /**
    *@param list a list of rules with order: explicit ones to general ones 
    *
    *@exception AccessControlException  
    */
    private void computePermission(String clientUser, String user, Set groups, 
                                   ArrayList list, String grouptag) 
                                   throws AccessControlException {
        String rule, gbstmp;
        Principal group;
        HashMap allows, denys;
        BitSet inheritedbs = new BitSet(2);
        BitSet ubs, gbs, ubsall, gbsall;

        if (DEBUG) {
           logger.log(Logger.INFO,  "ComputePermission for "+
           clientUser+"["+user+"], list="+list+", groups="+groups+", grouptag="+grouptag);
        }

        for (int i = 0; i < list.size(); i++) {

        rule = (String)list.get(i);
        allows = getRuleRightHand(rule + ALLOW_SUFFIX + USER_SUFFIX);
        denys = getRuleRightHand(rule + DENY_SUFFIX + USER_SUFFIX);
        ubsall = getPermission(ALL, allows, denys);
        ubs = getPermission(user, allows, denys);
        gbs = new BitSet(2);
        gbsall = new BitSet(2);
        if (groups != null && groups.size() > 0) {
            if (groups.size() >= Integer.MAX_VALUE) {
                throw new AccessControlException(
                    Globals.getBrokerResources().getString(
                    BrokerResources.X_INTERNAL_EXCEPTION, "too many groups for user " +clientUser));
            }
            allows = getRuleRightHand(rule + ALLOW_SUFFIX + grouptag);
            denys = getRuleRightHand(rule + DENY_SUFFIX + grouptag);
            gbsall = getPermission(ALL, allows, denys); 
            Iterator iter = groups.iterator();
            while(iter.hasNext()) {
                group = (Principal)iter.next();
                gbs.or(getPermission(group.getName(), allows, denys)); 
            }
        }
        if (DEBUG) {
        logger.log(Logger.INFO,  "\t"+clientUser+"["+user+"] computePermission:ubs=" + ubs);
        logger.log(Logger.INFO,  "\t"+clientUser+"["+user+"] computePermission:gbs=" + gbs);
        logger.log(Logger.INFO,  "\t"+clientUser+"["+user+"] computePermission:ubsall=" + ubsall);
        logger.log(Logger.INFO,  "\t"+clientUser+"["+user+"] computePermission:gbsall=" + gbsall);
        }
        overridePermission(gbs, ubs);
        overridePermission(gbsall, ubsall);
        overridePermission(gbsall, gbs);
        if (DEBUG) {
        logger.log(Logger.INFO,  "computed permission:"+rule+":bs=" + gbsall);
        }
        overridePermission(inheritedbs, gbsall);
        if (DEBUG) {
        logger.log(Logger.INFO,  "computed permission:total=" + inheritedbs);
        }

        } //for

        if (inheritedbs.get(ALLOW_BIT) && !inheritedbs.get(DENY_BIT)) {
            return;
        }

        throw new AccessControlException((clientUser.equals(user)) ? (""+clientUser):(clientUser+" ["+user+"]"));
    }

    private void overridePermission(BitSet basebs,  BitSet bs) {
        if (bs.get(ALLOW_BIT) && bs.get(DENY_BIT)) {
            return;
        }
        if (bs.get(ALLOW_BIT)) {
            basebs.set(ALLOW_BIT);
            basebs.clear(DENY_BIT);
        }
        if (bs.get(DENY_BIT)) {
            basebs.set(DENY_BIT);
            basebs.clear(ALLOW_BIT);
        }
    }


    /**
     * @return BitSet
     *  01   explicitly allow
     *  10   explicitly deny
     *  11   explicitly allow and deny - reset to 00 on return
     *  00   either does not have rules or rule does not apply 
     */
    private BitSet getPermission(String name, HashMap allows, HashMap denys) {
        BitSet bs = new BitSet(2);

        if (allows != null) { 
            if (allows.get(name) != null) {
            bs.set(ALLOW_BIT);
            }
        }
        if (denys != null) { 
            if (denys.get(name) != null) {
                bs.set(DENY_BIT);
            }
        }
        if (bs.get(ALLOW_BIT) && bs.get(DENY_BIT)) {
            bs.clear(ALLOW_BIT);
            bs.clear(DENY_BIT);
        }
        return bs;
    }

    private ArrayList getRules(String prefix, String variant, String suffix, boolean wild) 
       throws AccessControlException {
       String currentkey = null;
       try {

       ArrayList list = new ArrayList();
       String rule = null;

       if (variant == null && suffix != null) {
           rule = prefix + "." + suffix;
           list.add(rule);
           return list;
       }

       //add WILDCARD rule first
       rule = prefix + "." + WILDCARD;
       if (suffix != null) {
           rule = rule + "." + suffix;
       }
       list.add(rule);

       if (variant != null && !variant.equals(WILDCARD)) {
           rule = prefix + "." + variant;
           if (suffix != null) {
               rule = rule + "." + suffix;
           }
           list.add(rule);
       }

       if (wild && variant != null && suffix != null) {
           Pattern pattern = Pattern.compile("("+prefix+"\\."+"(.+)"+"\\."+suffix+")"+
                                             "(\\"+ALLOW_SUFFIX+"|"+"\\"+DENY_SUFFIX+
                                             ")(\\"+USER_SUFFIX+"|"+"\\"+GROUP_SUFFIX+")");
           Enumeration enu = acs.propertyNames();
           Matcher matcher = null;
           String regEx = null;
           while (enu.hasMoreElements()) {
               currentkey = (String)enu.nextElement();
               matcher = pattern.matcher(currentkey);
               if (matcher.matches()) {
                   if (matcher.group(2).equals(WILDCARD)) {
                       continue;
                   }
                   regEx = DestinationUID.createRegExString(matcher.group(2));
                   if (Pattern.matches(regEx, variant)) {
                       list.add(matcher.group(1));
                   }
               }
           }
       }
       return list;

       } catch (Exception e) {
         AccessControlException ae = new AccessControlException(
                      e.toString()+(currentkey == null ? "":" - "+currentkey));
         ae.initCause(e);
         throw ae;
       }
    }

    private HashMap getRuleRightHand(String propname) {
        if (DEBUG) {
        logger.log(Logger.DEBUG,  "check permission " +propname);
        }
        String values = acs.getProperty(propname);
        if (values == null) return null;
        StringTokenizer token = new StringTokenizer(values, ",", false);
        HashMap hp = new HashMap();
        while (token.hasMoreElements())
            hp.put(token.nextToken().trim(), "");
        if (hp.size() == 0) return null;
        return hp; 
    }

    private void validate(String user, Set groups) throws AccessControlException {
        if (user == null) {
        throw new AccessControlException(
        Globals.getBrokerResources().getKString(BrokerResources.X_USER_NOT_DEFINED));
        }
        if (user.equals(ALL)) {
        throw new AccessControlException(
        Globals.getBrokerResources().getKString(BrokerResources.X_USER_NAME_RESERVED,ALL));
        }

        if (groups != null) {
            Principal group;
            Iterator iter = groups.iterator();
            while(iter.hasNext()) {
                group = (Principal)iter.next();
                if (group == null || group.getName() == null) {
                    iter.remove();
                }
                else if (group.getName().equals(ALL)) {
                    throw new AccessControlException(
                    Globals.getBrokerResources().getKString(
                               BrokerResources.X_GROUP_NAME_RESERVED, ALL));
                }
            }
        }
    }

    /**
     * test driver
     * step 1. gnumake 
     * step 2. mkdir ./var/security
     * step 3. create ./var/security/accesscontrol.properties
     * step 4. run
     *
     * CPATH=../../../../../../../../../../binary/share/opt/classes
     * java -classpath $CPATH com.sun.messaging.jmq.jmsserver.auth.acl.JMQFileAccessControlModel
     *
     */
    public static void main(String[] args) throws Exception {
       DEBUG = true;
       Properties prop = new Properties();
       prop.setProperty(AccessController.PROP_ACCESSCONTROL_PREFIX +
	                    TYPE + ".filename", "accesscontrol.properties");
       JMQFileAccessControlModel m = new JMQFileAccessControlModel();
       m.initialize("file", prop);
       String user = "akang";
       HashSet groups = new HashSet();
       groups.add("student");
       groups.add("Accounting Managers");
       ArrayList list = m.getRules("topic", "abc", "produce", true);
       System.out.println(list);
       m.computePermission(user, user, groups, list, GROUP_SUFFIX);
       System.out.println("--DONE--");
    }

}

