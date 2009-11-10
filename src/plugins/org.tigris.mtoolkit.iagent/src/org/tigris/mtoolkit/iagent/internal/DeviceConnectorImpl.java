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
package org.tigris.mtoolkit.iagent.internal;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tigris.mtoolkit.iagent.DeploymentManager;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.ServiceManager;
import org.tigris.mtoolkit.iagent.VMManager;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;
import org.tigris.mtoolkit.iagent.internal.tcp.ConnectionManagerImpl;
import org.tigris.mtoolkit.iagent.internal.utils.DebugUtils;
import org.tigris.mtoolkit.iagent.pmp.EventListener;
import org.tigris.mtoolkit.iagent.pmp.RemoteObject;
import org.tigris.mtoolkit.iagent.rpc.RemoteCapabilitiesProvider;
import org.tigris.mtoolkit.iagent.spi.AbstractConnection;
import org.tigris.mtoolkit.iagent.spi.ConnectionEvent;
import org.tigris.mtoolkit.iagent.spi.ConnectionListener;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.spi.DeviceConnectorSpi;
import org.tigris.mtoolkit.iagent.spi.IAgentManager;
import org.tigris.mtoolkit.iagent.spi.MethodSignature;
import org.tigris.mtoolkit.iagent.spi.PMPConnection;
import org.tigris.mtoolkit.iagent.transport.Transport;
import org.tigris.mtoolkit.iagent.transport.TransportsHub;
import org.tigris.mtoolkit.iagent.util.LightServiceRegistry;

/**
 * 
 * DeviceConnector implementation
 * 
 */
public class DeviceConnectorImpl extends DeviceConnector implements EventListener, DeviceConnectorSpi {
	private LightServiceRegistry serviceRegistry;
	private VMManagerImpl runtimeCommands;
	private DeploymentManagerImpl deploymentCommands;
	private ServiceManagerImpl serviceManager;
	private ConnectionManager connectionManager;
	private Object lock = new Object();
	private volatile boolean isActive = true;
	private Dictionary connectionProperties;

	private List devicePropertyListeners = new LinkedList();

	private String DEVICE_PROPERTY_EVENT = "iagent_property_event";

	private static final String EVENT_CAPABILITY_NAME = "capability.name";
	private static final String EVENT_CAPABILITY_VALUE = "capability.value";
	
	private HashMap managers;

	/**
	 * Creates new DeviceConnector with specified transport object
	 * 
	 * @param aConManager
	 * @throws IAgentException
	 */
	// TODO: remove this method. It is remained for backward compatibility.
	public DeviceConnectorImpl(Dictionary props) throws IAgentException {
		log("[Constructor] >>> connection properties: " + DebugUtils.convertForDebug(props));
		if (props == null)
			throw new IllegalArgumentException("Connection properties hashtable could not be null!");
		this.connectionProperties = props;

		String targetIP = (String) props.get(DeviceConnector.KEY_DEVICE_IP);
		if (targetIP == null)
			throw new IllegalArgumentException("Connection properties hashtable does not contain device IP value with key DeviceConnector.KEY_DEVICE_IP!");
		Transport transport;
		try {
			transport = TransportsHub.openTransport("socket", targetIP);
			setTransportProps(transport);
		} catch (IOException e) {
			throw new IAgentException("Unable to establish connection", IAgentErrors.ERROR_CANNOT_CONNECT);
		}
		if (transport == null) {
			throw new IAgentException("Unable to find compatible transport provider.", IAgentErrors.ERROR_CANNOT_CONNECT);
		}
		connectionManager = new ConnectionManagerImpl(transport, props);
		Boolean connectImmeadiate = (Boolean) props.get("framework-connection-immediate"); 
	    if (connectImmeadiate == null || connectImmeadiate.booleanValue()) {
	      log("[Constructor] Connect to device which support MBSA");
	      connect(ConnectionManager.MBSA_CONNECTION);
	    } else {  // connect directly to PMP
	      log("[Constructor] Connect to device which doesn't support MBSA");
	      connect(ConnectionManager.PMP_CONNECTION);
	    }
	}

