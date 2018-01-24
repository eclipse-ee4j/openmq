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
 * @(#)RequestReply.c	1.26 06/26/07
 */ 

/* 
 ***********************************************************
 * C sample program: RequestReply.c                                      
 *  
 * Description:
 *
 * A simple request-reply program
 *
 * Specify mode (requestor or replier) when you run this program.
 * Run the requestor inconjunction with the replier. If the destination
 * type is topic, start the replier first, then run the requestor.
 * By default the destination type is topic.  See usage for options.
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
reply(MQConnectionHandle connectionHandle,
      MQSessionHandle sessionHandle,
      MQDestinationHandle destinationHandle)
{
  MQStatus status;
  MQConsumerHandle consumerHandle = MQ_INVALID_HANDLE;
  MQMessageHandle recvMessageHandle = MQ_INVALID_HANDLE;
  MQMessageHandle sendMessageHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle replyToHandle = MQ_INVALID_HANDLE;
  MQProducerHandle producerHandle = MQ_INVALID_HANDLE;
  ConstMQString msgText;

  MQ_ERR_CHK( MQCreateMessageConsumer(sessionHandle, destinationHandle,
                                      NULL, MQ_TRUE, &consumerHandle) );

  fprintf(stdout, "Waiting for request ...\n");
  MQ_ERR_CHK( MQReceiveMessageWait(consumerHandle, &recvMessageHandle) );

  MQ_ERR_CHK( MQGetTextMessageText(recvMessageHandle, &msgText) );
  fprintf(stdout, "Received request: %s\n", msgText);

  /* Get the destination that a reply to this message should be sent */
  MQ_ERR_CHK( MQGetMessageReplyTo(recvMessageHandle, &replyToHandle) );


  /* Create a message producer for sending a reply */
  MQ_ERR_CHK( MQCreateMessageProducerForDestination(sessionHandle,
                                       replyToHandle, &producerHandle));

  /* Free the destination handle */
  MQ_ERR_CHK( MQFreeDestination(replyToHandle) );

  MQ_ERR_CHK( MQCreateTextMessage(&sendMessageHandle) );
  MQ_ERR_CHK( MQSetTextMessageText(sendMessageHandle, "This is a reply") );
  fprintf(stdout, "Sending reply ...\n");
  MQ_ERR_CHK( MQSendMessage(producerHandle, sendMessageHandle) );
  fprintf(stdout, "Reply sent.\n");
  MQ_ERR_CHK( MQFreeMessage(sendMessageHandle) );

  /* Acknowledge the request message received */
  MQ_ERR_CHK( MQAcknowledgeMessages(sessionHandle, recvMessageHandle) );
  MQFreeMessage( recvMessageHandle );
  return status;

Cleanup:
  {
    MQString errorString = MQGetStatusString(status);
    fprintf(stderr, "reply(): Error: %s\n",
                    (errorString == NULL) ? "NULL":errorString);
    MQFreeString(errorString);
  }
  MQFreeDestination( replyToHandle );
  MQFreeMessage(recvMessageHandle);
  MQFreeMessage(sendMessageHandle);
  return status;

}


MQStatus
request(MQConnectionHandle connectionHandle,
        MQSessionHandle sessionHandle,
        MQDestinationHandle destinationHandle)
{
  MQStatus status;
  MQProducerHandle producerHandle = MQ_INVALID_HANDLE;
  MQMessageHandle  messageHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle replyToHandle = MQ_INVALID_HANDLE;
  MQConsumerHandle consumerHandle = MQ_INVALID_HANDLE;
  ConstMQString msgText;

  MQ_ERR_CHK( MQCreateMessageProducerForDestination(sessionHandle,
                                    destinationHandle, &producerHandle));

  /* Create a temporary destination */
  MQ_ERR_CHK( MQCreateTemporaryDestination(sessionHandle,
                                    MQ_QUEUE_DESTINATION, &replyToHandle) );

  fprintf(stdout, "Creating a text message ...\n");
  MQ_ERR_CHK( MQCreateTextMessage(&messageHandle) );
  MQ_ERR_CHK( MQSetTextMessageText(messageHandle, "This is a request") );

  /* Set where a reply to this message should be sent  */
  MQ_ERR_CHK( MQSetMessageReplyTo(messageHandle, replyToHandle) );

  /* Create a message consumer to receive the reply */
  MQ_ERR_CHK( MQCreateMessageConsumer(sessionHandle, replyToHandle,
                                      NULL, MQ_TRUE, &consumerHandle) );

  MQ_ERR_CHK( MQFreeDestination(replyToHandle) );

  fprintf(stdout, "Sending request ...\n");
  MQ_ERR_CHK( MQSendMessage(producerHandle, messageHandle) );
  MQ_ERR_CHK( MQFreeMessage(messageHandle) );

  fprintf(stdout, "Waiting for reply ...\n");
  MQ_ERR_CHK( MQReceiveMessageWait(consumerHandle, &messageHandle) );
  MQ_ERR_CHK( MQGetTextMessageText(messageHandle, &msgText) );
  fprintf(stdout, "Received reply: %s\n", msgText);
  MQ_ERR_CHK( MQAcknowledgeMessages(sessionHandle, messageHandle) );
  MQ_ERR_CHK( MQFreeMessage(messageHandle) );

  return status;

Cleanup:
  {
    MQString errorString = MQGetStatusString(status);
    fprintf(stderr, "request(): Error: %s\n",
                    (errorString == NULL) ? "NULL":errorString);
    MQFreeString(errorString);
  }
  MQFreeMessage(messageHandle);
  MQFreeDestination(replyToHandle);
  return status;
}


