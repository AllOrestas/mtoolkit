package org.tigris.mtoolkit.iagent.internal;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.event.EventAdmin;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteService;
import org.tigris.mtoolkit.iagent.ServiceManager;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;
import org.tigris.mtoolkit.iagent.rpc.RemoteApplicationAdmin;
import org.tigris.mtoolkit.iagent.rpc.RemoteConsole;
import org.tigris.mtoolkit.iagent.rpc.RemoteDeploymentAdmin;

public class PropertiesRegestry implements RemoteDevicePropertyListener {

	private final static String[] propertyMap = { RemoteDeploymentAdmin.class.getName(),
		EventAdmin.class.getName(),
		RemoteApplicationAdmin.class.getName(),
		RemoteConsole.class.getName() };

	private ServiceManager serviceManager = null;
	private DeviceConnectorImpl connector = null;
	private HashMap connectionProperties;

	public PropertiesRegestry(ServiceManager serviceManager, DeviceConnectorImpl connector) {
		this.connector = connector;
		this.serviceManager = serviceManager;
		connectionProperties = new HashMap();
		initializeDeviceProperties();
	}

	private void initializeDeviceProperties() {
		for (int i = 0; i < propertyMap.length; i++) {
			try {
				updateProperty(i);
			} catch (IAgentException e) {
				e.printStackTrace();
			}
		}
	}

	private void updateProperty(int propertyIndex) throws IAgentException {
		RemoteService[] eventServices = serviceManager.getAllRemoteServices(propertyMap[propertyIndex], null);
		boolean state = true;
		if (eventServices == null || (eventServices.length == 0))
			state = false;

		connectionProperties.put(propertyMap[propertyIndex], new Boolean(state));
		connector.fireDevicePropertyEvent(propertyIndex, state);
	}

	public void devicePropertiesChanged(RemoteDevicePropertyEvent e) {
		connectionProperties.put(propertyMap[e.getType()], new Boolean(e.getEventState()));
	}

	public Map getDeviceProperties() {
		return (Map) connectionProperties;
	}
}
