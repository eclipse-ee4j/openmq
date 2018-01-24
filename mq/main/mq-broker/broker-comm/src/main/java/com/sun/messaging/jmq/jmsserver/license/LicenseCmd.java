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
 * @(#)LicenseCmd.java	1.12 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.license;

import java.io.*;
import java.util.*;
import java.security.*;

class LicenseCmd {
    public static void main(String args[]) throws Exception {
        if (args.length == 0)
            usage();

        if (args[0].equals("-generateFile"))
            generateFile();
        else if (args.length == 2 && args[0].equals("-generateCode"))
            generateCode(args[1]);
        else if (args.length == 2 && args[0].equals("-dump"))
            dump(args[1]);
        else
            usage();
    }

    private static final String LICENSE_FILE_PREFIX = "imqbroker";
    private static final String LICENSE_FILE_SUBFIX = ".lic";

    private static void readProps(Properties p) throws Exception {
        readCommonProperties(p);

        if (p.getProperty("imq.file_version").equals("4")) {
            readRaptorProperties(p);
        }
    }

    private static void generateFile() throws Exception {
        Properties p = new Properties();
        readProps(p);

        String lictype = p.getProperty("imq.license_type");
        File file = new File(LICENSE_FILE_PREFIX +
            lictype + LICENSE_FILE_SUBFIX);

        FileLicense fl = new FileLicense();
        fl.setAutoChecking(false);
        fl.superimpose(p);

        fl.writeLicense(file);
    }

    private static String codeFragment1 =
		"/*\n" +
		" * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.\n" +
		" *\n" +
		" * This program and the accompanying materials are made available under the\n" +
		" * terms of the Eclipse Public License v. 2.0, which is available at\n" +
		" * http://www.eclipse.org/legal/epl-2.0.\n" +
		" *\n" +
		" * This Source Code may also be made available under the following Secondary\n" +
		" * Licenses when the conditions for such availability set forth in the\n" +
		" * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,\n" +
		" * version 2 with the GNU Classpath Exception, which is available at\n" +
		" * https://www.gnu.org/software/classpath/license.html.\n" +
		" *\n" +
		" * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0\n" +
        " */\n" +
		" *\n" +
        "package com.sun.messaging.jmq.jmsserver.license;\n" +
        "\n" +
        "import java.io.*;\n" +
        "import java.util.*;\n" +
        "import java.security.*;\n" +
        "import com.sun.messaging.jmq.jmsserver.util.BrokerException;\n" +
        "\n" +
        "/**\n" +
        " * This is a generated license class file.\n" +
        " */\n" +
        "public class ";

    private static String codeFragment2 =
        " extends LicenseBase {\n" +
        "    private static byte[] data = {\n        ";

    private static String codeFragment3 =
        "\n    public ";

    private static String codeFragment4 =
        "() throws BrokerException {\n" +
        "        super();\n" +
        "\n" +
        "        try {\n" +
        "            byte[] plain = FileLicense.scramble(data);\n" +
        "            ByteArrayInputStream bais =\n" +
        "                new ByteArrayInputStream(plain);\n" +
        "            Properties tmp = new Properties();\n" +
        "            tmp.load(bais);\n" +
        "            superimpose(tmp);\n" +
        "        }\n" +
        "        catch (Exception e) {\n" +
        "            throw new BrokerException(\"Bad license.\", e);\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    public boolean isLicenseFileRequired() {\n" +
        "        return false;\n" +
        "    }\n" +
        "\n" +
        "    public static void main(String args[]) throws Exception {\n";

    private static String codeFragment5 =
        "        System.out.println(l.getProperties());\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "/*\n" +
        " * EOF\n" +
        " */\n";

    private static void generateCode(String cname) throws Exception {
        Properties p = new Properties();
        readProps(p);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        p.store(baos, null);
        byte[] plain = baos.toByteArray();
        baos.close();

        byte[] encrypted = FileLicense.scramble(plain);

        StringBuffer sbuf = new StringBuffer(); 
        for (int i = 0; i < encrypted.length; i++) {
            sbuf.append(Integer.toString((int)encrypted[i]) + ", ");
            if ((i+1) % 10 == 0) {
                sbuf.append("\n        ");
            }
        }
        sbuf.append("};\n");

        String code = codeFragment1 + cname + codeFragment2 + sbuf.toString() +
            codeFragment3 + cname + codeFragment4 + "        " +
            cname + " l = new " + cname + "();\n" + codeFragment5;

        File file = new File(cname + ".java");
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(code.getBytes());
        fos.flush();
        fos.close();
    }

    private static void dump(String in) throws Exception {
        File file = new File(in);

        FileLicense fl = null;
        fl = new FileLicense(file, false);

        Properties p = fl.getProperties();
        for (int i = 0; i < commonParams.length; i++) {
            String name = commonParams[i];
            String value = p.getProperty(name);

            if (value != null)
                System.out.println(name + " = " + value);
        }

        for (int i = 0; i < raptorParams.length; i++) {
            String name = raptorParams[i];
            String value = p.getProperty(name);
            if (name.equals("date_string"))
                value = dumpDateString(value);

            if (value != null)
                System.out.println(name + " = " + value);
        }

        System.out.println("\nUnknown properties :");
        Iterator itr = p.keySet().iterator();
        while (itr.hasNext()) {
            String s = (String) itr.next();
            if (types.get(s) == null)
                System.out.println(s + " = " + p.getProperty(s));
        }
    }

    private static String dumpDateString(String s) {
        if (s.startsWith(NONE_STRING)) {
            return "NONE";
        }
        else if (s.startsWith(TRY_STRING)) {
            // this license contains the number of days to try
            int oindex = s.indexOf(OPEN_BRACKET);
            int cindex = s.indexOf(CLOSE_BRACKET);
            int d = Integer.parseInt(
                s.substring(oindex+1, cindex));
            return "Expires after " + d + " days.";
        } else if (s.startsWith(VALID_STRING)) {
            String ret = null;

            // this license contains a date range
            int oindex = s.indexOf(OPEN_BRACKET);
            int dashindex = s.indexOf(DASH);
            int cindex = s.indexOf(CLOSE_BRACKET);

            if ((dashindex - oindex) > 1) {
                // we have a start date
                long start = Long.parseLong(
                    s.substring(oindex+1, dashindex));
                ret = "start = " + (new Date(start)).toString();
            }

            if ((cindex - dashindex) > 1) {
                // we have an exipriation date
                long end = Long.parseLong(
                    s.substring(dashindex+1, cindex));
                ret = ret + " --- end = " + (new Date(end)).toString();
            }

            return ret;
        } else {
            return "######## Bad date format ########";
        }
    }

    private static void readCommonProperties(Properties p)
        throws Exception {
        for (int i = 0; i < commonParams.length; i++) {
            String name = commonParams[i];

            String prompt = (String) prompts.get(name);
            String ptype = (String) types.get(name);
            String value = null;

            if (ptype == null) {
                System.out.println("ptype = null for : " + name);
                System.exit(1);
            }

            if (ptype.equals("Integer"))
                value = readInt(prompt);

            if (ptype.equals("Limit"))
                value = readLimit(prompt);

            if (ptype.equals("String"))
                value = readString(prompt);

            if (ptype.equals("DateInfo"))
                value = readDateInfo(prompt);

            if (ptype.equals("Boolean"))
                value = readBoolean(prompt);

            if (value == null) {
                System.out.println("Bad value : name = " + name +
                    ", value = " + value);
                System.exit(1);
            }

            p.setProperty(name, value);
        }
    }

    private static void readRaptorProperties(Properties p)
        throws Exception {
        for (int i = 0; i < raptorParams.length; i++) {
            String name = raptorParams[i];

            String prompt = (String) prompts.get(name);
            String ptype = (String) types.get(name);
            String value = null;

            if (ptype == null) {
                System.out.println("ptype = null for : " + name);
                System.exit(1);
            }

            if (ptype.equals("Integer"))
                value = readInt(prompt);

            if (ptype.equals("Limit"))
                value = readLimit(prompt);

            if (ptype.equals("String"))
                value = readString(prompt);

            if (ptype.equals("DateInfo"))
                value = readDateInfo(prompt);

            if (ptype.equals("Boolean"))
                value = readBoolean(prompt);

            if (value == null) {
                System.out.println("Bad value : name = " + name +
                    ", value = " + value);
                System.exit(1);
            }

            p.setProperty(name, value);
        }
    }

    private static BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in));

    private static final String USAGE =
        "java com.sun.messaging.jmq.jmsserver.license.LicenseCmd\n" +
        "\t-generateFile\n" +
        "\t-generateCode license_class_name\n" +
        "\t-dump  license_file\n";

    public static void usage() {
        System.out.println(USAGE);
        System.exit(1);
    }

    private static final String[] commonParams = {
        "imq.license_type",
        "imq.file_version",
        "imq.license_version",
        "description", // Shared with iAS.
        "imq.precedence",
    };

    private static final String[] raptorParams = {
        "imq.max_client_conns",
        "imq.max_broker_conns",
        "date_string", // Shared with iAS.
        "imq.enable_cluster",
        "imq.enable_http",
        "imq.enable_ssl",
        "imq.enable_sharedpool",
        "imq.max_backup_cons",
        "imq.max_active_cons",
        "imq.enable_c_api",
        "imq.enable_failover",
        "imq.enable_monitoring",
        "imq.enable_localdest",
        "imq.enable_dmq", 
        "imq.enable_clientping",
        "imq.enable_msgbody_compression",
        "imq.enable_shared_sub",
        "imq.enable_audit_ccc",
        "imq.enable_no_ack",
        "imq.enable_reconnect",
        "imq.enable_ha",
    
    };

    private static final String LICENCE_TYPE_P =
        "\nEnter basename for the file\n" +
        "(e.g. for imqbrokerdev.lic, the basename would be dev)\n" +
        "BaseName: ";

    private static final String FILE_VERSION_P =
        "\nEnter license file verion #\n" +
        "(Current File Format since Falcon=4) : ";

    private static final String LICENSE_VERSION_P =
        "\nEnter the license version #\n" +
        "(e.g. \"3.6 Beta\", \"3.6 FCS\"...) : ";

    private static final String DESCRIPTION_P =
        "\nEnter a description of the license: ";

    private static final String PRECEDENCE_P =
        "\nEnter a precedence value for this license\n" +
        "(e.g. try = 1000, developer = 2000, enterprise = 10000..) : ";

    private static final String CONNLIMIT_P =
        "\nEnter the max # of client connections " +
        "(-1 = unlimited) : ";

    private static final String BROKERLIMIT_P =
        "\nEnter the max # of broker connections " +
        "(-1 = unlimited) : ";

    private static final String EXPIRY_DATE_P =
        "\nEnter the date information :\n" +
        "\t0 - unlimited\n\tTn - for n trial days\n" +
        "\tEmmnnyyyy - for expiration date\n" +
        "\tRmmnnyyyy-mmnnyyyy - for a valid date range\n" +
        "Date : ";

    private static final String ENABLE_CLUSTER_P =
        "\nEnable clustering (y/n) : ";

    private static final String ENABLE_HTTP_P =
        "\nEnable http/https (y/n) : ";

    private static final String ENABLE_SSL_P =
        "\nEnable ssl (y/n) : ";

    private static final String ENABLE_SHAREDPOOL_P =
        "\nEnable shared threadpool (y/n) : ";

    private static final String BACKUP_CONS_LIMIT_P =
        "\nEnter the max # of backup consumers " +
        "(-1 = unlimited) : ";

    private static final String ACTIVE_CONS_LIMIT_P =
        "\nEnter the max # of active consumers " +
        "(-1 = unlimited) : ";

    private static final String ENABLE_C_API_P =
        "\nEnable C API (y/n) : ";

    private static final String ENABLE_FAILOVER_P =
        "\nEnable Connection Failover (y/n) : ";

    private static final String ENABLE_MONITORING_P =
        "\nEnable Monitoring (y/n) : ";

    private static final String ENABLE_LOCAL_DESTINATIONS_P =
        "\nEnable Local Destinations (y/n) : ";
    
   private static final String ENABLE_DMQ_P =
        "\nEnable DMQ (y/n) : ";
    
    private static final String ENABLE_CLIENTPING_P =
        "\nEnable client side ping (y/n) : ";
    
    private static final String ENABLE_MSGBODYCOMP_P =
        "\nEnable message body compression (y/n) : ";
    
    private static final String ENABLE_SHAREDSUB_P =
        "\nEnable shared subscriptions (y/n) : ";
    
    private static final String ENABLE_AUDITCCC_P =
        "\nEnable auditing for common criteria certification (y/n) : ";
    
    private static final String ENABLE_NOACK_P =
        "\nEnable no acknowledgement mode (y/n) : ";
    
     private static final String ENABLE_RECONNECT =
        "\nEnable reconnect mode (y/n) : ";
    
    private static final String ENABLE_HA_P =
        "\nEnable high availability (y/n) : ";
       

    private static HashMap types = new HashMap();
    private static HashMap prompts = new HashMap();

    static {
        types.put("imq.license_type", "String");
        types.put("imq.file_version", "Integer");
        types.put("imq.license_version", "String");
        types.put("description", "String");
        types.put("imq.precedence", "Integer");
        types.put("imq.max_client_conns", "Limit");
        types.put("imq.max_broker_conns", "Limit");
        types.put("date_string", "DateInfo");
        types.put("imq.enable_cluster", "Boolean");
        types.put("imq.enable_http", "Boolean");
        types.put("imq.enable_ssl", "Boolean");
        types.put("imq.enable_sharedpool", "Boolean");
        types.put("imq.max_backup_cons", "Limit");
        types.put("imq.max_active_cons", "Limit");
        types.put("imq.enable_c_api", "Boolean");
        types.put("imq.enable_failover", "Boolean");
        types.put("imq.enable_monitoring", "Boolean");
        types.put("imq.enable_localdest", "Boolean");
        types.put("imq.enable_dmq", "Boolean");
        types.put("imq.enable_clientping", "Boolean");
        types.put("imq.enable_msgbody_compression", "Boolean");
        types.put("imq.enable_shared_sub", "Boolean");
        types.put("imq.enable_audit_ccc", "Boolean");
        types.put("imq.enable_no_ack", "Boolean");
        types.put("imq.enable_reconnect","Boolean");
        types.put("imq.enable_ha", "Boolean");
    
        prompts.put("imq.license_type", LICENCE_TYPE_P);
        prompts.put("imq.file_version", FILE_VERSION_P);
        prompts.put("imq.license_version", LICENSE_VERSION_P);
        prompts.put("description", DESCRIPTION_P);
        prompts.put("imq.precedence", PRECEDENCE_P);
        prompts.put("imq.max_client_conns", CONNLIMIT_P);
        prompts.put("imq.max_broker_conns", BROKERLIMIT_P);
        prompts.put("date_string", EXPIRY_DATE_P);
        prompts.put("imq.enable_cluster", ENABLE_CLUSTER_P);
        prompts.put("imq.enable_http", ENABLE_HTTP_P);
        prompts.put("imq.enable_ssl", ENABLE_SSL_P);
        prompts.put("imq.enable_sharedpool", ENABLE_SHAREDPOOL_P);
        prompts.put("imq.max_backup_cons", BACKUP_CONS_LIMIT_P);
        prompts.put("imq.max_active_cons", ACTIVE_CONS_LIMIT_P);
        prompts.put("imq.enable_c_api", ENABLE_C_API_P);
        prompts.put("imq.enable_failover", ENABLE_FAILOVER_P);
        prompts.put("imq.enable_monitoring", ENABLE_MONITORING_P);
        prompts.put("imq.enable_localdest", ENABLE_LOCAL_DESTINATIONS_P);
        prompts.put("imq.enable_dmq", ENABLE_DMQ_P);
        prompts.put("imq.enable_clientping", ENABLE_CLIENTPING_P);
        prompts.put("imq.enable_msgbody_compression", ENABLE_MSGBODYCOMP_P);
        prompts.put("imq.enable_shared_sub", ENABLE_SHAREDSUB_P);
        prompts.put("imq.enable_audit_ccc", ENABLE_AUDITCCC_P);
        prompts.put("imq.enable_no_ack", ENABLE_NOACK_P);
        prompts.put("imq.enable_reconnect",ENABLE_RECONNECT);
        prompts.put("imq.enable_ha", ENABLE_HA_P);
        
    }

    /*
    private static int readIntChoice(String prompt, int[] choices)
        throws Exception {
        System.out.println(prompt);
        System.out.flush();

        String line = getLine();
        int value = Integer.parseInt(line);

        boolean good = false;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == value)
                good = true;
        }
        if (!good) {
            System.out.println("Bad value : " + line);
            System.exit(1);
        }

        return value;
    }
    */

    private static String readBoolean(String prompt)
        throws Exception {
        System.out.print(prompt);
        System.out.flush();

        String line = getLine();
        if (line.equals("y") || line.equals("Y"))
            return "true";
        if (line.equals("n") || line.equals("N"))
            return "false";

        return null;
    }

    private static String readInt(String prompt)
        throws Exception {
        System.out.print(prompt);
        System.out.flush();

        String line = getLine();
        Integer.parseInt(line);
        return line;
    }

    private static String readLimit(String prompt)
        throws Exception {
        System.out.print(prompt);
        System.out.flush();

        String line = getLine();
        int value = Integer.parseInt(line);
        if (value < 0)
            value = Integer.MAX_VALUE;

        return String.valueOf(value);
    }

    private static String readString(String prompt)
        throws Exception {
        System.out.print(prompt);
        System.out.flush();

        String line = getLine();
        return line;
    }

    // some contants that may appear in the expirationDate field;
    private static final String NONE_STRING = "NONE";
    private static final String TRY_STRING = "TRY";
    private static final String EXPIRE_STRING = "EXPIRE"; // version 2
    private static final String VALID_STRING = "VALID"; // version 3
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "]";
    private static final String DASH = "-";

    private static String readDateInfo(String prompt)
        throws Exception {
        System.out.print(prompt);
        System.out.flush();

        String line = getLine();
        DateInfo di = new DateInfo();
        parseDateInfo(line, di);

        System.out.println("######## Date Info : " + di);

        return getDateString(di);
    }

    private static String getDateString(DateInfo di) {
        String string = null;
        if (di._noExpiration) {
            string = NONE_STRING;
        } else if (di._start != null || di._end != null) {
            String start = ((di._start != null) ?
                String.valueOf(di._start.getTime()) : "");
            String end = ((di._end != null) ?
                String.valueOf(di._end.getTime()) : "");

            string = formatString(VALID_STRING, start+DASH+end);
        } else {
            string = formatString(TRY_STRING,
                String.valueOf(di._daysToUse));
        }
        return string;
    }

    private static String formatString(String prefix, String content) {
        return prefix + OPEN_BRACKET + content + CLOSE_BRACKET;
    }

    private static void parseDateInfo(String line, DateInfo di)
        throws Exception {
        // sanity check
        if (!line.equals("0") && !(line.length() > 1)) {
            System.out.println("Bad input: " + line);
            System.exit(1);
        }

        String dateString = null;

        if (line.startsWith("E") || line.startsWith("e")) {
            // Emmnnyyyy
            // 012345678
            dateString = line.substring(1, line.length());
            checkDateString(dateString);
            Calendar cal = Calendar.getInstance();
            cal.set(cal.MONTH, getMonth(dateString)-1);
            cal.set(cal.DAY_OF_MONTH, getDay(dateString));
            cal.set(cal.YEAR, getYear(dateString));
            cal.set(cal.HOUR_OF_DAY, 0);
            cal.set(cal.MINUTE, 0);
            cal.set(cal.SECOND, 0);
            cal.set(cal.MILLISECOND, 0);
            di._end = cal.getTime();
        } else if (line.startsWith("T") || line.startsWith("t")) {
            // Tn
            di._daysToUse = Integer.parseInt(line.substring(1));
        } else if (line.startsWith("R") || line.startsWith("r")) {
            // Rmmnnyyyy-mmnnyyyy
            // 012345678901234567
            int dashindex = line.indexOf(DASH);
            if (dashindex > 1 || dashindex == -1) {
                // get start date
                if (dashindex == -1) dashindex = line.length();

                dateString = line.substring(1, dashindex);
                checkDateString(dateString);
                Calendar cal = Calendar.getInstance();
                cal.set(cal.MONTH, getMonth(dateString)-1);
                cal.set(cal.DAY_OF_MONTH, getDay(dateString));
                cal.set(cal.YEAR, getYear(dateString));
                cal.set(cal.HOUR_OF_DAY, 0);
                cal.set(cal.MINUTE, 0);
                cal.set(cal.SECOND, 0);
                cal.set(cal.MILLISECOND, 0);
                di._start = cal.getTime();
            }

            if ((line.length() - dashindex) > 1) {
                // get end date
                dateString = line.substring(dashindex+1, line.length());
                checkDateString(dateString);
                Calendar cal = Calendar.getInstance();
                cal.set(cal.MONTH, getMonth(dateString)-1);
                cal.set(cal.DAY_OF_MONTH, getDay(dateString)+1);
                cal.set(cal.YEAR, getYear(dateString));
                cal.set(cal.HOUR_OF_DAY, 0);
                cal.set(cal.MINUTE, 0);
                cal.set(cal.SECOND, 0);
                cal.set(cal.MILLISECOND, 0);
                di._end = cal.getTime();
            }
        } else if (line.equals("0")) {
            di._noExpiration = true;
        } else {
            System.out.println("Bad expiration date");
            System.exit(1);
        }
    }

    private static final String errorMsg =
			"Expected date in this format: mmnnyyy"; 

    // format expected
    // mmnnyyyy
    // 01234567
    private static void checkDateString(String string) throws Exception {
        if (string.length() != 8) {
            throw new Exception(errorMsg);
        }
    }

    private static int getMonth(String string) throws Exception {
        try {
            return Integer.parseInt(string.substring(0, 2));
        } catch (Exception e) {
            throw new Exception(errorMsg);
        }
    }

    private static int getDay(String string) throws Exception {
        try {
            return Integer.parseInt(string.substring(2, 4));
        } catch (Exception e) {
            throw new Exception(errorMsg);
        }
    }

    private static int getYear(String string) throws Exception {
        try {
            return Integer.parseInt(string.substring(4, 8));
        } catch (Exception e) {
            throw new Exception(errorMsg);
        }
    }


    private static String getLine() throws Exception {
        String line = reader.readLine();
        if (line == null) {
            System.exit(1);
        }
        return line;
    }
}

class DateInfo {
    public Date _start = null;
    public Date _end = null;
    public int _daysToUse = 0;
    public boolean _noExpiration = false;

    public DateInfo() {}

    public String toString() {
        return
            "(start = " + _start +
            ", end = " + _end +
            ", daysToUse = " + _daysToUse +
            ", Expiration = " + _noExpiration + ")";
    }
};

/*
 * EOF
 */
