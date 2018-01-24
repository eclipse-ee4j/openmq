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
 * @(#)ProducerAsyncConsumer.c	1.25 06/26/07
 */ 

/* 
 ***********************************************************
 * C sample program: ProducerAsyncConsumer.c                                      
 *
 * Description:  
 *
 * A message producer and an asynchronous message consumer
 *
 * By default the destination type is topic. See usage for options.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "mqcrt.h"

#define MQ_ERR_CHK(mqCall)                              \
  if (MQStatusIsError(status = (mqCall)) == MQ_TRUE) {  \
    goto Cleanup;                                       \
  }

static MQConnectionHandle g_connectionHandle = MQ_INVALID_HANDLE;

#ifdef __cplusplus
extern "C" {
#endif
MQError messageListenerFunc(const MQSessionHandle sessionHandle,
                            const MQConsumerHandle consumerHandle,
                            MQMessageHandle messageHandle,
                            void * callbackData);
#ifdef __cplusplus
}
#endif


MQStatus
printVersion()
{
  MQStatus status;
  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;
  ConstMQString mqName, mqVersion;
  MQInt32 mqVmajor = 0, mqVminor=0, mqVmicro=0, mqSvcPack=0, mqUrelease=0;

  MQ_ERR_CHK( MQGetMetaData(g_connectionHandle, &propertiesHandle) );

  MQ_ERR_CHK( MQGetStringProperty(propertiesHandle, MQ_NAME_PROPERTY, &mqName) );
  fprintf(stdout, "\nMQ_NAME(mqcrt)=%s, MQ_NAME=%s\n",mqName, MQ_NAME);

  MQ_ERR_CHK( MQGetStringProperty(propertiesHandle, MQ_VERSION_PROPERTY, &mqVersion) );
  fprintf(stdout, "MQ_VERSION(mqcrt)=%s, MQ_VERSION=%s\n", mqVersion, MQ_VERSION);

  MQ_ERR_CHK( MQGetInt32Property(propertiesHandle, MQ_MAJOR_VERSION_PROPERTY, &mqVmajor) );
  fprintf(stdout, "MQ_VMAJOR(mqcrt)=%d, MQ_VMAJOR=%d\n",mqVmajor, MQ_VMAJOR);

  MQ_ERR_CHK( MQGetInt32Property(propertiesHandle, MQ_MINOR_VERSION_PROPERTY, &mqVminor) );
  fprintf(stdout, "MQ_VMINOR(mqcrt)=%d, MQ_VMINOR=%d\n", mqVminor, MQ_VMINOR);

  MQ_ERR_CHK( MQGetInt32Property(propertiesHandle, MQ_MICRO_VERSION_PROPERTY, &mqVmicro) );
  fprintf(stdout, "MQ_VMICRO(mqcrt)=%d, MQ_VMICRO=%d\n", mqVmicro, MQ_VMICRO);

  MQ_ERR_CHK( MQGetInt32Property(propertiesHandle, MQ_UPDATE_RELEASE_PROPERTY, &mqUrelease) );
  fprintf(stdout, "MQ_URELEASE(mqcrt)=%d, MQ_URELEASE=%d\n\n",mqUrelease, MQ_URELEASE);

  MQ_ERR_CHK( MQFreeProperties(propertiesHandle) );
  return status;

Cleanup:
  {
    MQString errorString = MQGetStatusString(status);
    fprintf(stderr, "printVersion(): Error: %s\n",
                    (errorString == NULL) ? "NULL":errorString);
    MQFreeString(errorString);
  }
  MQFreeProperties(propertiesHandle);
  return status;
}

/**
 * Asynchronous message receiving callback function
 */
MQError
messageListenerFunc(const MQSessionHandle sessionHandle,
                    const MQConsumerHandle consumerHandle,
                    MQMessageHandle messageHandle,
                    void * callbackData)
{
  MQStatus status;
  ConstMQString  text;

  /* Get the message body */
  MQ_ERR_CHK(MQGetTextMessageText(messageHandle, &text));
  fprintf(stdout, "\t\t\t\tReceived Message: %s\n", text);

  /* Acknowledge the message */
  MQ_ERR_CHK(MQAcknowledgeMessages(sessionHandle, messageHandle));

  /* Free the message handle */
  MQ_ERR_CHK( MQFreeMessage(messageHandle) );
  return MQ_SUCCESS;

Cleanup:
  {
    MQString errorString = MQGetStatusString(status);
    fprintf(stderr, "MessageListenerFunc(): Error: %s\n",
                    (errorString == NULL) ? "NULL":errorString);
    MQFreeString(errorString);
  }
  MQFreeMessage(messageHandle);
  return MQGetStatusCode(status);

}


MQStatus
setupConsumer(char *destinationName, MQDestinationType destinationType)
{
  MQStatus status;
  MQSessionHandle sessionHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle destinationHandle = MQ_INVALID_HANDLE;
  MQConsumerHandle consumerHandle = MQ_INVALID_HANDLE;

  /* Create a session for asynchronous message receiving */
  MQ_ERR_CHK( MQCreateSession(g_connectionHandle, MQ_FALSE, MQ_CLIENT_ACKNOWLEDGE,
                              MQ_SESSION_ASYNC_RECEIVE, &sessionHandle) );

  /* Create a destination */
  MQ_ERR_CHK( MQCreateDestination(sessionHandle, destinationName, destinationType,
                                  &destinationHandle) );
              
  /* Create an asynchronous message consumer on the destination */
  MQ_ERR_CHK( MQCreateAsyncMessageConsumer(sessionHandle, destinationHandle,
                                           NULL, MQ_FALSE, &messageListenerFunc,
                                           NULL, &consumerHandle) );

  /* Free the destination handle */
  MQ_ERR_CHK( MQFreeDestination(destinationHandle) );

  /* Start the connection */
  MQ_ERR_CHK( MQStartConnection(g_connectionHandle) );

  fprintf(stdout, "Message Listener setup complete\n");
  return status;

Cleanup:
  {
  MQString errorString = MQGetStatusString(status);
  fprintf(stderr, "setupConsumer(): Error: %s\n", 
                  (errorString == NULL) ? "NULL":errorString);
  MQFreeString(errorString);
  }
  MQFreeDestination(destinationHandle);
  MQCloseConnection(g_connectionHandle);
  MQFreeConnection(g_connectionHandle);
  return status;

}


MQStatus
producer(char *destinationName,
         MQDestinationType destinationType)
{
  MQStatus status;
  MQSessionHandle sessionHandle = MQ_INVALID_HANDLE;
  MQDestinationHandle destinationHandle = MQ_INVALID_HANDLE;
  MQProducerHandle producerHandle = MQ_INVALID_HANDLE;
  MQMessageHandle messageHandle = MQ_INVALID_HANDLE;


  MQ_ERR_CHK( MQCreateSession(g_connectionHandle, MQ_FALSE, MQ_CLIENT_ACKNOWLEDGE,
                              MQ_SESSION_SYNC_RECEIVE, &sessionHandle) );

  MQ_ERR_CHK( MQCreateDestination(sessionHandle, destinationName, destinationType,
                                  &destinationHandle) );

  MQ_ERR_CHK( MQCreateMessageProducerForDestination(sessionHandle,
                                    destinationHandle, &producerHandle) );
  MQ_ERR_CHK( MQFreeDestination(destinationHandle) );

  MQ_ERR_CHK( MQCreateTextMessage(&messageHandle) );
  while (1) {

      char text[128];
      printf("Enter a message to send:\n");
      if (fgets(text, 128, stdin) == NULL) {
          break;
      }
      if ((strlen(text) == 0)) {
          break;
      }
      printf("Sending message: %s\n", text);
      MQ_ERR_CHK( MQSetTextMessageText(messageHandle, text) );
      MQ_ERR_CHK( MQSendMessage(producerHandle, messageHandle) );
  }

  MQ_ERR_CHK( MQSetTextMessageText(messageHandle, "END") );
  MQ_ERR_CHK( MQSendMessage(producerHandle, messageHandle) );

  /* Free the message handle */
  MQ_ERR_CHK( MQFreeMessage(messageHandle) );

  /* Close the connection and free the connection handle */
  MQ_ERR_CHK( MQCloseConnection(g_connectionHandle) );
  MQ_ERR_CHK( MQFreeConnection(g_connectionHandle) );
  return status;

Cleanup:
  {
  MQString errorString = MQGetStatusString(status);
  fprintf(stderr, "producer(): Error: %s\n", 
                  (errorString == NULL) ? "NULL":errorString);
  MQFreeString(errorString);
  }
  MQFreeMessage( messageHandle );
  MQFreeDestination(destinationHandle);
  MQCloseConnection(g_connectionHandle);
  MQFreeConnection(g_connectionHandle);
  return status;
}


MQStatus
createConnection(char *brokerHost, int brokerPort)
{
  MQStatus status;
  MQPropertiesHandle propertiesHandle = MQ_INVALID_HANDLE;

  MQ_ERR_CHK( MQCreateProperties(&propertiesHandle) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_BROKER_HOST_PROPERTY, brokerHost) ); 
  MQ_ERR_CHK( MQSetInt32Property(propertiesHandle, 
                                 MQ_BROKER_PORT_PROPERTY, brokerPort) );
  MQ_ERR_CHK( MQSetStringProperty(propertiesHandle, 
                                  MQ_CONNECTION_TYPE_PROPERTY, "TCP") );

  MQ_ERR_CHK( MQCreateConnection(propertiesHandle, "guest", "guest", NULL, 
                                 NULL, NULL, &g_connectionHandle) );

  /* print MQ C-API version */
  MQ_ERR_CHK( printVersion() );

  return status;

Cleanup:
  {
  MQString errorString = MQGetStatusString(status);
  fprintf(stdout, "createConnection(): Error: %s\n",
                  (errorString == NULL) ? "NULL":errorString);
  MQFreeString(errorString);
  }
  MQFreeProperties(propertiesHandle);
  return status;
}


void
usageExit() {
  fprintf(stderr, 
      "usage: ProducerAsyncConsumer [-h <broker-hostname>] [-p <broker-hostport>]\n");
  fprintf(stderr, 
      "                             [-t <topic|queue>] [-d <destination-name>] [-help]\n");
  fprintf(stderr, "\n");
  fprintf(stderr, "       defaults: localhost if no -h\n");
  fprintf(stderr, "                 7676      if no -p\n");
  fprintf(stderr, "                 topic     if no -t\n");
  fprintf(stderr, "                 example_producerconsumer_async_dest if no -d\n");
  exit(1);
}


int
main(int argc, char *argv[])
{
  char defaultBrokerHost[]      = "localhost";
  int  defaultBrokerPort        = 7676; 
  char defaultDestinationName[] = "example_producerconsumer_async_dest";

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
  if (MQStatusIsError( createConnection(brokerHost, brokerPort) )) {
    return 1;
  }
  if (MQStatusIsError( setupConsumer(destinationName, destinationType) )) {
    return 1;
  }
  if (MQStatusIsError( producer(destinationName, destinationType) )) {
    return 1;
  }

  fprintf(stdout, "Done !\n");
  return 0;
}
