/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

/*
 * @(#)Consumer.c	1.21 06/26/07
 */ 

/* 
 ***********************************************************
 * C sample program: Consumer.c                                      
 *
 * Description: 
 *
 * A synchronous message consumer that receives messages from
 * a destination
 *
 * Run this program in conjunction with the Producer.c sample
 * program.  If the destination type is topic, start the Consumer
 * first, then start the Producer.  By default the destination 
 * type is topic.  See usage for options.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mqcrt.h"

#define MQ_ERR_CHK(mqCall)                             \
  if (MQStatusIsError(status = (mqCall)) == MQ_TRUE) { \
    goto Cleanup;                                      \
  }


MQStatus
consumer(char * brokerHost, int brokerPort,
         char * destinationName, MQDestinationType destinationType)
{
  MQStatus status;

  /* Declare handles and initialize them */

  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;
  MQConnectionHandle connectionHandle = MQ_INVALID_HANDLE;
  MQSessionHandle sessionHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle destinationHandle = MQ_INVALID_HANDLE;
  MQConsumerHandle consumerHandle = MQ_INVALID_HANDLE;
  MQMessageHandle messageHandle = MQ_INVALID_HANDLE;
  MQPropertiesHandle headersHandle = MQ_INVALID_HANDLE;


  /* Setup connection properties */

  MQ_ERR_CHK( MQCreateProperties(&propertiesHandle) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_BROKER_HOST_PROPERTY, brokerHost) ); 
  MQ_ERR_CHK( MQSetInt32Property(propertiesHandle, 
                                 MQ_BROKER_PORT_PROPERTY, brokerPort) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle,
                                  MQ_CONNECTION_TYPE_PROPERTY, "TCP") );

  /* Create a connection to Message Queue broker */

  MQ_ERR_CHK( MQCreateConnection(propertiesHandle, "guest", "guest", NULL, 
                                 NULL, NULL, &connectionHandle) );

  /* Create a session for synchronous message receiving */ 

  MQ_ERR_CHK( MQCreateSession(connectionHandle, MQ_FALSE, MQ_CLIENT_ACKNOWLEDGE,
                              MQ_SESSION_SYNC_RECEIVE, &sessionHandle) );

  /* Create a destination */

  MQ_ERR_CHK( MQCreateDestination(sessionHandle, destinationName,
                                  destinationType, &destinationHandle) );

  /* Create a synchronous messagse consumer on the destination */

  MQ_ERR_CHK( MQCreateMessageConsumer(sessionHandle, destinationHandle,
                                      NULL, MQ_TRUE, &consumerHandle) );

  /* Free the destination handle */

  MQ_ERR_CHK( MQFreeDestination(destinationHandle) );

  /* Start the connection */

  MQ_ERR_CHK( MQStartConnection(connectionHandle) );


  /* Receiving messages */

  while (1) {

    fprintf(stdout, "Waiting for message ...\n");
    MQ_ERR_CHK( MQReceiveMessageWait(consumerHandle, &messageHandle) );
    {
      MQInt32 index;
      MQBool redelivered;
      ConstMQString my_msgtype;
      ConstMQString msgtext;
      MQMessageType messageType;

      /* Check message type */

      MQ_ERR_CHK( MQGetMessageType(messageHandle, &messageType) );
      if (messageType != MQ_TEXT_MESSAGE) {
        fprintf(stdout, "Received mesage is not MQ_TEXT_MESSAGE type.\n");
        MQ_ERR_CHK( MQAcknowledgeMessages(sessionHandle, messageHandle) );
		MQ_ERR_CHK( MQFreeMessage(messageHandle) );
        continue;
      }

      /* Get message body */

      MQ_ERR_CHK( MQGetTextMessageText(messageHandle, &msgtext) );
      fprintf(stdout, "Received message: %s\n", msgtext);

      /* Get message properties if any */

      status = MQGetMessageProperties(messageHandle, &propertiesHandle);
      if (MQGetStatusCode(status) != MQ_NO_MESSAGE_PROPERTIES) {
        MQ_ERR_CHK( status );
        status = MQGetInt32Property(propertiesHandle, "index", &index); 
        if (MQGetStatusCode(status) != MQ_NOT_FOUND) {
          MQ_ERR_CHK( status );
          fprintf(stdout, "\tProperty index=%d\n", index);
        }

        /* Free the properties handle */

        MQ_ERR_CHK( MQFreeProperties(propertiesHandle) );
      } 

      /* Get message headers, for example */
  
      MQ_ERR_CHK( MQGetMessageHeaders(messageHandle, &headersHandle) );

      MQ_ERR_CHK( MQGetBoolProperty(headersHandle, 
                            MQ_REDELIVERED_HEADER_PROPERTY, &redelivered) );  
      fprintf(stdout, "\tHeader redelivered=%d\n", redelivered);

      status = MQGetStringProperty(headersHandle,
                            MQ_MESSAGE_TYPE_HEADER_PROPERTY, &my_msgtype); 
      if (MQGetStatusCode(status) != MQ_NOT_FOUND) {
        MQ_ERR_CHK( status );
        fprintf(stdout, "\tHeader message-type=%s\n", my_msgtype);
      }

      /* Free the headers handle */

      MQ_ERR_CHK( MQFreeProperties(headersHandle) );


      /* Acknowledge the message */

      MQ_ERR_CHK( MQAcknowledgeMessages(sessionHandle, messageHandle) );
      if (strcmp(msgtext, "END") == 0) {
         MQ_ERR_CHK( MQFreeMessage(messageHandle) );
         break;
      }

      /* Free the message handle */

      MQ_ERR_CHK( MQFreeMessage(messageHandle) );
    }

  } /* while */


  /* Close the message consumer */

  MQ_ERR_CHK( MQCloseMessageConsumer(consumerHandle) );

  /* Close the session */
  MQ_ERR_CHK( MQCloseSession(sessionHandle) );

  /* Close the connection */

  MQ_ERR_CHK( MQCloseConnection(connectionHandle) );

  /* Free the connection handle */

  MQ_ERR_CHK( MQFreeConnection(connectionHandle) );

  return status;