	/**
	 * Creates new DeviceConnector with specified transport object
	 * 
	 * @param aConManager
	 * @throws IAgentException
	 */
	public DeviceConnectorImpl(Transport transport, Dictionary props) throws IAgentException {
		log("[Constructor] >>> connection properties: " + DebugUtils.convertForDebug(props));
		if (props == null)
			throw new IllegalArgumentException("Connection properties hashtable could not be null!");
		this.connectionProperties = props;
		setTransportProps(transport);
		
		connectionManager = new ConnectionManagerImpl(transport, props);
		Boolean connectImmeadiate = (Boolean) props.get("framework-connection-immediate"); 
	    if (connectImmeadiate == null || connectImmeadiate.booleanValue()) {
	      log("[Constructor] Connect to device which support MBSA");
	      connect(ConnectionManager.MBSA_CONNECTION);
	    } else {  // connect directly to PMP
	      log("[Constructor] Connect to device which doesn't support MBSA");
	      connect(ConnectionManager.PMP_CONNECTION);
	    }
	}

	private void monitorConnection(final int connectionType) {
		log("[monitorConnection] >>> connectionType: " + connectionType);
		connectionManager.addConnectionListener(new ConnectionListener() {
			public void connectionChanged(ConnectionEvent event) {
				if (event.getType() == ConnectionEvent.DISCONNECTED
								&& event.getConnection().getType() == connectionType) {
					log("[Constructor] connection of type: "
									+ connectionType
									+ " was disconnected. Close DeviceConnector...");
					try {
						if (isActive)
							closeConnection();
					} catch (IAgentException e) {
						IAgentLog.error("[DeviceConnectorImpl][Constructor] Failed to cleanup after disconnection", e);
					}
				}
			}
		});
	}

	private void connect(int connectionType) throws IAgentException {
		log("[connect] >>> connectionType: " + connectionType);
		// start monitoring the connection before connecting
		monitorConnection(connectionType);
		AbstractConnection connection = connectionManager.getActiveConnection(connectionType);
		if (connection == null) {
			log("[connect] No active connection with type: " + connectionType + ". Create new...");
			connection = connectionManager.createConnection(connectionType);
			if (connection == null) {
				log("[connect] Failed to create connection of type: " + connectionType);
				throw new IAgentException("Unable to create connection", IAgentErrors.ERROR_CANNOT_CONNECT);
			}
		}
		log("[connect] connection: " + connection);
	}

	public void closeConnection() throws IAgentException {
		log("[closeConnection] >>> Closing DeviceConnector...");
		synchronized (lock) {
			if (!isActive) {
				log("[closeConnection] Already closed.");
				return;
			}
			isActive = false;
		}
		try {
			if (deploymentCommands != null)
				deploymentCommands.removeListeners();
			if (serviceManager != null)
				serviceManager.removeListeners();
			if (connectionManager != null)
				((ConnectionManagerImpl) connectionManager).removeListeners();
			if (managers != null)
				managers = null;
			log("[closeConnection] Closing underlying connections...");
			connectionManager.closeConnections();
			log("[closeConnection] DeviceConnector closed successfully");
		} catch (Throwable t) {
			IAgentLog.error("[DeviceConnectorImpl][closeConnection] Failed to close underlying connections", t);
		}
		fireConnectionEvent(DISCONNECTED, this);
	}

	public VMManager getVMManager() throws IAgentException {
		synchronized (lock) {
			if (!isActive) {
				log("[getVMManager] Request for VMManager received, but DeviceConnector is closed");
				throw new IAgentException("The connection is closed", IAgentErrors.ERROR_DISCONNECTED);
			}
			if (runtimeCommands == null) {
				runtimeCommands = new VMManagerImpl(this);
			}
		}
		return runtimeCommands;
	}

