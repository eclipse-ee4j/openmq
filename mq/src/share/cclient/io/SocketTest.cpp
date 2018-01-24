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
 * @(#)SocketTest.cpp	1.3 06/26/07
 */ 

#include <nspr.h>
#include "../util/UtilityMacros.h"
#include "../util/PRTypesUtils.h"
#include "../basictypes/Monitor.hpp"

#include "TCPSocket.hpp"

//#define DUMP_TCP_STREAM
#if defined(DUMP_TCP_STREAM)
# if defined(WIN32)
 static TCPSocket sock("c:/temp/socket");
# else
 static TCPSocket sock("/home/de134463/temp/socket");
# endif
#else
 static TCPSocket sock;
#endif

static TCPSocket serverSocket;
static iMQError  socketError;
static Monitor   monitor;

#if !defined(WIN32) 
extern "C" {
#endif // !defined(WIN32) 
  static void socketReader(void *arg);
  static void socketWriter(void *arg);
  static void socketServer(void *arg);
  static void socketReaderWriter(void *arg);
#if !defined(WIN32) 
}
#endif // !defined(WIN32) 


static int MAX_SLEEP_MS_WRITER  = 0;
static int MAX_SLEEP_MS_READER  = 0;
static int MAX_ITER             = 0;
static int PRINT_INC            = 0;

static const PRUint16 SERVER_PORT = 22500;
static const PRUint8 WRITE_BYTE = 10;
static const PRUint8 READ_BYTE = 40;
static const PRUint32 TEST_DIV_VALUE = 1000;
static const PRUint32 TEST_MOD_VALUE = 256;

static const int MAX_BYTES           = 1000000;

static PRInt32 g_bytesPerIter        = 0;
static PRInt32 g_numIters            = 0;
static PRInt32 g_printInc            = 0;

static PRInt32 g_timeoutMSWriter     = 0;
static PRInt32 g_timeoutMSReader     = 0;
static PRInt32 g_timeoutMSServer     = 0;

static PRInt32 g_maxSleepMSWriter    = 0;
static PRInt32 g_maxSleepMSReader    = 0;
static PRInt32 g_maxSleepMSServer    = 0;

static float g_writerCloseProb       = 0.0;
static float g_readerCloseProb       = 0.0;
static float g_serverCloseProb       = 0.0;


bool initializeGlobals(FILE * const inputFile)
{
  if (inputFile == NULL) {
    return false;
  }
  PRInt32 itemsRead = 0;
  itemsRead += fscanf(inputFile, "%d,", &g_bytesPerIter);
  itemsRead += fscanf(inputFile, "%d,", &g_numIters);       
  itemsRead += fscanf(inputFile, "%d,", &g_printInc); 
  itemsRead += fscanf(inputFile, "%d,", &g_timeoutMSWriter); 
  itemsRead += fscanf(inputFile, "%d,", &g_timeoutMSReader); 
  itemsRead += fscanf(inputFile, "%d,", &g_timeoutMSServer);
  itemsRead += fscanf(inputFile, "%d,", &g_maxSleepMSWriter);
  itemsRead += fscanf(inputFile, "%d,", &g_maxSleepMSReader);
  itemsRead += fscanf(inputFile, "%d,", &g_maxSleepMSServer); 
  itemsRead += fscanf(inputFile, "%f,", &g_writerCloseProb);  
  itemsRead += fscanf(inputFile, "%f,", &g_readerCloseProb); 
  itemsRead += fscanf(inputFile, "%f\n",  &g_serverCloseProb);


  fprintf(stderr, "%d %d %d %d %d %d %d %d %d %f %f %f\n",
          g_bytesPerIter, g_numIters, g_printInc, g_timeoutMSWriter,
          g_timeoutMSReader, g_timeoutMSServer, g_maxSleepMSWriter,
          g_maxSleepMSReader, g_maxSleepMSServer, g_writerCloseProb,
          g_readerCloseProb, g_serverCloseProb);
  
  return itemsRead == 12;
}


// Define this to use an echo server on the local machine
#define LOCAL_ECHO_SERVER

// Define this to only be an echo server
//#define ONLY_ECHO_SERVER  

// Define this to have separate reader and writer threads
#define SEPARATE_READER_WRITER

static bool g_serverListen;
iMQError
socketTest(FILE * const inputFile)
{

  //
  ASSERT( inputFile != NULL );
  if (inputFile == NULL) {
    return IMQ_NULL_PTR_ARG;
  }

  // Skip the first line of the input file
  char firstLine[1000];
  fgets( firstLine, sizeof(firstLine), inputFile);
  
  g_serverListen = true;

  // Create the echo server
#if defined(LOCAL_ECHO_SERVER) 
  PRThread * serverThread = PR_CreateThread(PR_SYSTEM_THREAD, 
                                            socketServer, 
                                            NULL, 
                                            PR_PRIORITY_NORMAL, 
                                            PR_GLOBAL_THREAD, 
                                            PR_JOINABLE_THREAD, 
                                            0);
  // sleep for a little bit, to let the server start up
  PR_Sleep(PR_MicrosecondsToInterval(1 * 1000 * 1000));

# if defined(ONLY_ECHO_SERVER)
  PR_JoinThread(serverThread);
  return IMQ_SUCCESS;
# endif // defined(ONLY_ECHO_SERVER)
 
#endif // defined(LOCAL_ECHO_SERVER)
  
  for (int iter = 0; initializeGlobals(inputFile); iter++) {
    fprintf(stderr, "On iteration %d\n", iter);

#if defined(LOCAL_ECHO_SERVER)
    RETURN_IF_ERROR( sock.connect("localhost", SERVER_PORT, PR_FALSE, 0xFFFFFFFF) );
#else
    RETURN_IF_ERROR( sock.connect("129.153.130.220", 22228, PR_FALSE, 0xFFFFFFFF) );
    //RETURN_IF_ERROR( sock.connect("129.153.138.193", SERVER_PORT, 0xFFFFFFFF) );
#endif    
    
    socketError = IMQ_SUCCESS;

#if defined(SEPARATE_READER_WRITER)
    PRThread * readerThread = PR_CreateThread(PR_SYSTEM_THREAD, 
                                              socketReader, 
                                              NULL, 
                                              PR_PRIORITY_NORMAL, 
                                              PR_GLOBAL_THREAD, 
                                              PR_JOINABLE_THREAD, 
                                              0);
    
    PRThread * writerThread = PR_CreateThread(PR_SYSTEM_THREAD, 
                                              socketWriter, 
                                              NULL, 
                                              PR_PRIORITY_NORMAL, 
                                              PR_GLOBAL_THREAD, 
                                              PR_JOINABLE_THREAD, 
                                              0);
    
    PR_JoinThread(readerThread);
    PR_JoinThread(writerThread);
#else
    PRThread * readerWriterThread = PR_CreateThread(PR_SYSTEM_THREAD, 
                                                    socketReaderWriter, 
                                                    NULL, 
                                                    PR_PRIORITY_NORMAL, 
                                                    PR_GLOBAL_THREAD, 
                                                    PR_JOINABLE_THREAD, 
                                                    0);
    
    PR_JoinThread(readerWriterThread);
#endif
    
    
    //fprintf(stderr, "Sleeping for .3 secs.  ");
    PR_Sleep(PR_MicrosecondsToInterval(300 * 1000));
    //fprintf(stderr, "Done.  \n");
    
    sock.close();
  }

  fclose(inputFile);

  g_serverListen = false;
  
#if defined(LOCAL_ECHO_SERVER)
  PR_Interrupt(serverThread);
  PR_JoinThread(serverThread);
#endif // defined(LOCAL_ECHO_SERVER)

  return IMQ_SUCCESS;
}



/** 
 * Echo server that listens on port SERVER_PORT.
 */
static void socketServer(void *arg)
{
  UNUSED(arg);
  iMQError errorCode = IMQ_SUCCESS;
  TCPSocket * acceptSocket = NULL;
  PRBool doEcho = PR_TRUE;

  fprintf(stderr, "  Binding to %d.", (PRInt32)SERVER_PORT);
  //ERRCHK( serverSocket.bind("localhost", SERVER_PORT) );
  ERRCHK( serverSocket.bind("129.153.138.193", SERVER_PORT) );
  fprintf(stderr, "  Bound.");
  while (g_serverListen) {
    ERRCHK( serverSocket.listen() != IMQ_SUCCESS );
    ERRCHK( serverSocket.accept(&acceptSocket, 0xFFFFFFFF) != IMQ_SUCCESS );
    
    // Echo until the connection goes away.
    PRInt32 totalBytesRead = 0;
    PRInt32 totalBytesWritten = 0;
    while (doEcho) {
      const PRInt32 MAX_BYTES_TO_READ = 1000000;
      PRInt32 numBytesRead, numBytesWritten;
      static PRUint8 bytesRead[MAX_BYTES_TO_READ];
      if ((errorCode = acceptSocket->read(MAX_BYTES_TO_READ, 
                                          g_timeoutMSServer,
                                          bytesRead,
                                          &numBytesRead)) != IMQ_SUCCESS)
      {
        break;
      }

      if (numBytesRead > 0) {
        fprintf(stderr, "%d             total bytes read\n", totalBytesRead + numBytesRead );
      }
      
      int i;
      for (i = 0; i < numBytesRead; i++) {
        if (bytesRead[i] != (((totalBytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE)) {
          // This LOG_WARNING call can include class information since it's only called during testing
          LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                        "socketReader, bytes[%d]=bytes[%d]=%d, expected %d, bytesRead=%d, numBytesRead=%d",
                        totalBytesRead + i, i, bytesRead[i], ((totalBytesRead + i) % TEST_MOD_VALUE),
                        totalBytesRead, numBytesRead ));
        }
      }
      
      for (i = 0; i < numBytesRead; i++) {
        ASSERT( bytesRead[i] == (((totalBytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE) );
      }

      if ((errorCode = acceptSocket->write(numBytesRead,
                                           bytesRead,
                                           0xFFFFFFFF,
                                           &numBytesWritten)) != IMQ_SUCCESS)
      {
        break;
      }
      
      if (numBytesWritten > 0) {
        fprintf(stderr, "               %d total bytes written\n", totalBytesWritten + numBytesWritten );
      }


      totalBytesRead += numBytesRead;
      totalBytesWritten += numBytesWritten;
      
      if (numBytesRead > 0) {
        // This LOG_WARNING call can include class information since it's only called during testing
        LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                      "socketServer, totalBytesRead=%d, totalBytesWritten=%d",
                      totalBytesRead, totalBytesWritten ));
      }

      if (numBytesRead != numBytesWritten) {
        // This LOG_WARNING call can include class information since it's only called during testing
        LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                      "socketServer, bytesRead=%d, bytesWritten=%d, errorCode=%d",
                      numBytesRead, numBytesWritten, errorCode ));
      }

      ASSERT( numBytesRead == numBytesWritten );
      // randomly close the socket
      double prob = ((double)rand()/(double)RAND_MAX);
      /*
      static int cnt = 0;
      cnt++;
      if (prob < .001) {
        fprintf( stderr, "Prob =   %f,  %d since (close on = %f)\n", prob, cnt, g_serverCloseProb);
        cnt = 0;
        }*/
      if (prob < g_serverCloseProb) {
        fprintf(stderr, "Server closing the socket early\n");
        //acceptSocket->close();
        break;
      }
    }
    fprintf(stderr, "Server closing the socket\n");
    acceptSocket->close();
    DELETE( acceptSocket );
  }
  
