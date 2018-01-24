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
 */ 

package com.sun.messaging.jmq.jmsserver.persist.jdbc.comm;

import com.sun.messaging.jmq.io.MQAddress;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.util.StringUtil;
import com.sun.messaging.jmq.util.Password;
import com.sun.messaging.jmq.jmsserver.util.*;
import com.sun.messaging.jmq.jmsserver.config.*;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.*;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.persist.jdbc.Util;
import com.sun.messaging.jmq.jmsserver.BrokerStateHandler;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.net.*;
import java.lang.reflect.*;
import javax.sql.*;

/**
 * A supper DB manager class to be used by different JDBC stores
 */
public abstract class CommDBManager {

    private static final String JDBC_STORE_TYPE = Store.JDBC_STORE_TYPE;

    private static final String TABLEOPTION_NAME_VARIABLE = "tableoption";
    private static final String TABLE_NAME_VARIABLE = "name";
    private static final String INDEX_NAME_VARIABLE = "index";

    private static final String LOGIN_TIMEOUT_PROP_SUFFIX =
        ".loginTimeout";

    private static final String TRANSACTION_RETRY_MAX_PROP_SUFFIX =
        ".transaction.retry.max";
    private static final String TRANSACTION_RETRY_DELAY_PROP_SUFFIX =
        ".transaction.retry.delay";

    public static final int TRANSACTION_RETRY_MAX_DEFAULT = 5;
	public static final long TRANSACTION_RETRY_DELAY_DEFAULT = 2000;

    private static final int CONNECTION_RETRY_MAX_DEFAULT = 60;
    private static final long CONNECTION_RETRY_DELAY_DEFAULT = 5000; //millisec
    private static final String CONNECTION_RETRY_MAX_PROP_SUFFIX =
        ".connection.retry.max";
    private static final String CONNECTION_RETRY_DELAY_PROP_SUFFIX =
        ".connection.retry.delay";

    private static final String CONNECTION_RETRY_REGEX_PROP_SUFFIX =
        ".connection.retry.regex";

    private static final String SQL_RETRIABLE_ERROR_CODES_PROP_SUFFIX =
        ".sqlRetriableErrorCodes";

    protected static final String VENDOR_PROP_SUFFIX = ".dbVendor";
    protected static final String FALLBACK_USER_PROP_SUFFIX = ".user";
    protected static final String FALLBACK_PWD_PROP_SUFFIX = ".password";

    private static final String USE_DERIVEDTABLE_FOR_UNIONSUBQUERIES_SUFFIX =
        ".useDerivedTableForUnionSubQueries";

    private static final String UNKNOWN_VENDOR = "unknown";
    private static final boolean DEFAULT_NEEDPASSWORD = false;

    private String vendor = null;
    private String vendorPropPrefix = null;
    private String tablePropPrefix = null;
    private String vendorProp = null;
    private String driverProp = null;
    private String openDBUrlProp = null;
    private String createDBUrlProp = null;
    private String closeDBUrlProp = null;
    private String userProp = null;
    private String passwordProp = null;
    private String needPasswordProp = null;

    public int txnRetryMax;    // Max number of retry
    public long txnRetryDelay; // Number of milliseconds to wait between retry

    public int connRetryMax = CONNECTION_RETRY_MAX_DEFAULT;
    public long connRetryDelay = CONNECTION_RETRY_DELAY_DEFAULT;

    // database connection
    private String driver = null;
    private String openDBUrl = null;
    private String createDBUrl = null;
    protected String closeDBUrl = null;
    private String user = null;
    private String password = null;
    private boolean needPassword = true;
    private boolean isDataSource = false;
    private boolean isPoolDataSource = false;

    private Object dataSource = null;

    protected boolean isHADB = false;
    protected boolean isOracle = false;
    protected boolean isOraDriver = false;
    protected boolean isMysql = false;
    private boolean isDerby = false;
    private boolean isDB2 = false;
    private boolean isPostgreSQL = false;
    private boolean supportBatch = false;
    private boolean supportGetGeneratedKey = false;
    private String dbProductName = null;
    private String dbProductVersion = null;
    private int dbProductMajorVersion = -1;
    private int sqlStateType = DatabaseMetaData.sqlStateSQL99;
    private boolean useDerivedTableForUnionSubQueries = false;

    protected String tableSuffix = null;

    protected BrokerConfig config = Globals.getConfig();
    protected BrokerResources br = Globals.getBrokerResources();
    protected Logger logger = Globals.getLogger();

    private boolean isJDBC4 = true;
    private boolean isClosing = false;

    // HashMap of database tables. table name->TableSchema
    protected HashMap tableSchemas = new HashMap();

    private ArrayList<String> reconnectPatterns = new ArrayList<String>();
    private static final String DEFAULT_CONNECTION_RETRY_PATTERN  = "(?s).*";

    private ArrayList<Integer> sqlRetriableErrorCodes = new ArrayList<Integer>();

    private Integer loginTimeout = null;

    /**
     */
    protected void initDBMetaData() throws BrokerException {

        int maxTableNameLength = 0;
        Connection conn = null;
        Exception myex = null;
        try {
            conn = getConnection( true );

            DatabaseMetaData dbMetaData = conn.getMetaData();
            dbProductName = dbMetaData.getDatabaseProductName();
            dbProductVersion = dbMetaData.getDatabaseProductVersion();
            dbProductMajorVersion = dbMetaData.getDatabaseMajorVersion();
            supportBatch = dbMetaData.supportsBatchUpdates();
            sqlStateType = dbMetaData.getSQLStateType();
            maxTableNameLength = dbMetaData.getMaxTableNameLength();
            String driverv = dbMetaData.getDriverVersion();
            supportGetGeneratedKey = dbMetaData.supportsGetGeneratedKeys();

            // Check to see if we're using an Oracle driver so
            // we know how to deal w/ Oracle LOB handling!
            isOraDriver = "oracle".equalsIgnoreCase(dbProductName);

            String logMsg = new StringBuffer(256)
                   .append(getLogStringTag()+"DBManager: database product name=")
                   .append(dbProductName)
                   .append(", database version number=")
                   .append(dbProductVersion)
                   .append(", driver version number=")
                   .append(driverv)
                   .append(", supports batch updates=")
                   .append(supportBatch)
                   .append(", supports getGeneratedKey=")
                   .append(supportGetGeneratedKey)
                   .toString();
            logger.log(Logger.FORCE, getLogStringTag()+dbProductName+", "+
                             dbProductVersion+", "+driverv);
            logger.log((Store.getDEBUG()? Logger.INFO:Logger.DEBUG), logMsg);
        } catch (SQLException e) {
            myex = e;
            logger.log(Logger.WARNING, BrokerResources.X_GET_METADATA_FAILED, e);
        } finally {
            closeSQLObjects( null, null, conn, myex );
        }

        if (maxTableNameLength > 0) {
            checkMaxTableNameLength(maxTableNameLength);
        }
    }

    protected abstract boolean getDEBUG();
    protected abstract boolean isStoreInited();

    protected abstract String getLogStringTag();
    protected abstract String getJDBCPropPrefix();
    protected abstract String getStoreTypeProp();
    protected abstract String getCreateStoreProp();
    protected abstract boolean getCreateStorePropDefault();

    protected abstract void
    checkMaxTableNameLength(int maxlenAllowed) throws BrokerException;

    public String toString() {
        return "CommDBManager";
    }

