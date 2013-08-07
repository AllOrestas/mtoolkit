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
package org.tigris.mtoolkit.iagent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;
import org.tigris.mtoolkit.iagent.internal.DeviceConnectorImpl;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.transport.Transport;
import org.tigris.mtoolkit.iagent.transport.TransportsHub;

/**
 * This class represents connection to a remote OSGi framework. It is associated
 * to real connection over some transport API. It provides the command groups
 * with available commands to the connected OSGi framework.
 *
 * @version 1.0
 */
public abstract class DeviceConnector {
  /**
   * Specifies TCP connection type to the target OSGi framework
   */
  public static final int    TYPE_TCP       = 0;

  /**
   * Specifies the key for the host needed to establish client connection to
   * target OSGi framework. The value attached with this key in the connection
   * properties table must be a String object.
   */
  public static final String KEY_DEVICE_IP  = "framework-connection-ip";

  public static final String TRANSPORT_TYPE = "transport-type";

  public static final String TRANSPORT_ID   = "transport-id";

  /**
   * @since 3.1
   */
  public static final String PROP_PMP_PORT  = ConnectionManager.PROP_PMP_PORT;

  /**
   * Internal constant indicating that a device connector was disconnected.
   */
  protected static final int DISCONNECTED   = 1 << 0;

  /**
   * Internal constant indicating that a device connector was connected.
   */
  protected static final int CONNECTED      = 1 << 1;

  private static final List  listeners      = new ArrayList();

  public final Object        lockObj        = new Object();

  /**
   * Provides DeviceConnector connected to specified remote OSGi framework over
   * client connection.
   *
   * @param transport
   *          specifies the transport of this connection
   * @param aConProps
   *          the properties needed to establish connection. At least it must
   *          contain the host property. They must be mapped with specified keys
   *          - {@link DeviceConnector#KEY_DEVICE_IP}. The value for the host
   *          must be String.
   * @param monitor
   *          progress monitor. Can be null.
   * @return DeviceConnector object which is connected to the specified remote
   *         OSGi framework
   * @throws IAgentException
   *           thrown if connection could not be established
   */
  public final static DeviceConnector openClientConnection(Transport transport, Dictionary aConProps,
      IAProgressMonitor monitor) throws IAgentException {
    DeviceConnector connector = new DeviceConnectorImpl(transport, aConProps, monitor);
    fireConnectionEvent(CONNECTED, connector);
    return connector;
  }

  public final static DeviceConnector connect(Transport transport, Dictionary aConProps, IAProgressMonitor monitor)
      throws IAgentException {
    return openClientConnection(transport, aConProps, monitor);
  }

  public final static DeviceConnector connect(String transportType, String id, Dictionary aConProps,
      IAProgressMonitor monitor) throws IAgentException {
    try {
      Transport transport = TransportsHub.openTransport(transportType, id);
      if (transport == null) {
        throw new IAgentException("Unable to find compatible transport provider.", IAgentErrors.ERROR_CANNOT_CONNECT);
      }
      return connect(transport, aConProps, monitor);
    } catch (IOException e) {
      throw new IAgentException("Unable to establish connection", IAgentErrors.ERROR_CANNOT_CONNECT);
    }
  }

  /**
   * Provides {@link VMManager} associated with this device connector.
   * {@link VMManager} object is created only one time (the first time this
   * method is called). The returned object by this method will be one and the
   * same during the whole life of this device connector.
   *
   * @return {@link VMManager} through which OSGi specific commands could be
   *         executed on the device
   */
  public abstract VMManager getVMManager() throws IAgentException;

  /**
   * Provides {@link DeploymentManager} associated with this device connector.
   * {@link VMManager} object is created only one time (the first time this
   * method is called). The returned object by this method will be one and the
   * same during the whole life of this device connector.
   *
   * @return {@link VMManager} through which OSGi specific commands could be
   *         executed on the device
   */
  public abstract DeploymentManager getDeploymentManager() throws IAgentException;