	public DeploymentManager getDeploymentManager() throws IAgentException {
		synchronized (lock) {
			if (!isActive) {
				log("[getDeploymentManager] Request for DeploymentManager received, but DeviceConnector is closed");
				throw new IAgentException("The connection is closed", IAgentErrors.ERROR_DISCONNECTED);
			}
			if (deploymentCommands == null) {
				deploymentCommands = new DeploymentManagerImpl(this);
			}
		}
		return deploymentCommands;
	}

	public boolean isActive() {
		return isActive;
	}

	public ConnectionManager getConnectionManager() {
		return connectionManager;
	}

	public ServiceManager getServiceManager() throws IAgentException {
		synchronized (lock) {
			if (!isActive) {
				log("[getServiceManager] Request for ServiceManager received, but DeviceConnector is closed");
				throw new IAgentException("Connection to target device has been closed.",
					IAgentErrors.ERROR_DISCONNECTED);
			}
			if (serviceManager == null) {
				serviceManager = new ServiceManagerImpl(this);
			}
		}
		return serviceManager;
	}

	public Dictionary getProperties() {
		Dictionary props = cloneDictionary(connectionProperties);
		if (isActive) {
			try {
				PMPConnection connection = (PMPConnection) getConnection(ConnectionManager.PMP_CONNECTION);
				RemoteObject service = connection.getRemoteAdmin(RemoteCapabilitiesProvider.class.getName());
				if (service != null) {
					MethodSignature getCapabilities = new MethodSignature("getCapabilities"); //$NON-NLS-1$
					Map devCapabilities = (Map) getCapabilities.call(service);
					Iterator iterator = devCapabilities.keySet().iterator();
					while (iterator.hasNext()) {
						String property = (String) iterator.next();
						props.put(property, devCapabilities.get(property));
					}
				}
			} catch (Exception e) {
				IAgentLog.error("[DeviceConnectorImpl][getProperties] Failed to get Remote Capabilities", e);
			}
		}
		return props;
	}
	
	private Dictionary cloneDictionary(Dictionary source) {
		Dictionary dest = new Hashtable();
		for (Enumeration en = source.keys(); en.hasMoreElements();) {
			Object k = en.nextElement();
			dest.put(k, source.get(k));
		}
		return dest;
	}

	private final void log(String message) {
		log(message, null);
	}

	private final void log(String message, Throwable e) {
		DebugUtils.log(this, message, e);
	}

	void fireDevicePropertyEvent(String property, Object value) {
		log("[fireDevicePropertyEvent] >>> property: " + property);
		RemoteDevicePropertyListener[] listeners;
		synchronized (devicePropertyListeners) {
			if (devicePropertyListeners.size() != 0) {
				listeners = (RemoteDevicePropertyListener[]) devicePropertyListeners.toArray(new RemoteDevicePropertyListener[devicePropertyListeners.size()]);
			} else {
				return;
			}
		}
		RemoteDevicePropertyEvent event = new RemoteDevicePropertyEvent(property, value);
		log("[fireRemoteDevicePropertyEvent] " + listeners.length + " listeners found.");
		for (int i = 0; i < listeners.length; i++) {
			RemoteDevicePropertyListener listener = listeners[i];
			try {
				log("[fireRemoteDevicePropertyEvent] deliver event: " + event + " to listener: " + listener);
				listener.devicePropertiesChanged(event);
			} catch (Throwable e) {
				log("[fireRemoteDevicePropertyEvent] Failed to deliver event to " + listener, e);
			}
		}
	}

	public void addRemoteDevicePropertyListener(RemoteDevicePropertyListener listener) throws IAgentException {
		log("[addRemoteDevicePropertyListener] >>> listener: " + listener);
		synchronized (devicePropertyListeners) {
			if (!devicePropertyListeners.contains(listener)) {
				PMPConnection connection = (PMPConnection) getConnection(ConnectionManager.PMP_CONNECTION, false);
				if (connection != null) {
					log("[addRemoteDevicePropertyListener] PMP connection is available, add event listener");
					connection.addEventListener(this, new String[] { DEVICE_PROPERTY_EVENT });
				}
				devicePropertyListeners.add(listener);
			} else {
				log("[addRemoteDevicePropertyListener] Listener already present");
			}
		}
	}
	