MQStatus
setup(char *brokerHost, int brokerPort,
      char *destinationName,
      MQDestinationType destinationType, MQBool isConsumer)
{
  MQStatus status;
  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;
  MQConnectionHandle connectionHandle = MQ_INVALID_HANDLE;
  MQSessionHandle sessionHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle destinationHandle = MQ_INVALID_HANDLE;


  MQ_ERR_CHK( MQCreateProperties(&propertiesHandle) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_BROKER_HOST_PROPERTY, brokerHost) ); 
  MQ_ERR_CHK( MQSetInt32Property(propertiesHandle,
                                 MQ_BROKER_PORT_PROPERTY, brokerPort) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_CONNECTION_TYPE_PROPERTY, "TCP") );

  MQ_ERR_CHK( MQCreateConnection(propertiesHandle, "guest", "guest", NULL, 
                                 NULL, NULL, &connectionHandle) );

  MQ_ERR_CHK( MQCreateSession(connectionHandle, MQ_FALSE, MQ_CLIENT_ACKNOWLEDGE,
                              MQ_SESSION_SYNC_RECEIVE, &sessionHandle) );

  MQ_ERR_CHK( MQCreateDestination(sessionHandle, destinationName,
                                  destinationType, &destinationHandle) );

  MQ_ERR_CHK( MQStartConnection(connectionHandle) );
  if (isConsumer == MQ_TRUE) {
      MQ_ERR_CHK( reply(connectionHandle, sessionHandle, destinationHandle) );
  }
  else {
      MQ_ERR_CHK( request(connectionHandle, sessionHandle, destinationHandle) );
  }
  MQ_ERR_CHK( MQFreeDestination(destinationHandle) );
  MQ_ERR_CHK( MQCloseConnection(connectionHandle) );
  MQ_ERR_CHK( MQFreeConnection(connectionHandle) );
  return status;

Cleanup:
  {
    MQString errorString = MQGetStatusString(status);
    fprintf(stderr, "setup(): Error: %s\n",
                    (errorString == NULL) ? "NULL":errorString);
    MQFreeString(errorString);
  }
  MQFreeProperties(propertiesHandle);
  MQFreeDestination(destinationHandle);
  MQCloseConnection(connectionHandle);
  MQFreeConnection(connectionHandle);
  return status;
}


void
usageExit() {
  fprintf(stderr, "usage: RequestReply [-h <broker-host>] [-p <broker-port>]\n");
  fprintf(stderr, "                    [-t <topic|queue>] [-d <destination-name>]\n");
  fprintf(stderr, "                    -mode <requestor>|<replier> [-help]\n");
  fprintf(stderr, "\n");
  fprintf(stderr, "       defaults: localhost if no -h\n");
  fprintf(stderr, "                 7676      if no -p\n");
  fprintf(stderr, "                 topic     if no -t\n");
  fprintf(stderr, "                 example_requestreply_dest if no -d\n");
  exit(1);
}


int
main(int argc, char *argv[])
{
  char defaultBrokerHost[]      = "localhost";
  int  defaultBrokerPort        = 7676; 
  char defaultDestinationName[] = "example_requestreply_dest";

  char    *brokerHost = defaultBrokerHost;
  int      brokerPort = defaultBrokerPort; 
  char    *destinationName = NULL;
  MQBool  isConsumer = MQ_FALSE, hasmode = MQ_FALSE; 
  MQDestinationType  destinationType = MQ_TOPIC_DESTINATION;
  int i;

  if (argc < 2) {
    usageExit();
  }

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
    if (strncmp(argv[i], "-m", 2) == 0) {
      hasmode = MQ_TRUE;
      if (strncmp(argv[++i], "rep", 3) == 0) {
        isConsumer = MQ_TRUE;
        continue;
      }
      if (strncmp(argv[i], "req", 3) == 0) {
        isConsumer = MQ_FALSE;
        continue;
      }
      usageExit();
    }
    usageExit();

  } /* for */

  if (hasmode == MQ_FALSE) {
    usageExit();
  }
  if (destinationName == NULL) {
    destinationName = &defaultDestinationName[0];
  }

  if (MQStatusIsError( setup(brokerHost, brokerPort, destinationName,
                             destinationType, isConsumer) )) {
    return 1;
  }

  fprintf(stdout, "Done !\n");
  return 0;
}