  /**
   * Provides {@link ServiceManager} associated with this device connector.
   * {@link ServiceManager} object is created only one time (the first time this
   * method is called). The returned object by this method will be one and the
   * same during the whole life of this device connector.
   *
   * @return {@link ServiceManager} through which OSGi service registry can be
   *         inspected
   * @throws IAgentException
   */
  public abstract ServiceManager getServiceManager() throws IAgentException;

  /**
   * Closes connection to the remote OSGi framework which this
   * {@link DeviceConnector} provides. After calling this method the provided
   * command utilities will throw exception if used.
   *
   * @throws IAgentException
   *           thrown when this method have been already called or accepted
   *           client (in case of server connection) has been already
   *           disconnected
   */
  public abstract void closeConnection() throws IAgentException;

  /**
   * When this DeviceConnector is created it should be active all the time till
   * its {@link DeviceConnector#closeConnection()} method is called.
   * Additionally when the {@link DeviceConnector} is working over server
   * connection this method will return false if the accepted client has been
   * disconnected.
   *
   * @return true if the method {@link DeviceConnector#closeConnection()} has
   *         not been called yet, or the accepted client (in case of server
   *         connection) has not been disconnected. false in any other case.
   */
  public abstract boolean isActive();

  /**
   * Adds {@link DeviceConnectionListener}. The listener's
   * {@link DeviceConnectionListener#disconnected(DeviceConnector)} method will
   * be called when the connection this {@link DeviceConnector} is working over
   * is disconnected.
   *
   * @param aListener
   *          The {@link DeviceConnectionListener} to be added
   * @throws IAgentException
   *           Thrown when this {@link DeviceConnector}'s associated connection
   *           is already disconnected.
   */
  public static void addDeviceConnectionListener(DeviceConnectionListener aListener) {
    synchronized (listeners) {
      if (!listeners.contains(aListener)) {
        listeners.add(aListener);
      }
    }
  }

  /**
   * Removes {@link DeviceConnectionListener} listener.
   *
   * @param aListener
   *          The {@link DeviceConnectionListener} to be removed
   */
  public static void removeDeviceConnectionListener(DeviceConnectionListener aListener) {
    synchronized (listeners) {
      listeners.remove(aListener);
    }
  }

  protected static void fireConnectionEvent(int type, DeviceConnector connector) {
    DeviceConnectionListener[] clonedListeners;
    synchronized (listeners) {
      if (listeners.size() == 0) {
        return;
      }
      clonedListeners = (DeviceConnectionListener[]) listeners.toArray(new DeviceConnectionListener[listeners.size()]);
    }

    for (int i = 0; i < clonedListeners.length; i++) {
      DeviceConnectionListener listener = clonedListeners[i];
      try {
        switch (type) {
        case DISCONNECTED:
          listener.disconnected(connector);
          break;
        case CONNECTED:
          listener.connected(connector);
          break;
        }
      } catch (Throwable e) {
        DebugUtils.error(DeviceConnector.class, "Failed to deliver disconnection event to " + listener, e);
      }
    }
  }

  /**
   * Returns the connection properties of the DeviceConnector. Result is
   * returned immediately without blocking.
   *
   * @return properties, never null
   */
  public abstract Dictionary getProperties();

  /**
   * Returns the properties of the remote side of the DeviceConnector. This
   * method could perform communication with the remote side and could block.
   *
   * @return remote properties, never null
   * @throws IAgentException
   *           in case of error
   * @since 4.1
   */
  public abstract Dictionary getRemoteProperties() throws IAgentException;

  /**
   * Removes a listener from the listener list. This means that the listener
   * won't be notified for remote device properties change events.
   *
   * @param listener
   *          the listener to be removed
   * @throws IAgentException
   *           if the remote OSGi framework is already disconnected
   */
  public abstract void removeRemoteDevicePropertyListener(RemoteDevicePropertyListener listener) throws IAgentException;

  /**
   * Add a listener which will be notified whenever a device property change
   * event is generated on the remote site. Adding the same listener twice
   * doesn't have any effect
   *
   * @param listener
   *          the listener which will be notified for remote device properties
   *          change events
   * @throws IAgentException
   */
  public abstract void addRemoteDevicePropertyListener(RemoteDevicePropertyListener listener) throws IAgentException;

  public abstract Object getManager(String className) throws IAgentException;

}