	public AbstractConnection getConnection(int type, boolean create) throws IAgentException {
		log("[getConnection] >>> create: " + create);
		ConnectionManager connectionManager = getConnectionManager();
		AbstractConnection connection = connectionManager.getActiveConnection(type);
		if (connection == null && create) {
			log("[getConnection] No active connection found. Create new connection (type=" + type + ")...");
			if (!isActive()) {
				log("[getConnection] Request for new connection arrived, but DeviceConnector is disconnected.");
				throw new IAgentException("Associated DeviceConnector object is closed",
					IAgentErrors.ERROR_DISCONNECTED);
			}
			connection = connectionManager.createConnection(type);
			log("[getConnection] Connection opened successfully: " + connection);
		} else {
			log("[getConnection] Active connection found: " + connection);
		}
		return connection;
	}
	
	public AbstractConnection getConnection(int type) throws IAgentException {
		return getConnection(type, true);
	}

	public void event(Object event, String evType) {
		try {
			log("[event] >>> event: " + event + "; type: " + evType);
			if (DEVICE_PROPERTY_EVENT.equals(evType)) {
				Dictionary eventProps = (Dictionary) event;
				String capabilityName = (String) eventProps.get(EVENT_CAPABILITY_NAME);
				Object capabilityValue = eventProps.get(EVENT_CAPABILITY_VALUE);
				fireDevicePropertyEvent(capabilityName, capabilityValue);
			}
		} catch (Throwable e) {
			IAgentLog.error("[DeploymentManagerImpl][event] Failed to process PMP event: "
							+ event
							+ "; type: "
							+ evType);
		}
	}

	public void removeRemoteDevicePropertyListener(RemoteDevicePropertyListener listener) throws IAgentException {
		log("[removeRemoteDevicePropertyListener] >>> listener: " + listener);
		synchronized (devicePropertyListeners) {
			if (devicePropertyListeners.contains(listener)) {
				devicePropertyListeners.remove(listener);
				if (devicePropertyListeners.size() == 0) {
					log("[removeRemoteDevicePropertyListener] No more listeners in the list, try to remove PMP event listener");
					PMPConnection connection = (PMPConnection) getConnection(ConnectionManager.PMP_CONNECTION, false);
					if (connection != null) {
						log("[removeRemoteDevicePropertyListener] PMP connection is available, remove event listener");
						connection.removeEventListener(this, new String[] { DEVICE_PROPERTY_EVENT });
					}
				}
			} else {
				log("[removeRemoteDevicePropertyListener] Listener not found in the list");
			}
		}
	}
	
	public DeviceConnector getDeviceConnector() {
		return this;
	}
	
	private LightServiceRegistry getServiceRegistry() {
		if (serviceRegistry == null)
			serviceRegistry = new LightServiceRegistry(DeviceConnectorImpl.class.getClassLoader());
		return serviceRegistry;
	}

	public Object getManager(String className) throws IAgentException {
		synchronized (lock) {
			if (!isActive) {
				log("[getManager] Request for getting Manager [" + className + "] received, but DeviceConnector is closed");
				throw new IAgentException("The connection is closed", IAgentErrors.ERROR_DISCONNECTED);
			}
			if (managers == null)
				managers = new HashMap(4);
			IAgentManager manager = (IAgentManager) managers.get(className);
			if (manager == null) {
				LightServiceRegistry registry = getServiceRegistry();
				manager = (IAgentManager) registry.get(className);
				manager.init(this);
				managers.put(className, manager);
			}
			return manager;
		}
	}
	
	private void setTransportProps(Transport transport) {
		connectionProperties.put(TRANSPORT_TYPE, transport.getType().getTypeId());
		connectionProperties.put(TRANSPORT_ID, transport.getId());
	}
}