Cleanup:
  fprintf(stderr, "\nsocketServer failed because %d.\n", errorCode);
  DELETE( acceptSocket );
  
  serverSocket.close();
  return;
}




static void socketReaderWriter(void *arg)
{
  UNUSED(arg);
  static PRUint8 bytes[MAX_BYTES];
  PRInt32 numBytesRead = 0;
  PRInt32 numBytesWritten = 0;
  PRInt32 error = IMQ_SUCCESS;

  PRInt32 totalBytesToRead = g_bytesPerIter * g_numIters;
  PRInt32 totalBytesToWrite = g_bytesPerIter * g_numIters;

  int bytesRead = 0;
  int bytesWritten = 0;

  while ((bytesRead < totalBytesToRead) && (bytesWritten < totalBytesToWrite))
  {
    //
    // Reader block
    //
    {
      numBytesRead = 0;
      // Initialize bytes
      int i;
      for (i = 0; i < g_bytesPerIter; i++) {
        bytes[i] = READ_BYTE;
      }

      //error = sock.read(g_bytesPerIter, g_timeoutMSReader, bytes, &numBytesRead);
      numBytesRead = PR_Recv(sock.hostSocket, bytes, g_bytesPerIter, 0, 0);
      error = IMQ_SUCCESS;
      if (numBytesRead < 0) {
        numBytesRead = 0;
      }
      if (numBytesRead > 0) {
        fprintf(stderr, "            %d total bytes read\n", bytesRead + numBytesRead );
      }
      
      // Check that the bytes read have the value of the bytes written
      for (i = 0; i < numBytesRead; i++) {
        if (bytes[i] != (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE)) {
          // This LOG_WARNING call can include class information since it's only called during testing
          LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                        "socketReader, bytes[%d]=bytes[%d]=%d, expected %d, bytesRead=%d, numBytesRead=%d",
                        bytesRead + i, i, bytes[i],
                        (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE),
                        bytesRead, numBytesRead ));
        }
      }

      for (i = 0; i < numBytesRead; i++) {
        ASSERT( bytes[i] == (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE ) );
      }

      if (error != IMQ_SUCCESS) {
        break;
      }
      bytesRead += numBytesRead;
    }

    //
    // Writer block
    //
    {
      // Initialize bytes
      for (int i = 0; i < g_bytesPerIter; i++) {
        bytes[i] = (PRUint8)(((bytesWritten + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE);

        if ((i == 0) || (i == (g_bytesPerIter - 1))) {
          LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                       "writer bytes[%d] = %d",
                       bytesWritten + i, bytes[i] ));
        }
      }

      //error = sock.write(g_bytesPerIter, bytes, g_timeoutMSWriter, &numBytesWritten);
      numBytesWritten = PR_Send(sock.hostSocket, bytes, g_bytesPerIter, 0, 0);
      error = IMQ_SUCCESS;
      if (numBytesWritten < 0) {
        numBytesWritten = 0;
      }
      if (numBytesWritten > 0) {
        fprintf(stderr, "%d             total bytes written\n", bytesWritten + numBytesWritten);
      }
      
      if (error == (iMQError)PR_WOULD_BLOCK_ERROR) {

      } 
      else if (error != IMQ_SUCCESS) {
        break;
      }
      bytesWritten+= numBytesWritten;
    }
  }

  fprintf(stderr, "Stopping because error = %d\n", error);
}