    protected boolean isJDBC4() {
        return isJDBC4;
    }

    protected void setJDBC4(boolean b) {
        isJDBC4 = b;
    }

    public String getOpenDBUrlProp() {
        return openDBUrlProp;
    }

    public String getOpenDBUrl() {
        return openDBUrl;
    }

    public String getVendorProp() {
        return vendorProp;
    }

    public String getVendor() {
        return vendor;
    }

    public List<String> getReconnectPatterns() {
        return reconnectPatterns;
    }

    private ConfigListener cfgListener = new ConfigListener() {
        public void validate(String name, String value)
            throws PropertyUpdateException {
            if (name.equals(getJDBCPropPrefix()+CONNECTION_RETRY_DELAY_PROP_SUFFIX)) {
                long v = -1L;
                try {
                    v = Long.parseLong(value);
                } catch (Exception e) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, br.getString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name + "=" + value), e);
                }
                if (v < 0L) {
                    throw new PropertyUpdateException(
                        PropertyUpdateException.InvalidSetting, name+"="+value);
                }
            }
        }
        public boolean update(String name, String value) {
            BrokerConfig cfg = Globals.getConfig();
            if (name.equals(getJDBCPropPrefix()+CONNECTION_RETRY_DELAY_PROP_SUFFIX)) {
                connRetryDelay = cfg.getLongProperty(getJDBCPropPrefix()+CONNECTION_RETRY_DELAY_PROP_SUFFIX);
            } else if (name.equals(getJDBCPropPrefix()+CONNECTION_RETRY_MAX_PROP_SUFFIX)) {
                connRetryMax = cfg.getIntProperty(getJDBCPropPrefix()+CONNECTION_RETRY_MAX_PROP_SUFFIX);
            }
            return true;
        }
    };


    protected void initDBManagerProps() throws BrokerException {

        String JDBC_PROP_PREFIX = getJDBCPropPrefix();

        String name = JDBC_PROP_PREFIX+USE_DERIVEDTABLE_FOR_UNIONSUBQUERIES_SUFFIX;
        useDerivedTableForUnionSubQueries = config.getBooleanProperty(name, false);
        if (useDerivedTableForUnionSubQueries) {
            logger.log(logger.INFO, name+"=true");
        }

        name = JDBC_PROP_PREFIX+LOGIN_TIMEOUT_PROP_SUFFIX; 
        String strv = config.getProperty(name);
        if (strv != null) {
            try {
                int v = Integer.parseInt(strv);
                if (v < 0) {
                    String emsg =  Globals.getBrokerResources().getKString(
                        BrokerResources.X_BAD_PROPERTY_VALUE, name+"="+strv);
                    throw new BrokerException(emsg);
                }
                loginTimeout = Integer.valueOf(v);
                logger.log(logger.INFO, name+"="+strv);
            } catch (Exception e) {
                if (e instanceof BrokerException) {
                    throw (BrokerException)e;
                }
                String emsg =  Globals.getBrokerResources().getKString(
                    BrokerResources.X_BAD_PROPERTY_VALUE, name+"="+strv)+": "+e;
                logger.log(Logger.ERROR, emsg, e);
                throw new BrokerException(emsg);
            }
        }
        txnRetryMax = config.getIntProperty(
            JDBC_PROP_PREFIX+TRANSACTION_RETRY_MAX_PROP_SUFFIX,
            TRANSACTION_RETRY_MAX_DEFAULT );
        txnRetryDelay = config.getLongProperty(
            JDBC_PROP_PREFIX+TRANSACTION_RETRY_DELAY_PROP_SUFFIX,
            TRANSACTION_RETRY_DELAY_DEFAULT);
        if (txnRetryDelay < 0L) {
            txnRetryDelay = TRANSACTION_RETRY_DELAY_DEFAULT;
        }

        connRetryMax = config.getIntProperty(
            JDBC_PROP_PREFIX+CONNECTION_RETRY_MAX_PROP_SUFFIX,
            CONNECTION_RETRY_MAX_DEFAULT );
        logger.log(logger.INFO, JDBC_PROP_PREFIX+
                   CONNECTION_RETRY_MAX_PROP_SUFFIX+"="+connRetryMax);
        connRetryDelay = config.getLongProperty(
            JDBC_PROP_PREFIX+CONNECTION_RETRY_DELAY_PROP_SUFFIX,
            CONNECTION_RETRY_DELAY_DEFAULT);
        if (connRetryDelay < 0L) {
            connRetryDelay = CONNECTION_RETRY_DELAY_DEFAULT;
        }
        logger.log(logger.INFO, JDBC_PROP_PREFIX+JDBC_PROP_PREFIX+
                   CONNECTION_RETRY_DELAY_PROP_SUFFIX+"="+connRetryDelay);

        Globals.getConfig().addListener(
            JDBC_PROP_PREFIX+CONNECTION_RETRY_MAX_PROP_SUFFIX, cfgListener);
        Globals.getConfig().addListener(
            JDBC_PROP_PREFIX+CONNECTION_RETRY_DELAY_PROP_SUFFIX, cfgListener);

        // get store type and double check that 'jdbc' is specified
        String type = config.getProperty(getStoreTypeProp());
        if (type == null || !type.equals(JDBC_STORE_TYPE)) {
            type = (type == null ? "" : type);
            throw new BrokerException(
                br.getKString(BrokerResources.E_NOT_JDBC_STORE_TYPE,
                getStoreTypeProp()+"="+type, JDBC_STORE_TYPE));
        }

        vendorProp = JDBC_PROP_PREFIX + VENDOR_PROP_SUFFIX;
        vendor = config.getProperty(vendorProp, UNKNOWN_VENDOR);
        vendorPropPrefix = JDBC_PROP_PREFIX + "." + vendor;

        tablePropPrefix = vendorPropPrefix + ".table";

        // get jdbc driver property
        driverProp = vendorPropPrefix + ".driver";
        driver = config.getProperty(driverProp);
        if (driver == null || (driver.length() == 0)) {
            // try fallback prop
            String fallbackProp = JDBC_PROP_PREFIX + ".driver";
            driver = config.getProperty(fallbackProp);
            if (driver == null || (driver.length() == 0)) {
                throw new BrokerException(
                    br.getKString(BrokerResources.E_NO_JDBC_DRIVER_PROP, driverProp));
            }
            driverProp = fallbackProp;
        }
        logger.log(logger.FORCE, driverProp+"="+driver);

        // get open database url property (optional for DataSource)
        openDBUrlProp = vendorPropPrefix + ".opendburl";
        openDBUrl = config.getProperty(openDBUrlProp);
        if (openDBUrl == null || (openDBUrl.length() == 0)) {
            // try fallback prop
            String fallbackProp = JDBC_PROP_PREFIX + ".opendburl";
            openDBUrl = config.getProperty(fallbackProp);
            if (openDBUrl != null && openDBUrl.trim().length() > 0) {
                openDBUrlProp = fallbackProp;
            }
        }
        openDBUrl = StringUtil.expandVariables(openDBUrl, config);
        if (openDBUrl != null) {
            logger.log(logger.FORCE, openDBUrlProp+"="+openDBUrl);
        }

        //
        // optional properties
        //

        // get create database url property
        createDBUrlProp = vendorPropPrefix + ".createdburl";
        createDBUrl = config.getProperty(createDBUrlProp);
        if (createDBUrl == null || (createDBUrl.length() == 0)) {
            // try fallback prop
            String fallbackProp = JDBC_PROP_PREFIX + ".createdburl";
            createDBUrl = config.getProperty(fallbackProp);
            if (createDBUrl != null) {
                createDBUrlProp = fallbackProp;
            }
        }
        createDBUrl = StringUtil.expandVariables(createDBUrl, config);
        if (createDBUrl != null) {
            logger.log(logger.FORCE, createDBUrlProp+"="+createDBUrl);
        }

        // get url to shutdown database
        closeDBUrlProp = vendorPropPrefix + ".closedburl";
        closeDBUrl = config.getProperty(closeDBUrlProp);
        if (closeDBUrl == null || (closeDBUrl.length() == 0)) {
            // try fallback prop
            String fallbackProp = JDBC_PROP_PREFIX + ".closedburl";
            closeDBUrl = config.getProperty(fallbackProp);
            if (closeDBUrl != null) {
                closeDBUrlProp = fallbackProp;
            }
        }
        closeDBUrl = StringUtil.expandVariables(closeDBUrl, config);
        if (closeDBUrl != null) {
            logger.log(logger.FORCE, closeDBUrlProp+"="+closeDBUrl);
        }

        // user name to open connection
        userProp = vendorPropPrefix + ".user";
        user = config.getProperty(userProp);
        if (user == null) {
            // try fallback prop
            user = config.getProperty(JDBC_PROP_PREFIX+FALLBACK_USER_PROP_SUFFIX);
            if (user != null) {
                userProp = JDBC_PROP_PREFIX+FALLBACK_USER_PROP_SUFFIX;
            }
        }

        String regex = vendorPropPrefix + CONNECTION_RETRY_REGEX_PROP_SUFFIX;
        int i = 1;
        String p = null;
        String v = null;
        while (true) {
            p = regex+"."+i++;
            v = config.getProperty(p);
            if (v == null) {
                break;
            }
            try {
                 "A".matches(v);
                 reconnectPatterns.add(v);
                 logger.log(logger.INFO, p+"="+v);
            } catch (Exception e) {
                 throw new BrokerException("["+p+"="+v+"]: "+e.toString(), e);               
            }
        }
        if (reconnectPatterns.size() == 0) {
            reconnectPatterns.add(DEFAULT_CONNECTION_RETRY_PATTERN);
            logger.log(logger.INFO, regex+"="+DEFAULT_CONNECTION_RETRY_PATTERN);
        }
        String sqlRetriablesp  =  vendorPropPrefix+SQL_RETRIABLE_ERROR_CODES_PROP_SUFFIX; 
        String sqlRetriablesv = config.getProperty(sqlRetriablesp);
        List<String> sqlRetriables = (List<String>)config.getList(sqlRetriablesp);
        if (sqlRetriables != null && sqlRetriables.size() > 0) {
            logger.log(logger.INFO, sqlRetriablesp+"="+sqlRetriablesv);
            Integer val = null;
            for (String ecode : sqlRetriables) {
                 try {
                     val = Integer.valueOf(ecode);
                     sqlRetriableErrorCodes.add(val);
                 } catch (Exception e) {
                     String[] eargs = { ecode, sqlRetriablesp+"="+sqlRetriablesv, e.toString() };
                     logger.log(logger.WARNING, 
                         br.getKString(br.W_IGNORE_VALUE_IN_PROPERTY_LIST, eargs)); 
                 }
            }
        }

        initTableSuffix();
    }
    public boolean isRetriableSQLErrorCode(int errorCode) {
        return sqlRetriableErrorCodes.contains(Integer.valueOf(errorCode));
    }

    protected abstract void initTableSuffix() throws BrokerException;

    protected void initDBDriver() throws BrokerException {

        String JDBC_PROP_PREFIX = getJDBCPropPrefix();

        // load jdbc driver
        try {
               
            Class driverCls = null;
            Object driverObj = null;
            if (Globals.isNucleusManagedBroker()) {
                driverObj = Globals.getHabitat().getService(DataSource.class, driver);
                if (driverObj == null) {
                    driverObj = Globals.getHabitat().getService(
                                    ConnectionPoolDataSource.class, driver);
                }
                if (driverObj == null && isMysql()) {
                    driverObj = Globals.getHabitat().getService(
                                    java.sql.Driver.class, "com.mysql.jdbc.Driver");
                }
                if (driverObj == null) {
                    throw new ClassNotFoundException(driver);
                }
                driverCls = driverObj.getClass();
            } else {
                driverCls = Class.forName(driver);
                driverObj = driverCls.newInstance();
            }
            // Check if driver is a DataSource
            if (driverObj instanceof ConnectionPoolDataSource) {
                isDataSource = true;
                isPoolDataSource = true;
            } else if (driverObj instanceof DataSource) {
                isDataSource = true;
            } else {
                // Not using DataSource make sure driver's url is specified
                if (openDBUrl == null || (openDBUrl.length() == 0)) {
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_NO_DATABASE_URL_PROP, openDBUrlProp));
                }
            }

            // Initialize DataSource properties
            if ( isDataSource ) {
                dataSource = driverObj;
                initDataSource(driverCls, dataSource);
            } else {
                initDriverManager();
            }
        } catch (InstantiationException e) {
            throw new BrokerException(
                br.getKString(BrokerResources.E_CANNOT_LOAD_JDBC_DRIVER, driver), e);
        } catch (IllegalAccessException e) {
            throw new BrokerException(
                br.getKString(BrokerResources.E_CANNOT_LOAD_JDBC_DRIVER, driver), e);
        } catch (ClassNotFoundException e) {
            throw new BrokerException(
                br.getKString(BrokerResources.E_CANNOT_LOAD_JDBC_DRIVER, driver), e);
        } catch (BrokerException e) {
             throw e;
        }

        // password to open connection; do this last because we want to init
        // the datasource to get the url that might be needed in getPassword()
        password = getPassword();

        // verify dbVendor is not unknown, i.e. upgrading from old store
        if (UNKNOWN_VENDOR.equals(vendor)) {
            // need to figure out the vendor
            Connection conn = null;
            String dbName = null;
            try {
                conn = getNewConnection(true);
                DatabaseMetaData dbMetaData = conn.getMetaData();
                dbName = dbMetaData.getDatabaseProductName();
            } catch (Exception e) {
                // Ignore error for now!
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {}
                }
            }

            if ("oracle".equalsIgnoreCase(dbName)) {
                vendor = "oracle";
                vendorPropPrefix = JDBC_PROP_PREFIX + "." + vendor;
                tablePropPrefix = vendorPropPrefix + ".table";
            } else {
                throw new BrokerException(
                    br.getKString(BrokerResources.E_NO_DATABASE_VENDOR_PROP,
                    getJDBCPropPrefix()+VENDOR_PROP_SUFFIX));
            }
        }

        if ( vendor.equalsIgnoreCase( "hadb" ) ) {
            isHADB = true;
            checkHADBJDBCLogging();
        } else if ( vendor.equalsIgnoreCase( "oracle" ) ) {
            isOracle = true;
        } else if ( vendor.equalsIgnoreCase( "mysql" ) ) {
            isMysql = true;
        } else if ( vendor.equalsIgnoreCase( "derby" ) ) {
            isDerby = true;
        } else if ( vendor.equalsIgnoreCase( "postgresql" ) ) {
            isPostgreSQL = true;
        } else if ( vendor.equalsIgnoreCase( "db2" ) ) {
            isDB2 = true;
        }
    }

    public boolean isUseDerivedTableForUnionSubQueries() {
        return useDerivedTableForUnionSubQueries;
    }

    public boolean isPoolDataSource() {
        return isPoolDataSource;
    }

    public Hashtable getDebugState() {
        Hashtable ht = new Hashtable();
        ht.put("vendor", ""+vendor);;
        ht.put("user", ""+user);;
        ht.put("isDataSource", Boolean.valueOf(isDataSource));
        ht.put("isPoolDataSource", Boolean.valueOf(isPoolDataSource));
        ht.put("supportBatch", Boolean.valueOf(supportBatch));
        ht.put("supportGetGeneratedKey", Boolean.valueOf(supportGetGeneratedKey));
        ht.put("sqlStateType", String.valueOf(sqlStateType));
        ht.put("isJDBC4", Boolean.valueOf(isJDBC4));
        return ht;
    }

    public TableSchema getTableSchema(String tableName)
    throws BrokerException {

        HashMap schemas = getTableSchemas();
        TableSchema tableSchema = (TableSchema)schemas.get( tableName );
        if ( tableSchema == null || tableSchema.tableSQL.length() == 0 ) {
            throw new BrokerException(
            br.getKString( BrokerResources.E_NO_JDBC_TABLE_PROP,
            tableName, getJDBCPropPrefix()+VENDOR_PROP_SUFFIX ) );
        }
        return tableSchema;
    }

    public abstract boolean hasSupplementForCreateDrop(String tableName);
  
    protected void createTableSupplement(Statement stmt,
                                         TableSchema tableSchema,
                                         String tableName)
                                         throws BrokerException {

        String sql = tableSchema.afterCreateSQL;
        if (sql == null) {
            return;
        }
        logger.logToAll( Logger.INFO,
            br.getKString(br.I_EXEC_CREATE_TABLE_SUPPLEMENT, sql, tableName) );
        try {
            int cnt = executeUpdateStatement( stmt, sql );
            if (cnt != 0 || stmt.getWarnings() != null) {
                String emsg = "["+sql+"]: "+stmt.getWarnings()+"(return="+cnt+")";
                logger.log(logger.ERROR, emsg);
                throw new BrokerException(emsg);
            }
        } catch (Throwable t) {
            if (t instanceof BrokerException) {
                throw (BrokerException)t;
            }
            String emsg = "["+sql+"]: "+t.getMessage();
            logger.logStack(logger.ERROR, emsg, t);
            throw new BrokerException(emsg);
        }
    }

    /**
     * subclass must override this if not no-op
     */
    public void dropOldTableSupplement(Statement stmt, 
    String oldTableName, boolean throwException) 
    throws BrokerException {
    }

    public void dropTableSupplement(Statement stmt,
                                    TableSchema tableSchema,
                                    String tableName,
                                    boolean throwException)
                                    throws BrokerException {

        String sql = tableSchema.afterDropSQL;
        if (sql == null) {
            return;
        }

        logger.logToAll( Logger.INFO,
            br.getKString(br.I_EXEC_DROP_TABLE_SUPPLEMENT, sql, tableName) );
        try {
            int cnt = executeUpdateStatement( stmt, sql );
            if (cnt != 0 || stmt.getWarnings() != null) {
                String emsg = "["+sql+"]: "+stmt.getWarnings()+"(return="+cnt+")";
                throw new BrokerException(emsg);
            }
        } catch (Throwable t) {
            BrokerException ex = null;
            if (t instanceof BrokerException) {
                ex = (BrokerException)t;
            } else {
                String emsg = "["+sql+"]: "+t.getMessage();
                ex = new BrokerException(emsg);
            }
            if (throwException) {
                logger.logStack(logger.WARNING, ex.getMessage(), t);
                throw ex;
            }
            logger.log(logger.INFO, ex.getMessage());
        }
    }

    /**
     * Get a database connection using the CREATEDB_URL.
     */
    public Connection connectToCreate() throws BrokerException {

        if (createDBUrl == null) {
            throw new BrokerException(br.getKString(
                BrokerResources.E_NO_DATABASE_URL_PROP, createDBUrlProp));
        }

        // make database connection
        Connection conn = null;
        try {
            if (user == null) {
                conn = DriverManager.getConnection(createDBUrl);
            } else {
                conn = DriverManager.getConnection(createDBUrl, user, password);
            }

            conn.setAutoCommit(false);
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.close();
                } catch(SQLException sqe) {
                    logger.logStack(Logger.WARNING, 
                    "Unable to close JDBC connection: "+sqe+ " after SQLException ", e);
                }
            }
            throw new BrokerException(br.getKString(
                BrokerResources.E_CANNOT_GET_DB_CONNECTION, createDBUrl), e);
        }

        return conn;
    }

    /**
     * Create a new database connection either from the DataSource or
     * the DriverManager using the OPENDB_URL.
     */
    public Connection getNewConnection(boolean autocommit) throws BrokerException {

        Object c = newConnection(true);

        if (c instanceof PooledConnection) {
            final PooledConnection pc = (PooledConnection)c;
            pc.addConnectionEventListener(new ConnectionEventListener() {
                    public void connectionClosed(ConnectionEvent event) { 
                        pc.removeConnectionEventListener(this);
                        try {
                            pc.close(); 
                        } catch (Exception e) {
                            logger.log(logger.WARNING,
                                br.getKString(br.W_DB_CONN_CLOSE_EXCEPTION,
                                pc.getClass().getName()+"[0x"+pc.hashCode()+"]", e.toString()));
                        }
                    }
                    public void connectionErrorOccurred(ConnectionEvent event) {
                        logger.log(logger.WARNING, br.getKString(br.W_DB_CONN_ERROR_EVENT,
                                   "0x"+pc.hashCode(), ""+event.getSQLException()));
                        pc.removeConnectionEventListener(this);
                        try {
                            pc.close(); 
                        } catch (Exception e) {
                            logger.log(logger.WARNING,
                            br.getKString(br.W_DB_CONN_CLOSE_EXCEPTION,
                            pc.getClass().getName()+"[0x"+pc.hashCode()+"]", e.toString()));
                        }
                    }
                }
            );
        }

        try {

            Util.RetryStrategy retry = null;
            do {
                try {
                    Connection conn = null;

                    if (c instanceof PooledConnection) {
                        conn = ((PooledConnection)c).getConnection();
                    } else {
                        conn = (Connection)c;
                    }
                    conn.setAutoCommit(autocommit);

                    return conn;
 
                } catch (Exception e) {
                    BrokerException ex = new BrokerException(e.getMessage(), e);
                    if ( retry == null ) {
                         retry = new Util.RetryStrategy(this);
                    }
                    retry.assertShouldRetry( ex );
                }
            } while (true);

        } catch (BrokerException e) {
            try {
                if (c instanceof PooledConnection) {
                    ((PooledConnection)c).close();
                } else {
                    ((Connection)c).close();
                }
            } catch (Throwable t) {
                logger.log(Logger.WARNING, 
                           br.getKString(br.W_DB_CONN_CLOSE_EXCEPTION,
                           c.getClass().getName()+"[0x"+c.hashCode()+"]", t.toString()));
            }
            throw e;
        }
    }

    protected Object getNewConnection() throws BrokerException {
        return newConnection(false);
    }

    protected Object newConnection(boolean doRetry) throws BrokerException {

        // make database connection; database should already exist
        Util.RetryStrategy retry = null;
        do {
            try {
                try {
                    Object conn = null;

                    if (dataSource != null) {
                        if (isPoolDataSource) {
                            if (user == null) {
                                conn = ((ConnectionPoolDataSource)dataSource)
                                    .getPooledConnection();
                            } else {
                                conn = ((ConnectionPoolDataSource)dataSource)
                                    .getPooledConnection(user, password);
                            }
                        } else {
                            if (user == null) {
                                conn = ((DataSource)dataSource).getConnection();
                            } else {
                                conn = ((DataSource)dataSource).getConnection(user, password);
                            }
                        }
                    } else {
                        if (user == null) {
                            conn = DriverManager.getConnection(openDBUrl);
                        } else {
                            conn = DriverManager.getConnection(openDBUrl, user, password);
                        }
                    }

                    return conn;

                } catch (Exception e) {
                    throw new BrokerException(br.getKString(
                        BrokerResources.E_CANNOT_GET_DB_CONNECTION, openDBUrl), e);
                }

            } catch (BrokerException e) {
                if (!doRetry) {
                    throw e;
                }
                // Exception will be log & re-throw if operation cannot be retry
                if ( retry == null ) {
                    retry = new Util.RetryStrategy(this, connRetryDelay, connRetryMax, true);
                }
                retry.assertShouldRetry( e );
            }
        } while (true);
    }


    public Connection getConnection(boolean autocommit)
    throws BrokerException {
        Util.RetryStrategy retry = null;
        do {
            try {
                Connection conn = getConnectionNoRetry(autocommit);
                return conn;
            } catch (BrokerException e) {
                if (BrokerStateHandler.isStoreShutdownStage1()) {
                    throw e;
                }
                // Exception will be log & re-throw if operation cannot be retry
                if ( retry == null ) {
                    retry = new Util.RetryStrategy(this, connRetryDelay, connRetryMax, true);
                }
                retry.assertShouldRetry( e );
            }
        } while (true);
    }

    /**
     */
    public Connection getConnectionNoRetry(boolean autocommit)
    throws BrokerException {

        Exception exception = null;
        Connection conn = getConnection();
        try {
            // Set connection behavior

            try {
                conn.setAutoCommit(autocommit);
            } catch ( SQLException e ) {
                exception = e;
                throw new BrokerException(
                    br.getKString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unable to set connection's auto-commit mode"), e);
            }

            int txnIsolation = -1;
            try {
                if (isHADB) {
                    txnIsolation = conn.getTransactionIsolation();
                    if (txnIsolation != Connection.TRANSACTION_READ_COMMITTED) {
                        conn.setTransactionIsolation(
                            Connection.TRANSACTION_READ_COMMITTED);
                    }
                }
            } catch ( SQLException e ) {
                exception = e;
                throw new BrokerException(
                    br.getKString(BrokerResources.X_INTERNAL_EXCEPTION,
                    "Unable to set connection's transaction isolation level, current= "
                    + txnIsolation), e);
            }
        } finally {
            if (exception != null) {
                freeConnection(conn, exception);
            }
        }

        return conn;
    }

    protected abstract Connection 
    getConnection() throws BrokerException;

	public abstract void 
    freeConnection(Connection conn, Throwable thr) throws BrokerException;

    protected abstract BaseDAO getFirstDAO() throws BrokerException;

    public void setIsClosing() {
        isClosing = true;
    }

    public boolean getIsClosing() {
        return isClosing;
    }

    protected void close() {
        // Use closeDBUrl to close embedded db
        if (closeDBUrl != null) {
            Connection conn = null;
            try {
                conn = DriverManager.getConnection(closeDBUrl);
            } catch (SQLException e) {
                if (Store.getDEBUG()) {
                    logger.log(Logger.DEBUG, getLogStringTag()+
                        BrokerResources.I_DATABASE_SHUTDOWN, closeDBUrl, e);
                }
            } finally {
                if (conn != null) {
                    try {
                    conn.close();
                    } catch (Exception e) {
                    /* ignore */
                    }
                }
            }
        }
    }

    public boolean supportsBatchUpdates() {
        return supportBatch;
    }

    public boolean supportsGetGeneratedKey() {
        return supportGetGeneratedKey;
    }

    public boolean isHADB() {
        return isHADB;
    }

    public boolean isMysql() {
        return isMysql;
    }

    public boolean isDerby() {
        return isDerby;
    }

    public boolean isDB2() {
        return isDB2;
    }

    public boolean isPostgreSQL() {
        return isPostgreSQL;
    }
    
    public boolean isOracle() {
        return isOracle;
    }

    public boolean isOracleDriver() {
        return isOraDriver;
    }

    public int getDBProductMajorVersion() {
        return dbProductMajorVersion;
    }

    public int getSQLStateType() {
        return sqlStateType;
    }

    public String getOpenDBURL() {
        return openDBUrl;       
    }

    public String getCreateDBURL() {
        return createDBUrl;
    }

    public String getUser() {
        return user;
    }

    public String getClusterID() {
        return Globals.getClusterID();
    }

    public String getTableName(String tableNamePrefix) {
        if (tableNamePrefix == null || tableNamePrefix.length() == 0) {
            throw new NullPointerException();
        }
        return tableNamePrefix + tableSuffix;
    }

    public String getTableSuffix() {
        return tableSuffix;
    }
    
    /**
     * subclass must override this if this method is used 
     */
    public String[] getAllOldTableNames() {
        return new String[0];
    }

    public abstract String[] getTableNames(int version);

    public String getDriver() {
        return driver;
    }

    public abstract Iterator allDAOIterator() throws BrokerException;

    // get table schema for the current store version returns a Hashtable with
    // table names map to the create table SQL
    HashMap getTableSchemas() {
        return tableSchemas;
    }

    public abstract int 
    checkStoreExists(Connection conn) throws BrokerException;

    // Return 0 if store has not been created, -1 if some tables are missing, or
    // a value > 0 if all tables for the store have already been created
    protected int checkStoreExists(Connection conn, String version)
    throws BrokerException {

        Map tables = getTableNamesFromDB( conn, 
                         (version == null ? 
                          tableSuffix:(version+tableSuffix)), true );

        int total = 0;
        int found = 0;
        Iterator itr = allDAOIterator();
        StringBuffer sbuf =  new StringBuffer();
        while ( itr.hasNext() ) {
            total ++;
            String tname = ((BaseDAO)itr.next()).getTableName();
            if ( tables.containsKey( tname.toLowerCase() ) ) {
                found++;
            } else {
                sbuf.append(tname+" ");
            }
        }

        if ( (found > 0) && (found != total) ) {
            logger.log(logger.WARNING, br.getKString(br.W_TABLE_NOT_FOUND_IN_DATABASE,
                       sbuf.toString()+"["+total+","+found+", "+tableSuffix+"]"));
            return -1;  // There is a problem! some tables are missing
        } else {
            return found;
        }
    }

    // Get all table names from the db that matchs the specified name pattern .
    // If name pattern is not specified then use tableSuffix for this broker.
    // As a precaution, only get table that starts with "mq" or "MQ".
    public Map getTableNamesFromDB( Connection conn,
                                    String namePattern,
                                    boolean isSuffix )
                                    throws BrokerException {

        // If name pattern not specified then use the table suffix
        if ( namePattern == null || namePattern.trim().length() == 0 ) {
            namePattern = tableSuffix;
            isSuffix = true;
        }
        namePattern = namePattern.toLowerCase();

        HashMap tableNames = new HashMap();

        Util.RetryStrategy retry = null;
        do {
            boolean myConn = false;
            ResultSet rs = null;
            Exception myex = null;
            try {
                // Get a connection
                if ( conn == null ) {
                    conn = getConnection( true );
                    myConn = true;
                }

                DatabaseMetaData dbMetaData = conn.getMetaData();

                String[] tTypes = { "TABLE" };
                rs = dbMetaData.getTables( null, null, null, tTypes );
                while ( rs.next() ) {
                    String tableName = rs.getString( "TABLE_NAME" );
                    if ( tableName != null ) {
                        tableName = tableName.trim();
                        if (tableName.length() == 0) { 
                            throw new BrokerException(br.getKString(
                            br.X_DB_RETURN_EMPTY_TABLENAME, "DatabaseMetaData.getTables()"));
                        }
                        if (tableName.length() < 2) { 
                            continue;
                        }
                        // Only process MQ tables
                        char ch1 = tableName.charAt(0);
                        char ch2 = tableName.charAt(1);
                        if ( ( ch1 == 'm' || ch1 == 'M' ) &&
                             ( ch2 == 'q' || ch2 == 'Q' ) ) {
                            // Saves table name that match name pattern
                            String key = tableName.toLowerCase();
                            if (isSuffix) {
                                if (key.endsWith( namePattern )) {
                                    tableNames.put( key, tableName );
                                }
                            } else if ( key.indexOf( namePattern ) > -1 ) {
                                tableNames.put( key, tableName );
                            }
                        }
                    }
                }

                break; // We're done so break out from retry loop
            } catch ( Exception e ) {
                myex = e;
                // Exception will be log & re-throw if operation cannot be retry
                if ( retry == null ) {
                    retry = new Util.RetryStrategy(this);
                }
                retry.assertShouldRetry( e );
            } finally {
                if ( myConn ) {
                    closeSQLObjects( rs, null, conn, myex );
                }
            }
        } while (true);

        return tableNames;
    }

    public abstract void
    closeSQLObjects(ResultSet rset, Statement stmt,
                    Connection conn, Throwable ex)
                    throws BrokerException;
    
    public static SQLException
    wrapSQLException(String msg, SQLException e) {
        SQLException e2 = new MQSQLException(
            msg + ": " + e.getMessage()+
            "["+e.getSQLState()+", "+e.getErrorCode()+"]",
            e.getSQLState(), e.getErrorCode());
        e2.initCause(e);
        e2.setNextException(e);
        return e2;
    }

    public static IOException
    wrapIOException(String msg, IOException e) {
        IOException e2 = new IOException(msg + ": " + e.getMessage());
        e2.initCause(e);
        return e2;
    }

    /**************************************************************
     * These are a few wrapper methods for JDBC methods that involves
     * SQL strings.  They are here in order to aggregate following 
     * findbug low warnings 
     *
     * SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING
     * SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE 
     ***************************************************************/
    public static final PreparedStatement 
    createPreparedStatement(Connection conn, String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }
    public static final PreparedStatement 
    createPreparedStatement(Connection conn, String sql, int autoGeneratedKeys)
    throws SQLException {
        return conn.prepareStatement(sql, autoGeneratedKeys);
    }
    public static final PreparedStatement 
    createPreparedStatement(Connection conn, String sql,  String[] columnNames)
    throws SQLException {
        return conn.prepareStatement(sql, columnNames);
    }
    public static final boolean 
    executeStatement(Statement stmt, String sql) throws SQLException {
        return stmt.execute(sql);
    }
    public static final int 
    executeUpdateStatement(Statement stmt, String sql) throws SQLException {
        return stmt.executeUpdate(sql);
    }
    public static final ResultSet 
    executeQueryStatement(Statement stmt, String sql) throws SQLException {
        return stmt.executeQuery(sql);
    }
    /************************************************************
     * End of JDBC SQL statements wrappers
     *************************************************************/

    private void initDriverManager() throws BrokerException {
        if (loginTimeout != null) {
            try {
                DriverManager.setLoginTimeout(loginTimeout.intValue());
            } catch (Exception e) {
                throw new BrokerException(Globals.getBrokerResources().
                    getKString(BrokerResources.X_JDBC_DRIVER_SET_LOGIN_TIMEOUT,
                    driver, e.toString())); 
            }
        }
    }

    private void initDataSource( Class dsClass, Object dsObject ) 
    throws BrokerException {

        if (loginTimeout != null && dsObject != null) {
            try {
                ((javax.sql.CommonDataSource)dsObject).
                    setLoginTimeout(loginTimeout.intValue());
            } catch (Exception e) {
                throw new BrokerException(
                    Globals.getBrokerResources().getKString(
                    BrokerResources.X_JDBC_DRIVER_SET_LOGIN_TIMEOUT,
                    driver, e.toString())); 
            }
        }

    	// Get a list of property names to initialize the Data Source,
        // e.g. imq.persist.jdbc.<dbVendor>.property.*
        String propertyPrefix = vendorPropPrefix + ".property.";
        List list = config.getPropertyNames(propertyPrefix);
        if (list.size() == 0) {
            if (Store.getDEBUG()) {
                logger.log(Logger.DEBUG, "DataSource properties not specified!");
            }
        }

        Method[] methods = dsClass.getMethods();
        Object[] arglist = new Object[1];

        // Invoke the setter method for each DataSource property
        Iterator itr = list.iterator();
        while (itr.hasNext()) {
            Exception error = null;
            String errorMsg = null;

            String propName = (String)itr.next();
            String propValue = config.getProperty(propName).trim();

            if (propValue.length() > 0) {
                String prop = propName.substring(propertyPrefix.length(),
                    propName.length());

                // Map Datasource's url property to openDBUrl
                if ( prop.equalsIgnoreCase( "url" ) ||
                     prop.equalsIgnoreCase( "serverList" ) ) {
                     openDBUrl = propValue;
                } else {
                     logger.log(Logger.FORCE, propName+"="+propValue);
                }

            	// Find the DataSource's method to set the property
                String methodName = ("set" + prop).toLowerCase();
                Method method = null; // The DataSource method
                Class paramType = null; // The param type of this method
                for (int i = 0, len = methods.length; i < len; i++) {
                    Method m = methods[i];
                    Class[] paramTypes = m.getParameterTypes();
                    if (methodName.equals(m.getName().toLowerCase()) &&
                        paramTypes.length == 1) {
                        // Found the setter method for this property
                        method = m;
                        paramType = paramTypes[0];
                        break;
                    }
                }

                if (method != null ) {
                    try {
                        if (paramType.equals( Boolean.TYPE )) {
                            arglist[0] = Boolean.valueOf(propValue);
                            method.invoke(dsObject, arglist);
                        } else if (paramType.equals(Integer.TYPE)) {
                            arglist[0] = Integer.valueOf(propValue);
                            method.invoke(dsObject, arglist);
                        } else if (paramType.equals(String.class)) {
                            arglist[0] = propValue;
                            method.invoke(dsObject, arglist);
                        } else {
                            errorMsg = "Invalid DataSource Property: " +
                            	propName + ", value: " + propValue;
                        }
                    } catch (Exception e) {
                        error = e;
                        errorMsg = "Unable to initialize DataSource Property: " +
                            propName + ", value: " + propValue;
                    }
                } else {
                    errorMsg = "Invalid DataSource Property: " + propName +
                    	", value: " + propValue;
                }
            } else {
                errorMsg = "Invalid DataSource Property: " + propName +
                	", value: " + propValue;
            }

            if (errorMsg != null) {
                logger.log(Logger.ERROR, errorMsg, error);
            }
        }
    }

    /**
     * Extract all table definition properties and put it in tables.
     * Format of the properties is:
     * - name is imq.persist.jdbc.<vendor>.table.[tablename]
     *   where [tablename] is the name of the table
     * - value is the create table SQL with the table name specified
     *   as ${name} so that we can replace it with the corresponding name
     */
    protected void loadTableSchema() throws BrokerException {

        List list = config.getPropertyNames(tablePropPrefix);
        if (list.size() == 0) {
            throw new BrokerException("Table definition not found for " + vendor);
        }

        // Sort the list to ensure the table definition will come before
        // the table index definition.
        Collections.sort(list);

        // Variable ${name}, ${tableoption} and ${index} to substitute for the
        // create table SQL and create table index SQL
        Properties vars = new Properties();

        // set table option
        String key = vendorPropPrefix + "." + TABLEOPTION_NAME_VARIABLE;
        String value = config.getProperty(key, "").trim();
        if (isMysql) {
            if (value.toUpperCase().matches(".*ENGINE\\s*=\\s*MYISAM.*")) { 
                String emsg = Globals.getBrokerResources().getKString(
                              BrokerResources.X_UNSUPPORTED_PROPERTY_VALUE,
                              value, key+"="+value);
                throw new BrokerException(emsg);
            }
        }
        vars.setProperty( TABLEOPTION_NAME_VARIABLE, value );

        if (!value.equals("")) {
            logger.log(logger.FORCE, key+"="+value);
        }

        Iterator itr = list.listIterator();
        while (itr.hasNext()) {
            key = ((String)itr.next()).trim();
            value = config.getProperty(key).trim();

            int i = key.indexOf( ".index." );
            if (i > 0) {
                // table index definition:
                //    imq.persist.jdbc.<dbVendor>.table.<name>.index.<index>=<SQL>
                int n = key.indexOf( ".table." );
                String tname = key.substring( n + 7, i ) + tableSuffix;
                String iname = tname + key.substring( i + 7 );

                // set table name & index name in the variable table so we can expand it
                vars.setProperty( TABLE_NAME_VARIABLE, tname );
                vars.setProperty( INDEX_NAME_VARIABLE, iname );
                String sql = StringUtil.expandVariables( value, vars );

                TableSchema schema = (TableSchema)tableSchemas.get( tname );
                if ( schema != null ) {
                    schema.addIndex( iname, sql );
                }
            } else if ((i = key.indexOf( ".aftercreate" )) > 0) {
                int n = key.indexOf( ".table." );
                String tname = key.substring( n + 7, i ) + tableSuffix;
                vars.setProperty( TABLE_NAME_VARIABLE, tname );
                String sql = StringUtil.expandVariables( value, vars );
                TableSchema schema = (TableSchema)tableSchemas.get( tname );
                if ( schema != null ) {
                    schema.setAfterCreateSQL( sql );
                }
            } else if ((i = key.indexOf( ".afterdrop" )) > 0) {
                int n = key.indexOf( ".table." );
                String tname = key.substring( n + 7, i ) + tableSuffix;
                vars.setProperty( TABLE_NAME_VARIABLE, tname );
                String sql = StringUtil.expandVariables( value, vars );
                TableSchema schema = (TableSchema)tableSchemas.get( tname );
                if ( schema != null ) {
                    schema.setAfterDropSQL( sql );
                }
                
            } else {
                // table definition:
                //    imq.persist.jdbc.<dbVendor>.table.<tableName>=<SQL>
                int n = key.lastIndexOf('.');
                String tname = key.substring( n + 1 ) + tableSuffix;

                // set table name in the variable table so we can expand it
                vars.setProperty( TABLE_NAME_VARIABLE, tname );
                String sql = StringUtil.expandVariables( value, vars );

                // add table schema object
                TableSchema schema = new TableSchema( tname, sql );
                tableSchemas.put( tname, schema );
            }
        }

        checkTables();
    }

    // make sure definition for all tables needed by the broker is specified
    private void checkTables() throws BrokerException {

        Iterator itr = allDAOIterator();
        while ( itr.hasNext() ) {
            BaseDAO dao = (BaseDAO)itr.next();
            String tname = dao.getTableName();
            TableSchema schema = (TableSchema)tableSchemas.get(tname);
            if (schema == null || schema.tableSQL.length() == 0) {
                throw new BrokerException(
                    br.getKString(BrokerResources.E_NO_JDBC_TABLE_PROP,
                    tname, getJDBCPropPrefix()+"."+tname));
            } else {
                if (Store.getDEBUG()) {
                    logger.log(Logger.DEBUG, tname + ": '" + schema.tableSQL + "'");
                }
            }
        }
    }

    private String getPassword() {

        String JDBC_PROP_PREFIX = getJDBCPropPrefix();

        // get private property first
        passwordProp = vendorPropPrefix + ".password";
        String dbpw = config.getProperty(passwordProp);
        if (dbpw == null) {
            // try fallback prop
            dbpw = config.getProperty(JDBC_PROP_PREFIX+FALLBACK_PWD_PROP_SUFFIX);
            if (dbpw != null) {
                passwordProp = JDBC_PROP_PREFIX+FALLBACK_PWD_PROP_SUFFIX;
            }
        }

        needPasswordProp = vendorPropPrefix + ".needpassword";
        if (config.getProperty(needPasswordProp) == null) {
            // try fallback prop
            String fallbackProp = JDBC_PROP_PREFIX + ".needpassword";
            if (config.getProperty(fallbackProp) != null) {
                needPasswordProp = fallbackProp;
            }
        }

        needPassword = config.getBooleanProperty(needPasswordProp, DEFAULT_NEEDPASSWORD);

        if (dbpw == null && needPassword) {
            int retry = 0;
            Password pw = new Password();
            if (pw.echoPassword()) {
                System.err.println(Globals.getBrokerResources().
                    getString(BrokerResources.W_ECHO_PASSWORD));
            }
            while ((dbpw == null || dbpw.trim().equals("")) && retry < 5) {
                System.err.print(br.getString(
                    BrokerResources.M_ENTER_DB_PWD, openDBUrl));
                System.err.flush();

                dbpw = pw.getPassword();

                // Limit the number of times we try reading the passwd.
                // If the VM is run in the background the readLine()
                // will always return null and we'd get stuck in the loop
                retry++;
            }
        }

        return dbpw;
    }

    private void checkHADBJDBCLogging() {
        // Enable HADB's JDBC driver logging
        String level = config.getProperty( "com.sun.hadb.jdbc.level" );
        if (level != null && level.length() > 0) {
            String logFile = StringUtil.expandVariables(
                "${imq.instanceshome}${/}${imq.instancename}${/}log${/}hadbLog.txt",
                config);

            logger.log( Logger.INFO,
                "Enable HADB's JDBC driver logging (level=" + level + "): " +
                logFile );

            try {
                // Setup logger
                java.util.logging.Logger jLogger =
                    java.util.logging.Logger.getLogger( "com.sun.hadb.jdbc" );
                java.util.logging.FileHandler fh =
                    new java.util.logging.FileHandler(logFile, true);
                fh.setFormatter(new java.util.logging.SimpleFormatter());
                jLogger.addHandler(fh);
                jLogger.setLevel(java.util.logging.Level.parse(level));
            } catch (Exception e) {
                logger.logStack( Logger.WARNING,
                    "Failed to enable HADB's JDBC driver logging", e );
            }
        }
    }

    /**
     * Putting our broker identifier in a lock column of a table that  
     * is served as lock for tables managed by this manager, or unlock
     * by setting the column to null.
     * @param conn Database connection
     * @param doLock
     * @throws BrokerException if the operation is not successful because
     * it cannot be locked
     */
    public void lockTables(Connection conn, boolean doLock)
    throws BrokerException {
        lockTables( conn, doLock, null );
    }
    public void lockTables(Connection conn, boolean doLock, Object extra)
    throws BrokerException {

        // Create the lockID for this broker
        LockFile lockFile = LockFile.getCurrentLockFile();
        String lockID = null;
        if (lockFile != null) {
            lockID = lockFile.getInstance() + ":" +
                lockFile.getHost() + ":" + lockFile.getPort();
        } else {
            // we are probably imqdbmgr
            MQAddress mqaddr = Globals.getMQAddress();
            String hostName = (mqaddr == null ? null:mqaddr.getHostName());
            if ( hostName == null ) {
                try {
                    InetAddress ia = InetAddress.getLocalHost();
                    hostName =  MQAddress.getMQAddress(
                        ia.getCanonicalHostName(), 0).getHostName();
                } catch (UnknownHostException e) {
                    hostName = "";
                    Globals.getLogger().log(Logger.ERROR,
                        BrokerResources.E_NO_LOCALHOST, e);
                } catch (Exception e) {
                    throw new BrokerException(e.getMessage(), e);
                }
            }
            lockID = Globals.getConfigName() + ":" + hostName + ":" + "imqdbmgr";
        }

        String currLck = getCurrentTableLock( conn, doLock );

        boolean doUpdate = false;
        String newLockID = null;
        String oldLockID = null;
        TableLock tableLock = new TableLock( currLck, getTableLockTableName() );
        if ( tableLock.isNull ) {
            // NO lock!
            if ( doLock ) {
                // Try to lock the tables for this broker
                doUpdate = true;
                newLockID = lockID;
            }
        } else {
            // There is a lock
            if ( isOurLock( lockFile, tableLock ) ) {
                if ( !doLock ) {
                    // Remove the lock, i.e. setting lockID to null
                    doUpdate = true;
                    oldLockID = tableLock.lockstr;
                }
            } else {
                // Not our lock; check whether the other broker is still running
                if ( validLock( tableLock ) ) {
                    // Only action is to reset if it is lock by imqdbmgr
                    if ( !doLock && tableLock.port == 0 ) {
                        doUpdate = true;
                        oldLockID = tableLock.lockstr;
                    } else {
                        throwTableLockedException( tableLock );
                    }
                } else {
                    // Lock is invalid so take over or reset the lock
                    if ( doLock ) {
                        // Take over the lock
                        doUpdate = true;
                        newLockID = lockID;
                        oldLockID = tableLock.lockstr;
                    } else {
                        // Remove the lock, i.e. setting lockID to null
                        doUpdate = true;
                        oldLockID = tableLock.lockstr;
                    }
                }
            }
        }

        if ( doUpdate ) {
            updateTableLock(conn, newLockID, oldLockID, extra);
        }
    }

    protected abstract String getTableLockTableName() throws BrokerException; 

    /**
     * @return must not null; the current lock id or "" if no lock
     */
    protected abstract String getCurrentTableLock( Connection conn, boolean doLock ) 
    throws BrokerException;

    /**
     * @param newLockID null if unlock
     */
    protected abstract void updateTableLock( Connection conn, 
    String newLockID, String oldLockID, Object extra ) 
    throws BrokerException;

    public abstract void throwTableLockedException( String lockid )
    throws BrokerException;

    protected abstract void throwTableLockedException( TableLock lock )
    throws BrokerException;

    // check whether the string in brokerlock belong to us
    private static boolean isOurLock(LockFile lf, TableLock lock) {

        if (lf != null) {
            // i am a broker
            if (lf.getPort() == lock.port &&
                LockFile.equivalentHostNames(lf.getHost(), lock.host, false) &&
                lf.getInstance().equals(lock.instance)) {
                return true;
            } else {
                return false;
            }
        } else {
            // i am imqdbmgr; assume the lock in the table is NOT our lock
            return false;
        }
    }

    // check whether the broker specified in the lock string is
    // still running, if it is, it's a valid lock
    private static boolean validLock(TableLock lock) {

        if (lock.port == 0) {
            // locked by imqdbmgr
            return true;
        }

        Socket s = null;
        try {
            // Try opening socket to other broker's portmapper. If we
            // can open a socket then the lock is valid
            s = new Socket(InetAddress.getByName(lock.host), lock.port);
            return true;
        } catch (Exception e) {
            // Looks like owner is not running. the lock is not valid
            return false;
        } finally {
            if ( s != null ) {
                try {
                    s.close();
                } catch (Exception e) {}
            }
        }
    }

    public static final Class TRANSIENT_SQLEX_CLASS = GET_TRANSIENT_SQLEX_CLASS();
    public static final Class NON_TRANSIENT_SQLEX_CLASS = GET_NONTRANSIENT_SQLEX_CLASS();
    public static final Class RECOVERABLE_SQLEX_CLASS = GET_RECOVERABLE_SQLEX_CLASS();
    public static final Class TRANSIENT_CONNECTION_SQLEX_CLASS = GET_TRANSIENT_CONNECTION_SQLEX_CLASS();
    public static final Class TIMEOUT_SQLEX_CLASS = GET_TIMEOUT_SQLEX_CLASS();
    public static final Class TRANSACTION_ROLLBACK_SQLEX_CLASS = GET_TRANSACTION_ROLLBACK_SQLEX_CLASS();

    private static Class GET_TRANSIENT_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLTransientException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class GET_RECOVERABLE_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLRecoverableException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class GET_NONTRANSIENT_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLNonTransientException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class GET_TRANSIENT_CONNECTION_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLTransientConnectionException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class GET_TRANSACTION_ROLLBACK_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLTransactionRollbackException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class GET_TIMEOUT_SQLEX_CLASS() {
        try {
            return Class.forName("java.sql.SQLTimeoutException");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    protected static class TableLock {
        public String host;
        public int port = 0;
        String lockstr;
        String instance;
        String portstr;
        boolean isNull = false;

        public TableLock(String str, String tableName) {
            lockstr = str;

            if (lockstr == null || lockstr.length() == 0) {
                isNull = true;
            } else {
                // lockstr has the format <instance>:<host>:<port>
                StringTokenizer st = new StringTokenizer(lockstr, " :\t\n\r\f");

                try {
                    instance = st.nextToken();
                    host = st.nextToken();
                    portstr = st.nextToken();
                    try {
                        port = Integer.parseInt(portstr);
                    } catch (NumberFormatException e) {
                        port = 0;
                    }
                } catch (NoSuchElementException e) {
                    Globals.getLogger().log(Logger.WARNING,
                        BrokerResources.W_BAD_BROKER_LOCK, lockstr, tableName);
                    lockstr = null;
                    isNull = true;
                }
            }
	}
    }

}
