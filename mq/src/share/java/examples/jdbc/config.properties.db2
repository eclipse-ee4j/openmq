#
# Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0, which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause
#

#############################################################################
#
# This file contains example properties for plugging a DB2 database
#

###########################################################################
# Persistence Settings
###########################################################################

# Type of data store
# To plug in a database, set the value to "jdbc".
imq.persist.store=jdbc

# An identifier to make database table names unique per broker.
# The specified value should contain alphanumeric characters only.
# The length of the identifier should not exceed the maximum length
# of a table name allowed in the database minus 12.
imq.brokerid=<alphanumeric id>

# DB2 database settings
##########################

# Specify DB2 as database vendor
imq.persist.jdbc.dbVendor=db2

# DB2 JDBC driver
imq.persist.jdbc.db2.driver=com.ibm.db2.jcc.DB2Driver

# The URL to connect to an DB2 database
imq.persist.jdbc.db2.opendburl=jdbc:db2://<host>:<port>/<database>

# User name used to access the database.
# This can also be specified by command line option for imqbroker and
# imqdbmgr.
imq.persist.jdbc.db2.user=<username>

# Specify whether the broker should prompt the user for a password for
# database access.
# It should be set to true if the password is not provided by other means
#imq.persist.jdbc.db2.needpassword=[true|false]