static void socketReader(void *arg)
{
  UNUSED(arg);
  static PRUint8 bytes[MAX_BYTES];
  PRInt32 numBytesRead = 0;
  PRInt32 error = IMQ_SUCCESS;
  
  PRInt32 nextTimeToPrint = 0;

  PRInt32 totalBytes = g_bytesPerIter * g_numIters;
  for (int bytesRead = 0; bytesRead < totalBytes; ) {
    numBytesRead = 0;
    bytes[0] = 0;
    bytes[1] = 0;
    if (g_maxSleepMSReader > 0) {
      PRUint32 sleeptime = 1000 * (rand()%g_maxSleepMSReader);
      PR_Sleep(PR_MicrosecondsToInterval(sleeptime));
    }

    // Initialize bytes
    int i;
    for (i = 0; i < g_bytesPerIter; i++) {
      bytes[i] = READ_BYTE;
    }

    error = sock.read(g_bytesPerIter, g_timeoutMSReader, bytes, &numBytesRead);

    if (numBytesRead > 0) {
      fprintf(stderr, "            %d total bytes read\n", bytesRead + numBytesRead );
    }

    // Check that the bytes read have the value of the bytes written
    for (i = 0; i < numBytesRead; i++) {
      if (bytes[i] != (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE)) {
        // This LOG_WARNING call can include class information since it's only called during testing
        LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                      "socketReader, bytes[%d]=bytes[%d]=%d, expected %d, bytesRead=%d, numBytesRead=%d",
                      bytesRead + i, i, bytes[i],
                      (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE),
                      bytesRead, numBytesRead ));
      }
    }

    for (i = 0; i < numBytesRead; i++) {
      ASSERT( bytes[i] == (((bytesRead + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE ) );
    }

    if (numBytesRead == 0) {
      PR_Sleep(0);
    }

    if (error != IMQ_SUCCESS) {
      fprintf(stderr, "socket.read returned %d\n", error);
      break;
    }
    bytesRead += numBytesRead;
    if (bytesRead > nextTimeToPrint) {
      // This LOG_WARNING call can include class information since it's only called during testing
      LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                    "socketReader, totalBytesRead=%d, thisBytesRead=%d",
                    bytesRead, numBytesRead ));
      //fprintf(stderr, "%d\n", bytesRead);
      nextTimeToPrint = bytesRead + g_printInc;
    }
    // randomly close the socket
    if (((double)rand()/(double)RAND_MAX) < g_readerCloseProb) {
      fprintf(stderr, "Reader closing the socket early\n" );
      sock.close();
      break;
    }
  }
}



