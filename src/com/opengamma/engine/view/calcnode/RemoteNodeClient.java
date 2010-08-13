/**
 * Copyright (C) 2009 - 2009 by OpenGamma Inc.
 * 
 * Please see distribution for license.
 */
package com.opengamma.engine.view.calcnode;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.fudgemsg.FudgeContext;
import org.fudgemsg.FudgeFieldContainer;
import org.fudgemsg.FudgeMsgEnvelope;
import org.fudgemsg.mapping.FudgeDeserializationContext;
import org.fudgemsg.mapping.FudgeSerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.Lifecycle;

import com.opengamma.transport.FudgeConnection;
import com.opengamma.transport.FudgeMessageReceiver;
import com.opengamma.transport.FudgeMessageSender;

/**
 * Client end to RemoteNodeServer for registering one or more AbstractCalculationNodes with a remote job dispatcher.
 */
public class RemoteNodeClient extends AbstractCalculationNodeInvocationContainer<BlockingQueue<AbstractCalculationNode>> implements FudgeMessageReceiver, Lifecycle {

  private static final Logger s_logger = LoggerFactory.getLogger(RemoteNodeClient.class);

  private final FudgeConnection _connection;
  private final ExecutorService _executorService = Executors.newCachedThreadPool();
  private boolean _started;

  public RemoteNodeClient(final FudgeConnection connection) {
    super(new LinkedBlockingQueue<AbstractCalculationNode>());
    _connection = connection;
    connection.setFudgeMessageReceiver(this);
  }

  public RemoteNodeClient(final FudgeConnection connection, final AbstractCalculationNode node) {
    this(connection);
    setNode(node);
  }

  public RemoteNodeClient(final FudgeConnection connection, final Collection<AbstractCalculationNode> nodes) {
    this(connection);
    setNodes(nodes);
  }

  @Override
  public void onNodeChange() {
    if (isRunning()) {
      sendCapabilities();
    }
  }

  protected FudgeConnection getConnection() {
    return _connection;
  }

  protected ExecutorService getExecutorService() {
    return _executorService;
  }

  private void sendMessage(final RemoteCalcNodeMessage message) {
    final FudgeMessageSender sender = getConnection().getFudgeMessageSender();
    final FudgeSerializationContext context = new FudgeSerializationContext(sender.getFudgeContext());
    final FudgeFieldContainer msg = FudgeSerializationContext.addClassHeader(context.objectToFudgeMsg(message), message.getClass(), RemoteCalcNodeMessage.class);
    s_logger.debug("Sending message ({} fields) to {}", msg.getNumFields(), _connection);
    sender.send(msg);
  }

  protected void sendCapabilities() {
    final RemoteCalcNodeReadyMessage ready = new RemoteCalcNodeReadyMessage(getNodes().size());
    // TODO any other capabilities to add
    sendMessage(ready);
  }

  @Override
  public void messageReceived(FudgeContext fudgeContext, FudgeMsgEnvelope msgEnvelope) {
    final FudgeFieldContainer msg = msgEnvelope.getMessage();
    s_logger.debug("Received ({} fields) from {}", msg.getNumFields(), _connection);
    final FudgeDeserializationContext context = new FudgeDeserializationContext(fudgeContext);
    final RemoteCalcNodeMessage message = context.fudgeMsgToObject(RemoteCalcNodeMessage.class, msgEnvelope.getMessage());
    if (message instanceof RemoteCalcNodeJobMessage) {
      handleJobMessage((RemoteCalcNodeJobMessage) message);
    } else if (message instanceof RemoteCalcNodeInitMessage) {
      handleInitMessage((RemoteCalcNodeInitMessage) message);
    } else {
      s_logger.warn("Unexpected message - {}", message);
    }
  }

  private void handleJobMessage(final RemoteCalcNodeJobMessage message) {
    getExecutorService().execute(new Runnable() {
      @Override
      public void run() {
        try {
          final AbstractCalculationNode node = getNodes().take();
          final CalculationJobResult result = node.executeJob(message.getJob());
          getNodes().add(node);
          sendMessage(new RemoteCalcNodeResultMessage(result));
        } catch (InterruptedException e) {
          s_logger.warn("Thread interrupted");
        }
      }
    });
  }

  private void handleInitMessage(final RemoteCalcNodeInitMessage message) {
    s_logger.debug("Passing function repository to calculation nodes");
    for (AbstractCalculationNode node : getNodes()) {
      node.setFunctionRepository(message.getFunctions());
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return _started;
  }

  @Override
  public synchronized void start() {
    if (!_started) {
      s_logger.info("Client starting");
      sendCapabilities();
      _started = true;
      s_logger.info("Client started for {}", _connection);
    } else {
      s_logger.warn("Client already started");
    }
  }

  @Override
  public synchronized void stop() {
    if (_started) {
      s_logger.info("Client stopped");
      _started = false;
    } else {
      s_logger.warn("Client already stopped");
    }
  }

}
