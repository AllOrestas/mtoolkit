/*******************************************************************************
 * Copyright (c) 2005, 2009 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.iagent.internal.tcp;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tigris.mtoolkit.iagent.IAProgressMonitor;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.spi.AbstractConnection;
import org.tigris.mtoolkit.iagent.spi.ConnectionEvent;
import org.tigris.mtoolkit.iagent.spi.ConnectionListener;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.spi.ExtConnectionFactory;
import org.tigris.mtoolkit.iagent.transport.Transport;
import org.tigris.mtoolkit.iagent.util.LightServiceRegistry;

public final class ConnectionManagerImpl implements ConnectionManager {
  protected Dictionary conProperties;
  protected Transport  transport;
  private List         listeners    = new LinkedList();
  private List         extFactories = new LinkedList();
  private Map          connections  = new Hashtable();

  public ConnectionManagerImpl(Transport transport, Dictionary aConProperties) {
    this.transport = transport;
    this.conProperties = aConProperties;
    LightServiceRegistry registry = new LightServiceRegistry(ConnectionManagerImpl.class.getClassLoader());
    Object[] extenders = registry.getAll(ExtConnectionFactory.class.getName());
    for (int i = 0; i < extenders.length; i++) {
      if (extenders[i] instanceof ExtConnectionFactory) {
        extFactories.add(extenders[i]);
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#createConnection(int)
   */
  public AbstractConnection createConnection(int type) throws IAgentException {
    return createConnection(type, null);
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#createConnection(int, org.tigris.mtoolkit.iagent.IAProgressMonitor)
   */
  public AbstractConnection createConnection(int type, IAProgressMonitor monitor) throws IAgentException {
    DebugUtils.debug(this, "[createConnection] >>> type: " + type);
    AbstractConnection connection = null;
    AbstractConnection staleConnection = null;
    boolean fireEvent = false;
    Integer key = new Integer(type);

    // Connection creation is done in two steps. This have the side effect
    // that DISCONNECT event
    // can be delivered after CONNECT event.

    // Step 1: check whether there is a stale connection is closed/closing
    // If there is a closed/closing connection, save the reference and clear
    // the active connection reference
    // This gives us two things: 1. event won't be fired from the
    // connectionClosed() method
    // 2. saved reference will be used when firing the event
    synchronized (this) {
      connection = (AbstractConnection) connections.get(key);
      if (connection != null && !connection.isConnected()) {
        staleConnection = connection;
        connections.remove(key);
      }
    }
    // If stale connection is detected, try to close it (just in case) and
    // fire an event for the disconnection
    if (staleConnection != null) {
      staleConnection.closeConnection();
      fireConnectionEvent(ConnectionEvent.DISCONNECTED, staleConnection);
    }
    // Step 2: check whether there is an active connection. Create if
    // necessary.
    // It is very unlikely to get stale connection here, because we have
    // already checked it in Step 1
    synchronized (this) {
      connection = (AbstractConnection) connections.get(key);
      if (connection == null) {
        fireEvent = true;
        switch (type) {
        case MBSA_CONNECTION:
          connection = createMBSAConnection(transport);
          break;
        case PMP_CONNECTION:
          connection = createPMPConnection(transport);
          break;
        default:
          ExtConnectionFactory factory = findFactoryForType(type);
          if (factory == null) {
            DebugUtils.info(this, "[createConnection] Unknown connection type passed: " + type);
            throw new IllegalArgumentException("Unknown connection type passed: " + type);
          }
          connection = factory.createConnection(transport, conProperties, this, monitor);
        }
        connections.put(key, connection);
      }
    }
    // If new connection was created, fire an event
    if (fireEvent) {
      fireConnectionEvent(ConnectionEvent.CONNECTED, connection);
      DebugUtils.debug(this, "[createConnection] Finished sending events");
    }
    DebugUtils.debug(this, "[createConnection] connection: " + connection);
    return connection;
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#getActiveConnection(int)
   */
  public synchronized AbstractConnection getActiveConnection(int type) {
    DebugUtils.debug(this, "[getActiveConnection] >>> type: " + type);
    AbstractConnection connection = (AbstractConnection) connections.get(new Integer(type));
    if (connection != null && !connection.isConnected()) {
      connection = null;
    }
    DebugUtils.debug(this, "[getActiveConnection] connection: " + connection);
    return connection;
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#closeConnections()
   */
  public void closeConnections() throws IAgentException {
    DebugUtils.debug(this, "[closeConnections] >>>");
    // only call closeConnection() because it will result in
    // connectionClosed()
    ArrayList tmpConnections = new ArrayList();
    synchronized (this) {
      tmpConnections.addAll(connections.values());
    }
    Iterator it = tmpConnections.iterator();
    while (it.hasNext()) {
      AbstractConnection connection = (AbstractConnection) it.next();
      connection.closeConnection();
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#addConnectionListener(org.tigris.mtoolkit.iagent.spi.ConnectionListener)
   */
  public void addConnectionListener(ConnectionListener listener) {
    DebugUtils.debug(this, "[addConnectionListener]  >>> listener: " + listener);
    synchronized (listeners) {
      if (!listeners.contains(listener)) {
        listeners.add(listener);
      } else {
        DebugUtils.debug(this, "[addConnectionListener] listener already have been added");
      }
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#removeConnectionListener(org.tigris.mtoolkit.iagent.spi.ConnectionListener)
   */
  public void removeConnectionListener(ConnectionListener listener) {
    DebugUtils.debug(this, "[removeConnectionListener] >>> listener: " + listener);
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  /**
   * This method should be called by
   * {@link AbstractConnection#closeConnection()} method every time it is
   * called, passing itself as argument for the method.
   *
   * @param connection
   */
  public void connectionClosed(AbstractConnection connection, boolean notify) {
    DebugUtils.debug(this, "[connectionClosed] >>> connection: " + connection);
    boolean sendEvent = false;
    synchronized (this) {
      if (connection == null) {
        return;
      }
      Integer key = new Integer(connection.getType());
      AbstractConnection currentConnection = (AbstractConnection) connections.get(key);
      if (currentConnection == connection) {
        DebugUtils.debug(this, "[connectionClosed] Active connection match, connection type: " + connection.getType());
        connections.remove(key);
        sendEvent = true;
      }
    }
    if (sendEvent && notify) {
      fireConnectionEvent(ConnectionEvent.DISCONNECTED, connection);
    }
  }

  /* (non-Javadoc)
   * @see org.tigris.mtoolkit.iagent.spi.ConnectionManager#queryProperty(java.lang.String)
   */
  public Object queryProperty(String propertyName) {
    Map activeConnections = new HashMap();
    synchronized (this) {
      activeConnections.putAll(connections);
    }
    for (Iterator it = activeConnections.values().iterator(); it.hasNext();) {
      AbstractConnection connection = (AbstractConnection) it.next();
      Object value = connection.getProperty(propertyName);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  /**
   * Returns types of ext controller connections.
   *
   * @return array of types or empty array
   */
  public int[] getExtControllerConnectionTypes() {
    List types = new ArrayList();
    for (Iterator it = extFactories.iterator(); it.hasNext();) {
      ExtConnectionFactory factory = (ExtConnectionFactory) it.next();
      if (factory.isControllerType()) {
        types.add(new Integer(factory.getConnectionType()));
      }
    }
    int[] result = new int[types.size()];
    int i = 0;
    for (Iterator it = types.iterator(); it.hasNext();) {
      result[i++] = ((Integer) it.next()).intValue();
    }
    return result;
  }

  public void removeListeners() {
    DebugUtils.debug(this, "[removeListeners] >>>");
    synchronized (listeners) {
      listeners.clear();
    }
  }

  private MBSAConnectionImpl createMBSAConnection(Transport transport) throws IAgentException {
    MBSAConnectionImpl connection = new MBSAConnectionImpl(transport, conProperties, this);
    DebugUtils.debug(this, "[createMBSAConnection] Created connection: " + connection);
    return connection;
  }

  private PMPConnectionImpl createPMPConnection(Transport transport) throws IAgentException {
    final PMPConnectionImpl connection = new PMPConnectionImpl(transport, conProperties, this);
    DebugUtils.debug(this, "[createPMPConnection] Created connection: " + connection);
    return connection;
  }

  private void fireConnectionEvent(int type, AbstractConnection connection) {
    DebugUtils.debug(this, "[fireConnectionEvent] >>> type=" + type + ";connection=" + connection);
    ConnectionListener[] clonedListeners;
    synchronized (listeners) {
      if (listeners.size() == 0) {
        DebugUtils.debug(this, "[fireConnectionEvent] There were no listeners");
        return;
      }
      clonedListeners = (ConnectionListener[]) listeners.toArray(new ConnectionListener[listeners.size()]);
    }
    ConnectionEvent event = new ConnectionEvent(type, connection);
    DebugUtils.debug(this, "[fireConnectionEvent] Sending event: " + event + " to " + clonedListeners.length
        + " connection listeners");
    for (int i = 0; i < clonedListeners.length; i++) {
      ConnectionListener listener = clonedListeners[i];
      try {
        DebugUtils.debug(this, "[fireConnectionEvent] Sending event to " + listener);
        listener.connectionChanged(event);
      } catch (Throwable e) {
        DebugUtils.error(this, "[fireConnectionEvent] Failed to deliver event to " + listener, e);
      }
    }
  }

  private ExtConnectionFactory findFactoryForType(int type) {
    for (Iterator it = extFactories.iterator(); it.hasNext();) {
      ExtConnectionFactory factory = (ExtConnectionFactory) it.next();
      if (factory.getConnectionType() == type) {
        return factory;
      }
    }
    return null;
  }
}