/** 
 * 
 */
static void socketWriter(void *arg)
{
  UNUSED(arg);
  static PRUint8 bytes[MAX_BYTES];
  iMQError error = IMQ_SUCCESS;
  PRInt32 numBytesWritten = 0;

  PRInt32 nextTimeToPrint = 0;
  PRInt32 totalBytes = g_bytesPerIter * g_numIters;

  for (int bytesWritten = 0; bytesWritten < totalBytes; ) {
    if (g_maxSleepMSWriter > 0) {
      PRUint32 sleeptime = 1000 * (rand()%g_maxSleepMSWriter);
      PR_Sleep(PR_MicrosecondsToInterval(sleeptime));
    }
    // Initialize bytes
    for (int i = 0; i < g_bytesPerIter; i++) {
      bytes[i] = (PRUint8)(((bytesWritten + i) / TEST_DIV_VALUE) % TEST_MOD_VALUE);

      if ((i == 0) || (i == (g_bytesPerIter - 1))) {
        LOG_FINEST(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                     "writer bytes[%d] = %d",
                     bytesWritten + i, bytes[i] ));
      }
    }
    error = sock.write(g_bytesPerIter, bytes, g_timeoutMSWriter, &numBytesWritten);
    if (numBytesWritten > 0) {
      fprintf(stderr, "%d             total bytes written\n", bytesWritten + numBytesWritten);
    }
    
    if (error == (iMQError)PR_WOULD_BLOCK_ERROR) {

    } 
    else if (error != IMQ_SUCCESS) {
      fprintf(stderr, "socket.write returned %d\n", error);
      break;
    }
    bytesWritten+= numBytesWritten;
    if (bytesWritten >= nextTimeToPrint) {
      // This LOG_WARNING call can include class information since it's only called during testing
      LOG_WARNING(( CODELOC, SOCKET_LOG_MASK, NULL_CONN_ID, IMQ_SUCCESS,
                    "socketWriter, totalBytesWritten=%d, thisBytesWritten=%d",
                    bytesWritten, numBytesWritten ));
      nextTimeToPrint = bytesWritten + g_printInc;
    }
    // randomly close the socket
    if (((double)rand()/(double)RAND_MAX) < g_writerCloseProb) {
      fprintf(stderr, "Writer closing the socket early\n");
      sock.close();
      break;
    }
  }
}
