/*
 * Copyright (c) 2012, 2017 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.messaging.jmq.jmsserver.persist.file;

import java.io.File;
import java.io.IOException;

import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.data.ToTxnLogConverter;
import com.sun.messaging.jmq.jmsserver.data.TransactionList;
import com.sun.messaging.jmq.jmsserver.persist.api.Store;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.util.log.Logger;

public class TxnConversionUtil {

	static String convertingToTxnLogMode = "convertingToTxnLogMode";
	static String convertingFromTxnLogMode = "convertingFromTxnLogMode";
	static boolean txnConversionRequired;
	static boolean convertingToTxnLog;
	
	public static final Logger logger = Globals.getLogger();
	
	public static String getPrefix()
	{
		return "TxnConversionUtil.";
	}

	public static void resetAllTransactionState(File rootDir)
			throws BrokerException {
		logger.log(Logger.INFO, getPrefix()+"resetAllTransactionState");
		deleteConversionFile(rootDir, convertingToTxnLogMode);
		deleteConversionFile(rootDir, convertingFromTxnLogMode);
		TransactionLogManager.deleteAllFileState(rootDir);
		TidList.deleteAllFiles(rootDir);
		TxnAckList.deleteAllFiles(rootDir);
	}

	public static void checkForIncompleteTxnConversion(File rootDir, boolean isNewTxnLogEnabled)
			throws BrokerException {
		// first check if we were converting to txn log format
		logger.log(Logger.DEBUG, getPrefix()+"checkForIncompleteTxnConversion");
		File convertingToTxnFormatFile = new File(convertingToTxnLogMode);
		File convertingFromTxnFormatFile = new File(convertingFromTxnLogMode);

		if (convertingToTxnFormatFile.exists()) {
			// sanity check
			if (convertingFromTxnFormatFile.exists())
				throw new BrokerException("Inconsistent state. Both "
						+ convertingToTxnFormatFile + " and "
						+ convertingFromTxnFormatFile + " exist.");
			resolveIncompleteConversionToTxnLog(rootDir,
					convertingToTxnFormatFile);
		}

		if (convertingFromTxnFormatFile.exists()) {
			resolveIncompleteConversionFromTxnLog(rootDir,
					convertingFromTxnFormatFile);
		}

		// we were not in the middle of converting

		// now let us check the status of the txn files

		// Do we have state in one mode only? (if not throw exception)
		boolean nonTxnLogStateExists = false;
		boolean txnLogStateExists = false;

		if (TransactionLogManager.transactionLogExists(rootDir)) {
			txnLogStateExists = true;
		}

		if (TidList.txFileExists(rootDir)) {
			nonTxnLogStateExists = true;
		}
		if (txnLogStateExists && nonTxnLogStateExists) {
			throw new BrokerException(
					"ack file and txnlog file both exist: transaction state exists in both txn-log and non-txn-log formats");
		}

		// Do we need to convert ( if so then set conversion required)
		if (isNewTxnLogEnabled) {
			if (nonTxnLogStateExists){
				String msg = "TxnLog is enabled but non txn log files exist. TxnConversion required";
				logger.log(Logger.INFO, msg);
				
				setTxnConversionRequired(true);
			}
		} else {
			if (txnLogStateExists){
				String msg = "TxnLog is not enabled but txn log files exist. TxnConversion required";
				logger.log(Logger.INFO, msg);
				
				setTxnConversionRequired(true);
			}
		}

	}

	public static boolean isTxnConversionRequired() {
		return TxnConversionUtil.txnConversionRequired;
	}

	public static void setTxnConversionRequired(boolean txnConversionRequired) {
		TxnConversionUtil.txnConversionRequired = txnConversionRequired;
	}

	static void resolveIncompleteConversionToTxnLog(File rootDir,
			File convertingToTxnFormatFile) throws BrokerException {

		String msg = "Found incomplete conversion of transactions to txnLog format on startup. Will redo or complete conversion";
		logger.log(Logger.WARNING, msg);

		// stages of conversion are:
		// 1) create convertingToTxnFormatFile
		// 2) create txnlog file
		// 3) copy data to txn log file
		// 4) delete txn and txnack files
		// 5) delete convertingToTxnFormatFile

		// lets work backwards and see how far we got in conversion

		// if both txn and txnack files are present,
		// simply redo conversion
		if (TidList.txFileExists(rootDir) && TidList.txAckFileExists(rootDir)) {
			// delete any state in txnLog format as this will be
			// re-converted
			TransactionLogManager.deleteAllFileState(rootDir);
			deleteConversionFile(rootDir, convertingToTxnLogMode);
			return;
		}

		// if either txn or txnack files are deleted then we should be
		// at stage 4, i.e we have completed conversion. Simply tidy up.
		if (!TidList.txFileExists(rootDir) || !TidList.txAckFileExists(rootDir)) {
			// sanity check
			TransactionLogManager.assertAllFilesExist(rootDir);

			TidList.deleteAllFiles(rootDir);
			TxnAckList.deleteAllFiles(rootDir);
			deleteConversionFile(rootDir, convertingToTxnLogMode);
			return;
		}

		// source txn files exist, so let us just reconvert them
		// delete any partially converted txn log files
		TransactionLogManager.deleteAllFileState(rootDir);
		deleteConversionFile(rootDir, convertingToTxnLogMode);
		return;

	}

	static void resolveIncompleteConversionFromTxnLog(File rootDir,
			File convertingFromTxnFormatFile) throws BrokerException {
		String msg = "Found incomplete conversion of transactions from txnLog format on startup. Will redo or complete conversion";
		logger.log(Logger.WARNING, msg);

		// stages of conversion are:
		// 1) create convertingFromTxnFormatFile
		// 2) create txn and txn ack files
		// 3) copy data to txn and txn ack file
		// 4) delete txnlong and prepared txn files
		// 5) delete convertingFromTxnFormatFile

		// lets work backwards and see how far we got in conversion

		// if both source files (txnlog and prepared txn store) are still
		// present (i.e not deleted yet),
		// simply redo conversion

		if (TransactionLogManager.transactionLogExists(rootDir)
				&& TransactionLogManager.incompleteTxnStoreExists(rootDir)) {
			// delete any state in txn and txnack format as these will be
			// re-converted
			TidList.deleteAllFiles(rootDir);
			TxnAckList.deleteAllFiles(rootDir);
			deleteConversionFile(rootDir, convertingFromTxnLogMode);
			return;
		}

		// if either txnlog or prepared txn store files are deleted then we
		// should be
		// at stage 4, i.e we have completed conversion. Simply tidy up.
		if (!TransactionLogManager.transactionLogExists(rootDir)
				|| !TransactionLogManager.incompleteTxnStoreExists(rootDir)) {

			// sanity check. txn and txnack files should both exist
			TidList.assertAllFilesExists(rootDir);

			TransactionLogManager.deleteAllFileState(rootDir);
			deleteConversionFile(rootDir, convertingFromTxnLogMode);
			return;
		}

		// source txnlog files exist, so let us just delete any converted data
		// and reconvert.
		// delete any partially converted txn or txnack files
		TidList.deleteAllFiles(rootDir);
		TxnAckList.deleteAllFiles(rootDir);
		deleteConversionFile(rootDir, convertingFromTxnLogMode);
		return;

	}

	public static void convertTxnFormats(FileStore fileStore, File rootDir,
			TransactionList transactionList) throws BrokerException,
			IOException {

		if (Globals.isNewTxnLogEnabled()) {

			// check if already converting

			// we are converting to txn log format.
			// load txns and resolve open transactions
			if (Store.getDEBUG()) {
				String msg = "ConvertingTxnData :loading transactions from txn and txnack";
				logger.log(Logger.DEBUG, msg);
			}
			
			try {
				convertingToTxnLog = true;
				createConversionFile(rootDir, convertingToTxnLogMode);
				transactionList.loadTransactions();
				ToTxnLogConverter.convertToTxnLogFormat(transactionList,
						fileStore);

				fileStore.closeTidList();
				TidList.deleteAllFiles(rootDir);
				TxnAckList.deleteAllFiles(rootDir);
				deleteConversionFile(rootDir, convertingToTxnLogMode);
			} finally {
				convertingToTxnLog = false;

			}

		} else {
			createConversionFile(rootDir, convertingFromTxnLogMode);
			FromTxnLogConverter txnCoverter = new FromTxnLogConverter(fileStore);
			txnCoverter.convertFromTxnLogFormat();
			transactionList.loadTransactions();
			fileStore.closeTxnLogManager();
			TransactionLogManager.deleteAllFileState(rootDir);
			deleteConversionFile(rootDir, convertingFromTxnLogMode);

		}

	}

	public static void createConversionFile(File rootDir, String fileName)
			throws BrokerException {
		File file = new File(rootDir, fileName);
		try {
			file.createNewFile();
		} catch (IOException e) {
			throw new BrokerException(
					"can not create transaction conversion file " + file, e);
		}

	}

	public static void deleteConversionFile(File rootDir, String fileName)
			throws BrokerException {
		File file = new File(rootDir, fileName);

		if (file.exists()) {
			boolean deleted = file.delete();

			if (!deleted) {
				throw new BrokerException(
						"can not delete transaction conversion file " + file);
			}
		}
	}

}
