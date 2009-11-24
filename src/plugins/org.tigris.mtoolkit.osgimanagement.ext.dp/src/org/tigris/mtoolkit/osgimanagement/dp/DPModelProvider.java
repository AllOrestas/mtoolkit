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
package org.tigris.mtoolkit.osgimanagement.dp;

import java.util.Dictionary;

import javax.swing.text.StyleContext.SmallAttributeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.swt.graphics.Image;
import org.tigris.mtoolkit.iagent.DeploymentManager;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentErrors;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.RemoteDP;
import org.tigris.mtoolkit.iagent.event.RemoteDPEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDPListener;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyEvent;
import org.tigris.mtoolkit.iagent.event.RemoteDevicePropertyListener;
import org.tigris.mtoolkit.iagent.rpc.Capabilities;
import org.tigris.mtoolkit.osgimanagement.ContentTypeModelProvider;
import org.tigris.mtoolkit.osgimanagement.browser.model.Framework;
import org.tigris.mtoolkit.osgimanagement.browser.model.Model;
import org.tigris.mtoolkit.osgimanagement.browser.model.SimpleNode;
import org.tigris.mtoolkit.osgimanagement.dp.images.ImageHolder;
import org.tigris.mtoolkit.osgimanagement.dp.model.DeploymentPackage;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.BrowserErrorHandler;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.FrameworkConnectorFactory;

public class DPModelProvider implements ContentTypeModelProvider, RemoteDPListener, RemoteDevicePropertyListener {

	private static SimpleNode dpNode;
	private DeviceConnector connector;
	private Model parent;
	private DeploymentManager manager;
	private boolean supportDP;
	
	private static final String DP_ICON_PATH = "dpackage.gif";

	public Model connect(Model parent, DeviceConnector connector, IProgressMonitor monitor) {
		this.connector = connector;
		this.parent = parent;
		
		Dictionary connectorProperties = connector.getProperties();
		Object support = connectorProperties.get(Capabilities.DEPLOYMENT_SUPPORT);
		if (support != null && Boolean.valueOf(support.toString()).booleanValue()) {
			supportDP = true;
		}
		try {
			connector.addRemoteDevicePropertyListener(this);
		} catch (IAgentException e1) {
			e1.printStackTrace();
		}
		
		if (supportDP) {
			initModel(monitor);
		}
		return dpNode;
	}

	private void initModel(IProgressMonitor monitor) {
		dpNode = new SimpleNode("Deployment Packages");
		if (parent.findFramework().getViewType() == Framework.BUNDLES_VIEW) { 
			parent.addElement(dpNode);
		}
		try {
			manager = connector.getDeploymentManager();
			addDPs(monitor);
			try {
				manager.addRemoteDPListener(this);
			} catch (IAgentException e) {
				e.printStackTrace();
			}
		} catch (IAgentException e) {
			e.printStackTrace();
		}
	}


	public void disconnect() {
		if (manager != null) {
			try {
				manager.removeRemoteDPListener(this);
			} catch (IAgentException e) {
				e.printStackTrace();
			}
		}
		if (parent != null) {
			if (dpNode != null) {
				parent.removeElement(dpNode);
			}
			parent = null;
		}
		connector = null;
		dpNode = null;
		supportDP = false;
	}

	public Image getImage(Model node) {
		return ImageHolder.getImage(DP_ICON_PATH);
	}

	public Model switchView(int viewType) {
		Model node = null;
		if (supportDP) {
			if (viewType == Framework.BUNDLES_VIEW) {
				parent.addElement(dpNode);
				node = dpNode;
			} else if (viewType == Framework.SERVICES_VIEW) {
				parent.removeElement(dpNode);
			}
		}
		return node;
	}

	private void addDPs(IProgressMonitor monitor) throws IAgentException {
		if (!supportDP) {
			return;
		}
		Model deplPackagesNode = dpNode;
		RemoteDP dps[] = null;
		dps = connector.getDeploymentManager().listDeploymentPackages();

		if (dps != null && dps.length > 0) {
			SubMonitor sMonitor = SubMonitor.convert(monitor, dps.length);
			sMonitor.setTaskName("Retrieving deployment packages information...");

			for (int i = 0; i < dps.length; i++) {
				DeploymentPackage dpNode = new DeploymentPackage(dps[i], (Framework) parent);
				deplPackagesNode.addElement(dpNode);
				monitor.worked(1);
				if (monitor.isCanceled()) {
					return;
				}
			}
		}
	}

	public void deploymentPackageChanged(final RemoteDPEvent e) {
		try {
			BrowserErrorHandler
					.debug("Deployment package changed " + e.getDeploymentPackage().getName() + " " + (e.getType() == RemoteDPEvent.INSTALLED ? "INSTALLED" : "UNINSTALLED"));} catch (IAgentException e2) {} //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		synchronized (FrameworkConnectorFactory.getLockObject(connector)) {
			try {
				RemoteDP remoteDP = e.getDeploymentPackage();
				if (e.getType() == RemoteDPEvent.INSTALLED) {
					Model dpNodeRoot = dpNode;
					try {
						// check if this install actually is update
						DeploymentPackage dp = findDP(remoteDP.getName());
						if (dp != null) {
							dpNode.removeElement(dp);
						}

						DeploymentPackage dpNode = new DeploymentPackage(remoteDP, (Framework) parent);
						dpNodeRoot.addElement(dpNode);
					} catch (IAgentException e1) {
						if (e1.getErrorCode() != IAgentErrors.ERROR_DEPLOYMENT_STALE
								&& e1.getErrorCode() != IAgentErrors.ERROR_REMOTE_ADMIN_NOT_AVAILABLE) {
							BrowserErrorHandler.processError(e1, connector, e1.getMessage());
						}
					}
				} else if (e.getType() == RemoteDPEvent.UNINSTALLED) {
					if (remoteDP != null) {
						// there are cases where the dp failed to be added,
						// because it was too quickly uninstalled/updated
						DeploymentPackage dp = findDP(remoteDP.getName());
						if (dp != null) {
							dpNode.removeElement(dp);
						}
					}
				}

				dpNode.updateElement();
			} catch (IllegalStateException ex) {
				// ignore state exceptions, which usually indicates that something
				// is was fast enough to disappear
				BrowserErrorHandler.debug(ex);
			} catch (Throwable t) {
				t.printStackTrace();
				BrowserErrorHandler.processError(t, connector, t.getMessage());
			}
		}
	}

	public static DeploymentPackage findDP(String name) {
		Model[] dps = dpNode.getChildren();
		for (int i=0; i<dps.length; i++) {
			if (dps[i].getName().equals(name)) {
				return (DeploymentPackage) dps[i];
			}
		}
		return null;
	}

	public void devicePropertiesChanged(RemoteDevicePropertyEvent e) throws IAgentException {
		if (e.getType() == RemoteDevicePropertyEvent.PROPERTY_CHANGED_TYPE) {
			boolean enabled = ((Boolean) e.getValue()).booleanValue();
			Object property = e.getProperty();
			if (Capabilities.DEPLOYMENT_SUPPORT.equals(property)) {
				if (enabled) {
					supportDP = true;
					initModel(new NullProgressMonitor());
				} else {
					supportDP = false;
					try {
						manager.removeRemoteDPListener(this);
					} catch (IAgentException ex) {
						ex.printStackTrace();
					}
					dpNode.removeChildren();
					parent.removeElement(dpNode);
					dpNode = null;
				}
			}
		}
	}

}