Cleanup:
  { 
  MQString errorString = MQGetStatusString(status);
  fprintf(stderr, "consumer(): Error: %s\n",
                  (errorString == NULL) ? "NULL":errorString);
  MQFreeString(errorString);
  }
  MQFreeProperties(propertiesHandle);
  MQFreeProperties(headersHandle);
  MQFreeMessage(messageHandle);
  MQFreeDestination( destinationHandle );
  MQCloseConnection(connectionHandle);
  MQFreeConnection(connectionHandle);

  return status;
}


void
usageExit() {
  fprintf(stderr, "usage: Consumer [-h <broker-hostname>] [-p <broker-hostport>]\n");
  fprintf(stderr, "                [-t <topic|queue>] [-d <destination-name>] [-help]\n");
  fprintf(stderr, "\n");
  fprintf(stderr, "       defaults: localhost if no -h\n");
  fprintf(stderr, "                 7676      if no -p\n");
  fprintf(stderr, "                 topic     if no -t\n");
  fprintf(stderr, "                 example_producerconsumer_dest if no -d\n");
  exit(1);
}


int
main(int argc, char *argv[])
{
  char defaultBrokerHost[]      = "localhost";
  int  defaultBrokerPort        = 7676; 
  char defaultDestinationName[] = "example_producerconsumer_dest";

  char    *brokerHost = defaultBrokerHost;
  int      brokerPort = defaultBrokerPort; 
  char    *destinationName = NULL;
  MQDestinationType  destinationType = MQ_TOPIC_DESTINATION;
  int i;

  for (i = 1; i < argc; i++) {

    if (strcmp(argv[i], "-help") == 0) {
      usageExit();
    }
    if (i == argc - 1 || strncmp(argv[i+1], "-", 1) == 0) {
      usageExit();
    }
    if (strcmp(argv[i], "-h") == 0) {
      brokerHost = argv[++i];
      continue;
    }
    if (strcmp(argv[i], "-p") == 0) {
      brokerPort = atoi(argv[++i]);
      continue;
    }
    if (strcmp(argv[i], "-d") == 0) {
      destinationName = argv[++i];
      continue;
    }
    if (strcmp(argv[i], "-t") == 0) {
      if (strncmp(argv[++i], "q", 1) == 0) {
        destinationType = MQ_QUEUE_DESTINATION;
        continue;
      }
      if (strncmp(argv[i], "t", 1) == 0) {
        destinationType = MQ_TOPIC_DESTINATION;
        continue;
      }
      usageExit();
    }
    usageExit();

  }
  if (destinationName == NULL) {
    destinationName = &defaultDestinationName[0];
  }

  if (MQStatusIsError( consumer(brokerHost, brokerPort,
                                destinationName, destinationType) )) {
    return 1;
  }

  fprintf(stdout, "Done !\n");
  return 0;
}
