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
 * @(#)FileLogRecordIterator.java	1.2 06/29/07
 */ 

package com.sun.messaging.jmq.io.txnlog.file;


import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import com.sun.messaging.jmq.io.txnlog.*;

/**
 * This class reads MQ transaction log file from the last check point to the end
 * of the file. 
 */

public class FileLogRecordIterator implements Iterator {
	
	//private boolean hasNext = false;
	private FileTransactionLogWriter lwriter = null;
	private RandomAccessFile raf = null;
	private long cpPosition = -1;
        
    private long cpSequence = -1;
	
	private FileTransactionLogRecord entry = null;
	
	private FileTransactionLogRecord[] compoundEntries = null;

	private int compoundEntryIndex =0;
	
	private boolean eof = false;
	
	private long ct = 0;
    
    private long entrySequence = 0;
	
	private boolean debug = false;
	
	public FileLogRecordIterator (FileTransactionLogWriter writer) throws IOException {
		
        this.debug = Boolean.getBoolean (FileTransactionLogWriter.TXNLOG_DEBUG_PROP_NAME);
        
        this.lwriter = writer;
		this.raf = lwriter.getRAF();
		this.cpPosition = lwriter.getCPPosition();
		this.cpSequence = lwriter.getCPSequence();
                
		raf.seek(cpPosition);
		
		readNextRecord();
	}
	
	/**
	 * 
	 */
	public synchronized boolean hasNext() {

		/**
		 * We always read one record ahead.
		 */
		if (entry != null) {
			return true;
		} else {
			return false;
		}

	}
	
	public synchronized Object next() {
		
		if (eof) {
			throw new NoSuchElementException ();
		}
		
		if ( entry != null ) {
			
			//save the current entry to be returned.
			TransactionLogRecord lrec = entry;
			
			if(compoundEntries!=null)
			{
				// entry is a member of a compound log entry
				
				if(compoundEntryIndex < (compoundEntries.length-1) )
				{				
					// there are more compound entries to go
					compoundEntryIndex++;
					entry = compoundEntries[compoundEntryIndex];
					if(debug)
						log("setting next entry to a compound sub record");
					
					// no need to read next record
					return lrec;
				}
				else
				{
					// this is the last compound entry
					if(debug)
						log("This is the last compound sub record");
					compoundEntries = null;
					compoundEntryIndex = 0;
				}				
				
				
			}
			
			//set to null so that the next read can assign to it if exists.
			entry = null;
			
			//read the next record.
			readNextRecord();
			
			//return the entry
			return lrec;
		} 
		
		throw new NoSuchElementException ();
	}
	
	public void remove() {
		throw new UnsupportedOperationException ("Unsupported Operation.");
	}
	
	private synchronized void readNextRecord() {

		if(debug)
			log("readNextRecord");
		if (eof) {
			return;
		}

		try {

			byte[] bytes = new byte[FileTransactionLogWriter.RECORD_HEADER_SIZE];

			//read record header
			int bread = raf.read(bytes);

			//if bytes read is less than header size, we reached eof
			if (bread < FileTransactionLogWriter.RECORD_HEADER_SIZE) {

				log("Reached end of file., records read: " + ct);
				eof = true;

				return;
			}

			//read header fields
			ByteBuffer buf = ByteBuffer.wrap(bytes);

			// 1. magic number
			int magic = buf.getInt();

			// 2. record type
			int rtype = buf.getInt();

			// 3. body size
			int bsize = buf.getInt();

			// 4. timestamp
			long timestamp = buf.getLong();

			//check magic number
			if (magic != FileTransactionLogWriter.RECORD_MAGIC_NUMBER) {
                            
				log("Reached end of file. No Magic number., records read: " +  ct);
				
				eof = true;
				
				return;
			}
			
			//5. entry seq number
			long seq = buf.getLong();
			
			//6. cp seq number
			long cpseq = buf.getLong();

			// read record body
			byte[] body = new byte[bsize];
			int rsize = raf.read(body);

			if (rsize < bsize) {
				
				log("Reached end of file. Body read reached EOF. records read: " +  ct);
                                
				eof = true;
				
				return;
			}

			// 7. check sum
			long valueSet = buf.getLong();
			long calculated = lwriter.calculateCheckSum(body);

			if (valueSet != calculated) {
                
                log ("Reached end of file.  Check sum not validate., records read: " +  ct);
                
                eof = true;
                return;
            }
            
            if (cpseq != this.cpSequence) {
                
                log ("Reached end of check point.  records read: " + ct);
                
                eof = true;
                
                return;
            }
            
            //validate entry sequence
            if (seq != this.entrySequence) {
                log ("Entry sequence is not valid. Expected: " + entrySequence + ", but read: " + seq + ", total records read: " + ct);
                
                eof = true;
                
                return;
            } 
            
            entrySequence ++;
            
            //construct next available log record object
            entry = new FileTransactionLogRecord (timestamp, rtype, seq);
            
            entry.setCheckPointSequence (cpseq);
            
            entry.setBody (body);
            
            if(entry.getType()== TransactionLogType.COMPOUND_TRANSACTION)
            {
            	processCompoundTransactionLogRecord();
            }
            
            ct ++;
			
		} catch (Exception e) {
			eof = true;
		}

		return;
	}
	
	private void processCompoundTransactionLogRecord()
	{
		TransactionLogRecord compoundRecord =  entry;
		byte[] compoundBody = compoundRecord.getBody();
		
		//read header fields
		ByteBuffer buf = ByteBuffer.wrap(compoundBody);
		
		int numEntries  = buf.getInt();
		if(debug)
			log("processCompoundTransactionLogRecord numEntries="+numEntries);
		
		compoundEntries = new FileTransactionLogRecord[numEntries];
		compoundEntryIndex = 0;
		for(int i=0;i<numEntries;i++)
		{
			
			FileTransactionLogRecord subEntry = new FileTransactionLogRecord (entry.getTimestamp(), entry.getType(), entry.getSequence());
			
			subEntry.setCheckPointSequence(entry.getCheckPointSequence());
			
			compoundEntries[i]=subEntry;
			
			// 2. record type
			int rtype = buf.getInt();
			subEntry.setType(rtype);

			// 3. body size
			int bsize = buf.getInt();
			
			byte[] subBody = new byte[bsize];
			buf.get(subBody);			
			
			subEntry.setBody(subBody);
		}
		
		//set entry to the first record
		entry = compoundEntries[0];
		
	}
	
	private void log (String msg) {
    	if ( debug ) {
    		System.out.println(new Date() + ": " + msg);
    	}
    }

	/**
	 * @param args
	 */
	public static void main (String[] args) throws Exception {
        
        String fname = "MQTxn.log";
        
        if (args.length > 0) {
            fname = args[0];
        }
        
        int nrec = 0;
        
        TransactionLogWriter lwriter = new FileTransactionLogWriter (fname);
        
        if (lwriter.playBackRequired ()) {
            
            Iterator it = lwriter.iterator ();
            nrec = 0;
            while (it.hasNext ()) {
                Object obj = it.next ();
                System.out.println (obj.toString ());
                nrec ++;
            }
        } else {
            System.out.println ("*** No playback is required.");
        }
        
        System.out.println ("*** Read Txn log file: " + fname + ", nrec: " + nrec);
    }

}
