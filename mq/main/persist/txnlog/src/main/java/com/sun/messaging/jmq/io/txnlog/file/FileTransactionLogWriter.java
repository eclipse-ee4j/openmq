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
 * @(#)FileTransactionLogWriter.java	1.3 06/29/07
 */ 

package com.sun.messaging.jmq.io.txnlog.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

import com.sun.messaging.jmq.util.MQThread;
import com.sun.messaging.jmq.io.txnlog.CheckPointListener;
import com.sun.messaging.jmq.io.txnlog.TransactionLogRecord;
import com.sun.messaging.jmq.io.txnlog.TransactionLogType;
import com.sun.messaging.jmq.io.txnlog.TransactionLogWriter;

/**
 * This class is used to write FileTransactionLogRecord to the transaction log file.
 * <p>
 * Each transaction is written as a transaction log record.  Each record written is 
 * synced to the disk.  Broker does not need to sync its message stores until
 * a check point request (CheckPointListener is called).
 * <p>
 * <XXX: chiaming (06/30/2006) add comments to describe file/header layout here.
 */
public class FileTransactionLogWriter implements TransactionLogWriter, Runnable {
    
    //file header constants - version
    public static final short FILE_VERSION = 1;
    
    /**
     * File header size - 48 bytes.
     */
    public static final int FILE_HEADER_SIZE = 48;
    //file header magic number - constant
    public static final int FILE_MAGIC_NUMBER = 0x5555AAAA;
    //file header seserve field
    public static final short FILE_RESERVED_SHORT = 0;
    
    //file header status position.  If file header is changed,
    //this and check point position values MUST be updated.
    public static final int FILE_STATUS_POSITION = 6;
    
    //File check point position.
    public static final int FILE_CHECK_POINT_POSITION = 8;
    
    //File check sum position
    public static final int FILE_CHECK_SUM_POSITION = 32;
   
    /**
     * record header size - 48 bytes
     */
    public static final int RECORD_HEADER_SIZE = 48;
    
    public static final int RECORD_MAGIC_NUMBER = 0xAAAA5555;
    public static final int RECORD_TYPE = -1;
    public static final long RECORD_TIME_STAMP = 0;
    public static final int RECORD_BODY_SIZE = 0;
    public static final long RECORD_CHECK_SUM = 0;
    public static final int RECORD_HEADER_RESERVE = 0;
    
    //transaction log file created for writing -- init
    public static final short FILE_STATUS_CREATE_NORMAL = 0;
    
    //normal/clean shutdown status - close() called
    public static final short FILE_STATUS_SHUTDOWN_NORMAL = 1;
    
    //file to be renamed to .bak
    public static final short FILE_STATUS_BACKUP = 2;
    
    //checkPoint() called
    public static final short FILE_STATUS_CHK_POINT_UPDATED = 3;
    
    //reset() called
    public static final short FILE_STATUS_RESET = 4;
    
    public static final long DEFAULT_MAX_SIZE_KB = 10 * 1024L; //10 M
    
    public static final String TXNLOG_DEBUG_PROP_NAME = "imq.txnlog.debug";
    
    public static final String TXNLOG_BACKUP_PROP_NAME = "imq.txnlog.backup";
       
    private long maxSize = DEFAULT_MAX_SIZE_KB * 1024L; //10 M
    
    //when file pointer is > (maxSize - checkPointOffset),
    //this logwriter will reset itself and start from the beginning
    private long cpOffset = 500 * 1024L;
    private long cpSize = maxSize - cpOffset;
    
    private CheckPointListener callback = null;
    
    private boolean isListenerCalled = false;
    
    //The txn file.
    private RandomAccessFile raf = null;
    
    private File file = null;
   
    //read/write/sync mode.
    public static final String RWD_MODE = "rwd";

    /**
     * "rws" file mode -- for testing use only
     */
    private static final String RWS_MODE = "rws";
    
    /**
     * read-write mode -- call FileChannel.force() to sync every transaction.
     **/
    public static final String RW_MODE = "rw";
   
    private String fileMode = RWD_MODE;
    
    /**
     * This is used only if "useFileChannelSync" flag is true.
     */
    private FileChannel fchannel = null;
    
    /**
     * This is set to true only when RW_MODE is used.
     */
    private boolean useFileChannelSync = false;
    
    //check point position, 0 means this log is synced with message store.
    private long checkPointPosition = FILE_HEADER_SIZE;
    
    private boolean debug = false;
    
    //sync object for operations to txn file.
    private Object txnLogSyncObj = new Object();
    
    //to calculate checksum for each record written to txn log.
    private Checksum checksumEngine = new Adler32();
    
    /**
     * flag to indicate if playback of this file is required.
     */
    private boolean playBackRequired = false;
    
    /**
     * flag to indicate if file id corrupted.  This flag is set to true if
     * <code>FileTransactionLogWriter#readFileHeader</code>detects
     * that the header cannot be validated.  Broker MUST call
     * <code>FileTransactionLogWriter#reset</code> to reset the log file.
     */
    private boolean isFileCorrupted = false;
    
    //check point sequence number. This number is put in the file header
    //and each log record. The value in the file header indicates the last
    //check point sequence number. Each log record from the last check point
    //to the next one contains the same check point sequence number.
    //the number is increased by one for each check point synced.
    private long checkpointSequence = 0;
    
    //This number is increased by 1 for each record added
    //to the log file. This number is reset to 0 when a check
    //point is called.
    private long entrySequence = 0;
    
    //last entry written to the log file
    private TransactionLogRecord lastEntry = null;
    
    //back up txn log file name extension.
    public static final String TXNLOG_BACKUP_EXT = ".1";
    
    //flag to determine if we want to do txnlog file backup. default to false
    //some systems do not support the OP.
    //Set imq.txnlog.backup to true to turn it on
    private boolean doFileBackup = false;
    
    private boolean doAsyncWrites = false;
    
    private boolean closed;
    
    private MQThread asyncWriteThread = null;
    
    private boolean synch = true;
    
    // the following three variables are used to debug the average number of records in a compound record 
    private int sampleNum;
   
    private int sampleCount=1000;
    
    private int[] numrecordsArray = new int[sampleCount];
    
    // a version number passed in by the application that will be stored in the header when the file is first created
    private long currentAppCookie = 0;

    // the version number read from the file when the file already exists.
    private long existingAppCookie = 0;
    
    // a collection holding log records due for processing 
    private List<TransactionLogRecord> transactionLogRecordList = new LinkedList<TransactionLogRecord>();
    
    // a mutex controlling access to the transactionLogRecordList
    private Object recordListMutex = new Object();
    
    public FileTransactionLogWriter(File parent, String fileName, long size) throws IOException {
        init(parent, fileName, size);
    }
    
    public FileTransactionLogWriter(String fileName) throws IOException {
        init(null, fileName, DEFAULT_MAX_SIZE_KB*1024L);
    }
    
    /**
     * Constructor.
     * 
     */
    public FileTransactionLogWriter(File parent, String fileName, 
        long size, String mode, long applicationCookie) 
        throws IOException {
        this(parent,fileName,size,mode,true, false, applicationCookie);   
    }

    /**
     * Constructor.
     * 
     */
    public FileTransactionLogWriter(File parent, String fileName, long size,
        String mode, boolean sync, boolean groupCommit, long applicationCookie)
        throws IOException {

		if (RW_MODE.equals(mode)) {
			this.useFileChannelSync = true;
			fileMode = RW_MODE;
		} else if (RWS_MODE.equals(mode)) {
			fileMode = RWS_MODE;
		} else if (RWD_MODE.equals(mode) == false) {
			throw new java.lang.IllegalArgumentException(
					"This file mode is not supported: " + mode);
		}
		this.currentAppCookie = applicationCookie;
		this.synch = sync;
		this.doAsyncWrites = groupCommit;
		init(parent, fileName, size);
	}
    
    
    private void init(File parent, String child, long size) throws IOException {
        
        this.debug = Boolean.getBoolean (TXNLOG_DEBUG_PROP_NAME);
    
        this.doFileBackup = Boolean.getBoolean (TXNLOG_BACKUP_PROP_NAME);
        
        file = new File(parent, child);
        
        setMaximumSize(size);
        
        if (raf != null) {
            raf.close();
        }
      
        if(doAsyncWrites)
        {
        	log ("starting asyncwrite");
        	asyncWriteThread = new MQThread(this, child+ "AsyncWrite");
        	asyncWriteThread.setPriority(Thread.NORM_PRIORITY-1);
        	asyncWriteThread.start();
        }
      
        /**
         * The default file mode is RWD_MODE ("rwd").   If this class is instantiated with a file mode,
         * the file mode is set to the specified mode ("rw", "rws", "rwd" only).
         */
        log ("Using file mode: " + fileMode);
        raf = new RandomAccessFile(file, fileMode);
        
        /**
         * If "rw" mode is specified, useFileChannelSync flag is set to true. 
         */
        if ( this.useFileChannelSync) {
            log("File Channel Sync flag is on ...");
            fchannel = raf.getChannel();
        } 
       
        if ( raf.length() > 0 ) {
            
            if (debug) {
                log("file exists: " + file.getAbsolutePath() +  ", file size: " + raf.length());
            }
            
            readFileHeader();
            
            if (this.playBackRequired == false)  {
                writeFileHeader(FILE_STATUS_CHK_POINT_UPDATED, FILE_HEADER_SIZE);
            }
            
        } else {
            
            //do create header for now.
        	existingAppCookie = currentAppCookie;
            initNewFile();
        }
    }
    
    private void initNewFile() throws IOException {
        
        //always rewind.
        raf.seek(0);
        
        raf.setLength((int)maxSize);
        
        //allocate size for perf. reason.
        //raf.write( new byte [(int)maxSize] );
        doWrite (new byte [(int)maxSize]);
        
        //init check point sequence
        this.checkpointSequence = 0;
        //init entry sequence
        this.entrySequence = 0;
        
        //write file header.
        //fileStatus=CREATE_NORMAL, cpPosition=0
        writeFileHeader(FILE_STATUS_CREATE_NORMAL, FILE_HEADER_SIZE);
        
        if (debug) {
            log ("file created: " + file.getAbsolutePath () + ", size: " + raf.length ());
        }
    }
    
    /**
     * Read and validate file header.
     * @throws IOException
     */
    private void readFileHeader() throws IOException {
        
        raf.seek(0);
        
        byte[] bytes = new byte[FILE_HEADER_SIZE];
        
        int size = raf.read(bytes);
        
        //1. check header size
        if ( size != FILE_HEADER_SIZE ) {
            //set flag
            this.isFileCorrupted = true;
            
            throw new FileCorruptedException("File Header size mismatch. Expected:  " +
                FILE_HEADER_SIZE + ", read: " + size);
        }
        
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        
        int magic = buf.getInt();
        short fversion = buf.getShort();
        short shutdownState = buf.getShort();
        long cpPosition = buf.getLong();
        long timestamp = buf.getLong();
        
        long cpseq = buf.getLong(); //cp seq
        
        long chksum = buf.getLong();
        
        long fileCookie = buf.getLong();
        
        long calculated = calculateCheckSum(bytes, 0, FILE_CHECK_SUM_POSITION);
        
        if (debug) {
            log ("read file header, magic=" + magic + ", fversion=" + fversion + ", status="+shutdownState+ ", cpPosition="+cpPosition + ", timestamp=" + timestamp
                +  ", cpSequence="+cpseq + ", chksum="+chksum);
        }
        
        //2. validate check sum
        if ( chksum != calculated ) {
            
            //set flag
            this.isFileCorrupted = true;
            
            throw new FileCorruptedException("File Header checksum mismatch. Expected: " +
                chksum + ", calculated: " + calculated);
        }
        
        //3. validate magic number/version
        if ((magic != FILE_MAGIC_NUMBER) || fversion != FILE_VERSION) {
            
            //set flag
            this.isFileCorrupted = true;
            
            throw new FileCorruptedException("File Magic number/version mismatch: " + magic +":" + fversion);
        }
        
        //4. check if playback is required.
        if ( shutdownState != FILE_STATUS_SHUTDOWN_NORMAL) {
            //we need to playback.  set flag to true
            this.playBackRequired = true;
            
        }
        if (debug) {
            log ("playbackRequired="+playBackRequired);
        }
        
        
        //set chk position position.
        this.checkPointPosition = cpPosition;
        //set check point sequence
        this.checkpointSequence = cpseq;
        
        //move to the end of file header.
        raf.seek(FILE_HEADER_SIZE);
        //fchannel.position(FILE_HEADER_SIZE);
        
        
        // check application cookie matches
        this.existingAppCookie = fileCookie;
        if(this.existingAppCookie != currentAppCookie)
        {
          log("application cookies do not match! Existing file has version="+this.existingAppCookie +
        		  " Current version of software=" + this.currentAppCookie);	
        }
        
    }
    
    /**
     * Write file header into ByteBuffer.
     *
     * This file has a 48 byte fixed header as specified below.
     */
    private void
        writeFileHeader(short fileStatus, long cpPosition) throws IOException {
        
        //increase check point sequence. this value is used so that all entries in each
        //check point contains the same (and unique) checkPointSequence number.
        //
        if (checkpointSequence > Long.MAX_VALUE -1) {
            //reset
            checkpointSequence = 0;
        } else {
            checkpointSequence ++;
        }
        
        //reset entry seq number when check point seq is increased
        entrySequence = 0;
        
        //rewind.
        raf.seek(0);
        
        byte[] bytes = new byte[FILE_HEADER_SIZE];
        
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        
        //1. Magic number
        buf.putInt(FILE_MAGIC_NUMBER); //0-3
        //2. version
        buf.putShort(FILE_VERSION);    //4-5
        //3. status.
        buf.putShort(fileStatus);//6-7
        //4. check point position
        buf.putLong(cpPosition);//8-15
        //5. timestamp
        long timestamp = System.currentTimeMillis();
        buf.putLong(timestamp); //16-23
        //6. check point sequence number
        buf.putLong(checkpointSequence); //24-31
        
        long chksum = this.calculateCheckSum(bytes, 0, FILE_CHECK_SUM_POSITION);
        
        //6. check sum
        buf.putLong(chksum);             //32-39
        //7. reserve
        buf.putLong(existingAppCookie);                 //40-47
        
        //raf.write(bytes);
        doWrite (bytes);
        
        if (debug) {
            log ("write file header. magic=" + FILE_MAGIC_NUMBER +
                ", fversion=" + FILE_VERSION + ", status="+
                fileStatus+ ", cpPosition="+cpPosition +
                ", timestamp=" + timestamp + ", cpSequence="+checkpointSequence + ", chksum="+chksum);
        }
    }
    
    /**
     * sets the point where a checkpoint automatically
     * occurs
     * XXX: should we remove this?
     */
    public void setCheckpointSize(long bytes) {
        this.cpSize = bytes;
    }
    
    
    /**
     * sets the maximum size of all State logs. When the maximum
     * is hit, the log is checkpointed and renamed as a back up file.
     */
    public void setMaximumSize(long bytes) {
        this.maxSize = bytes;
        this.cpSize = maxSize - this.cpOffset;
        if (this.cpSize < (FILE_HEADER_SIZE +RECORD_HEADER_SIZE)) {
            this.cpSize = FILE_HEADER_SIZE +RECORD_HEADER_SIZE;
        }
    }
    
    /**
     * Set check point offset.  When the file pointer position reached
     * to (maxSize-offset), a check point is triggerred.
     */
    public void setCheckPointOffset(long offset) {
        this.cpOffset = offset;
        this.cpSize = maxSize - offset;
        if (this.cpSize < (FILE_HEADER_SIZE +RECORD_HEADER_SIZE)) {
            this.cpSize = FILE_HEADER_SIZE +RECORD_HEADER_SIZE;
        }
    }
    
    /**
     * registeres the checkpoint CB. The callback is
     * triggered when checkpoint is called whether because
     * the system hit a size limit or the application explicitly
     * called checkpoint();
     * @see checkpoint();
     */
    public void setCheckPointListener(CheckPointListener cb) {
        this.callback = cb;
    }
    
    
    public void writeAsyncRecord(TransactionLogRecord entry) throws IOException {

    	// add record to list
		synchronized (recordListMutex) {
	//		 log("adding record");
			transactionLogRecordList.add(entry);
	//		 log("notifying recordListMutex");
			 recordListMutex.notifyAll();
		}
		
		// now wait for record to be processed by logger thread
		synchronized (entry) {
			try {
				while(!entry.isWritten())
				{
		//			 log("waiting for record to be written");
  				    entry.wait();
  		//		    log("woken up from waiting for record to be written");
				}
		//		  log("record now written");
			} catch (InterruptedException e) {
			}
		}

	}
    
    private void processTransactionLogRecordList() {
		TransactionLogRecord[] records = null;
		synchronized (recordListMutex) {
			while (transactionLogRecordList.size() == 0 && !closed) {
				try {
				//	log("waiting for transactionLogRecordList to be  >0 ");
					recordListMutex.wait(1000);
				//	log("waking up from recordListMutex.wait()");
				} catch (InterruptedException e) {
				}
			}
			records = new TransactionLogRecord[transactionLogRecordList.size()];
			records = transactionLogRecordList.toArray(records);
			transactionLogRecordList.clear();
		}
	//	log("processing " + records.length + " records");
		

		// there should be something in the list now.

		
		if (debug) {
			numrecordsArray[sampleNum] = records.length;
			sampleNum++;
			int totalCount = 0;
			if (sampleNum == sampleCount) {
				sampleNum = 0;
				for (int i = 0; i < sampleCount; i++) {
					totalCount += numrecordsArray[i];
					System.out.print(numrecordsArray[i] + ",");
				}
				float averageCount = ((float) totalCount) / sampleCount;
				log(" average records in compound txn record = " + averageCount);

			}
		}
		
		if (records.length == 1) {
	//		log("transactionLogRecordList ==1 ");
			// only a single record so just save it normally
			TransactionLogRecord entry = records[0];

			try {
				writeRecord(entry);
			} catch (IOException e) {
				entry.setException(e);

			}

			// wake up waiting thread
	//		log("synchronized (entry) ");
			synchronized (entry) {
	//			log("notifying entry ");
				entry.setWritten(true);
				entry.notifyAll();
			}

			return;
		}

		try {
			writeCompoundRecord(records);
		} catch (IOException ioe) {
			for (int i = 0; i < records.length; i++) {
				TransactionLogRecord e = records[i];
				e.setException(ioe);
			}
		}

		for (int i = 0; i < records.length; i++) {
			TransactionLogRecord e = records[i];
	//		log("synchronized (entry) ");
			synchronized (e) {
	//			log("notifying entry ");
				e.setWritten(true);
				e.notifyAll();
			}
		}

	}
    
    
    public void run() {
    	
    	log("run called ");
		while (!closed) {
			try {
				processTransactionLogRecordList();
			} catch (Exception e) {
			}
		}
		log("run ending ");
	}
    
    
    void writeCompoundRecord(TransactionLogRecord[] records) throws IOException {
		synchronized (txnLogSyncObj) {
			if(closed)
			{
				// don't write record if txnLog has been closed
				return;
			}
			
			int numRecords = records.length;
			// log("writeCompoundRecord list size = "+records.length);

			TransactionLogRecord entry = new FileTransactionLogRecord();
			entry.setType(TransactionLogType.COMPOUND_TRANSACTION);

			// get bytes

			// calculate compoundBody size;
			int compoundBodySize = 4;
			for (int i = 0; i < numRecords; i++) {
				compoundBodySize += 8;
				compoundBodySize += records[i].getBody().length;
			}

			byte[] compoundBody = new byte[compoundBodySize];
			ByteBuffer subBuf = ByteBuffer.wrap(compoundBody);

			subBuf.putInt(numRecords);
			for (int i = 0; i < numRecords; i++) {
				subBuf.putInt(records[i].getType());
				subBuf.putInt(records[i].getBody().length);
				subBuf.put(records[i].getBody());
			}

			entry.setBody(compoundBody);

			// write entry, we need to sync from this point
			// First come first serve
			entry.setCheckPointSequence(checkpointSequence);

			// set timestamp
			entry.setTimestamp(System.currentTimeMillis());

			// set entry sequence. increase 1 after addition.
			entry.setSequence(entrySequence++);

			// 1. calculate record size
			int size = RECORD_HEADER_SIZE + entry.getBody().length;

			// 2. allocate byte buffer
			byte[] bytes = new byte[size];
			ByteBuffer buf = ByteBuffer.wrap(bytes);

			// 3. write record header
			writeRecordHeader(buf, entry);

			// 4. write body
			buf.put(entry.getBody());

			// write (and sync) bytes to disk
			// raf.write(bytes);
			doWrite(bytes);

			// check if we need to notify CP listener.
			if (raf.getFilePointer() > cpSize) {

				// we only call the listener once
				if (this.isListenerCalled == false) {

					if (debug) {
						log("calling check point listener, fpointer: "
								+ raf.getFilePointer());
					}

					callback.checkpoint();

					// set flag to true so that we only call the listener once.
					this.isListenerCalled = true;
				}
			}

			// last record added to the log file
			this.lastEntry = entry;
			// log("wroteCompoundRecord list ");
		}
	}
    
    
 /*
	 * public void write(TransactionLogRecord entry) throws IOException {
	 * 
	 * synchronized (txnLogSyncObj) {
	 * 
	 * if (isFileCorrupted) { throw new IllegalStateException("File is
	 * corrupted. You must reset the log file to continue."); }
	 * 
	 * if (playBackRequired) { throw new IllegalStateException("File not synced.
	 * You must call Iterator to play back log file."); }
	 * 
	 * if (callback == null) { throw new IllegalStateException("Check point
	 * listener not set. You must set a CheckPointListener before writing
	 * TransactionLogRecords."); }
	 *  // write entry, we need to sync from this point // First come first
	 * serve entry.setCheckPointSequence(checkpointSequence);
	 * 
	 * //set timestamp entry.setTimestamp (System.currentTimeMillis ());
	 * 
	 * //set entry sequence. increase 1 after addition. entry.setSequence
	 * (entrySequence++);
	 *  // 1. calculate record size int size = RECORD_HEADER_SIZE +
	 * entry.getBody().length;
	 *  // 2. allocate byte buffer byte[] bytes = new byte[size]; ByteBuffer buf =
	 * ByteBuffer.wrap(bytes);
	 *  // 3. write record header writeRecordHeader(buf, entry);
	 *  // 4. write body buf.put(entry.getBody());
	 *  // write (and sync) bytes to disk //raf.write(bytes); doWrite (bytes);
	 * 
	 * //check if we need to notify CP listener. if (raf.getFilePointer() >
	 * cpSize) {
	 * 
	 * //we only call the listener once if ( this.isListenerCalled == false) {
	 * 
	 * if (debug) { log ("calling check point listener, fpointer: " +
	 * raf.getFilePointer ()); }
	 * 
	 * callback.checkpoint();
	 * 
	 * //set flag to true so that we only call the listener once.
	 * this.isListenerCalled = true; } }
	 * 
	 * //last record added to the log file this.lastEntry = entry; } }
	 * 
	 */    
    public void write(TransactionLogRecord entry) throws IOException {
		if (doAsyncWrites) {
			writeAsyncRecord(entry);
		} else {
			writeRecord(entry);
		}
	}
    
    public void writeRecord(TransactionLogRecord entry) throws IOException {
        
        synchronized (txnLogSyncObj) {
            if(closed)
            {
            	// don't attempt to write record if txnLog has been closed
            	return;
            }
            if (isFileCorrupted) {
                throw new IllegalStateException("File is corrupted. You must reset the log file to continue.");
            }
            
            if (playBackRequired) {
                throw new IllegalStateException("File not synced.  You must call Iterator to play back log file.");
            }
            
            if (callback == null) {
                throw new IllegalStateException("Check point listener not set. You must set a CheckPointListener before writing TransactionLogRecords.");
            }
            
            // write entry, we need to sync from this point
            // First come first serve
            entry.setCheckPointSequence(checkpointSequence);
            
            //set timestamp
            entry.setTimestamp (System.currentTimeMillis ());
            
            //set entry sequence. increase 1 after addition.
            entry.setSequence (entrySequence++);
            
            // 1. calculate record size
            int size = RECORD_HEADER_SIZE + entry.getBody().length;
            
            // 2. allocate byte buffer
            byte[] bytes = new byte[size];
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            
            // 3. write record header
            writeRecordHeader(buf, entry);
            
            // 4. write body
            buf.put(entry.getBody());
            
            // write (and sync) bytes to disk
            //raf.write(bytes);
            doWrite (bytes);
            
            //check if we need to notify CP listener.
            if (raf.getFilePointer() > cpSize) {
                
                //we only call the listener once
                if ( this.isListenerCalled == false) {
                    
                    if (debug) {
                        log ("calling check point listener, fpointer: " + raf.getFilePointer ());
                    }
                    
                    callback.checkpoint();
                    
                    //set flag to true so that we only call the listener once.
                    this.isListenerCalled = true;
                }
            }
            
            //last record added to the log file
            this.lastEntry = entry;
        }
    }
    /**
     * header size - 32 bytes.
     * @param buf
     * @param entry
     * XXX: NEED SEQUENCE NUMBER?
     */
    private void writeRecordHeader(ByteBuffer buf, TransactionLogRecord entry) {
        
        //1. record magic number
        buf.putInt(RECORD_MAGIC_NUMBER); //0-3
        
        //2. record type
        buf.putInt(entry.getType());//4-7
        
        //3. body size
        buf.putInt(entry.getBody().length);//8-11
        
        //4. timestamp
        buf.putLong(entry.getTimestamp());//12-19
        
        //5. entry seq
        buf.putLong(entry.getSequence()); //20-28
        
        //6. check point sequence
        buf.putLong(entry.getCheckPointSequence () ); //29-36
        
        //7. calculate and write body check sum
        long value = this.calculateCheckSum(entry.getBody());
        buf.putLong(value); //37-44, check sum
        
        //8. header reserve
        buf.putInt(RECORD_HEADER_RESERVE);//45-48
    }
    
    /**
     * Writes bytes to the file. 
     */
    private void doWrite (byte[] bytes) throws IOException {
        
        raf.write(bytes);
        
        if ( this.useFileChannelSync && synch) {
            //this.fchannel.force(false);
            raf.getFD().sync();
        }
        
    }
    
    long calculateCheckSum(byte[] body) {
        
        long value = calculateCheckSum(body, 0, body.length);
        
        return value;
    }
    
    /**
     * Calculate check sum.  A simple perf showed that average time spent
     * here is about 0.001 (total-checksum-time/total-time) when writing
     * 2000 records to a synced file (hard drive cache off).
     *
     * @param body
     * @param offset
     * @param length
     * @return
     */
    long calculateCheckSum(byte[] body, int offset, int length) {
        
        long value = -1;
        
        synchronized (txnLogSyncObj) {
            checksumEngine.update(body, offset, length);
            value = checksumEngine.getValue();
            checksumEngine.reset();
        }
        
        return value;
    }
    
    /**
     *
     * @return the next check point sequence number.
     */
    public TransactionLogRecord checkpoint() throws IOException {
        
        synchronized (this.txnLogSyncObj) {
            
            long pos = raf.getFilePointer();
            
            if ( pos > cpSize ) {
                //rewind -- update file header only.
                writeFileHeader(FILE_STATUS_CHK_POINT_UPDATED, FILE_HEADER_SIZE);
            } else {
                //update file header only
                writeFileHeader(FILE_STATUS_CHK_POINT_UPDATED, pos);
                raf.seek(pos);
            }
            
            //reset this flag
            this.isListenerCalled = false;
            
            return this.lastEntry;
        }
    }
    
    /**
     * iterates from the last checkpoint
     */
    public Iterator iterator() throws IOException {
        
        Iterator it = new FileLogRecordIterator(this);
        
        return it;
    }
    
    /**
     * Rewind/Refresh the log file.
     *
     * This method should be called if one of the following conditions occurs.
     * <p>
     * 1.  When broker starts up, and detects that playback is required.  The broker should call the
     * iterator() method and playback the transaction entries from the iterator API.  The broker then
     * calls reset to clear the state of the transaction log file.
     *<p>
     * 2.
     */
    public void reset() throws IOException {
        
        log("Reseting txn log file ...");
        
        synchronized (this.txnLogSyncObj) {
            
            if (this.doFileBackup) {
                this.backupLogFile ();
            }
            
            if ( this.isFileCorrupted) {
                this.initNewFile();
            } else {
                writeFileHeader(FILE_STATUS_CHK_POINT_UPDATED, FILE_HEADER_SIZE);
            }
            
            /**
             * reset check point position.
             */
            this.checkPointPosition = FILE_HEADER_SIZE;
            
            this.playBackRequired = false;
            this.isFileCorrupted = false;
            this.isListenerCalled = false;
            
        }
    }
    
    /**
     * The last entry added to the log file.
     *
     * XXX: only works when the txn log is in writing/inserting mode.
     */
    public TransactionLogRecord getLastEntry() {
        synchronized (this.txnLogSyncObj) {
            return this.lastEntry;
        }
    }
    
    public void close(boolean clean) throws IOException {
    	if(clean)
    	{
    		close();
    		return;
    	}
    	
		synchronized (this.txnLogSyncObj) {		
			// close file.
			raf.close();
			closed = true;
		}
	}
    /**
     * 1. set SHUTDOWN_NORMAL.
     * 2. set cpPosition to current file position.
     */
    public void close() throws IOException {
        synchronized (this.txnLogSyncObj) {
            
            //get current file pointer
            //long pos = raf.getFilePointer();
            raf.getFilePointer();
            
            //set file status and cpPosition.
            writeFileHeader(FILE_STATUS_SHUTDOWN_NORMAL, FILE_HEADER_SIZE);
            
            //close file.
            raf.close();
            
            closed = true;            
        }
    }
    
    /**
     * XXX chiaming; Not required to sync the class since we have a cp sequence.
     */
    public TransactionLogRecord newTransactionLogRecord() {
        return new FileTransactionLogRecord();
    }
    
    RandomAccessFile getRAF() {
        return this.raf;
    }
    
    long getCPPosition() {
        return this.checkPointPosition;
    }
    
    public synchronized long getCPSequence() {
        return this.checkpointSequence;
    }
    
    private void backupLogFile() throws IOException {
        
        synchronized (this.txnLogSyncObj) {
            
            /**
             * Get txn log file name
             */
            String txnlogName = file.getCanonicalPath ();
           
            this.deleteBackUpFile (txnlogName);
            
            //rename the current file
            copyFile (txnlogName);
        }
        
    }
    
    /**
     * copy txnlogName contents to txnlogName.1
     *
     */
    private void copyFile (String txnlogName) throws IOException {
        
        String bname = txnlogName + TXNLOG_BACKUP_EXT;
        
        File bfile = new File (bname);
        
        if (bfile.exists ()) {
            throw new IOException ("Cannot backup txnlog.  You must remove the back up file to continue: " + bname);
        }
        
        long savedPosition = raf.getFilePointer ();
        
        if (this.useFileChannelSync == false) {
            fchannel = raf.getChannel ();
        }
        
        fchannel.position (0);
        
        FileChannel fc2 = new FileOutputStream (bfile).getChannel ();
        
        fc2.transferFrom (fchannel, 0, fchannel.size ());
        
        fc2.close ();
       
        if (this.useFileChannelSync == false) {
            
            fchannel.close ();
            raf.close ();
            
            //reopen the file.
             raf = new RandomAccessFile(file, fileMode);
            
       }
        
        raf.seek (savedPosition);
    }
    
    /**
     * delete the old back up file if exists.
     *
     */
    private void deleteBackUpFile (String txnLogName) throws IOException {
        
        //1. remove txnLogName.2 file if exists.
        String backupName2 = txnLogName + TXNLOG_BACKUP_EXT;
        
        File bfile2 = new File (backupName2);
        
        if (bfile2.exists ()) {
            bfile2.delete ();
        }
        
        //2. move txnLogName.1 to txnLogName.2
        
        //String backupName1 = txnLogName +".1";
        //File bfile1 = new File (backupName1);
        //if (bfile1.exists ()) {
        //    bfile1.renameTo (bfile2);
        //}
        
    }
    
    public boolean playBackRequired() {
        return this.playBackRequired;
    }
    
    private void log(String msg) {
        if ( debug ) {
            System.out.println(new Date() + " " + Thread.currentThread() + ": " + msg);
        }
    }

	public long getExistingAppCookie() {
		return existingAppCookie;
	}
    
}

