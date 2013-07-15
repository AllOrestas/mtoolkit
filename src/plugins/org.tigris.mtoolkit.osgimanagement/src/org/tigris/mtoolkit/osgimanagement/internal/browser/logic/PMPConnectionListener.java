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
package org.tigris.mtoolkit.osgimanagement.internal.browser.logic;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.tigris.mtoolkit.console.ConsoleManager;
import org.tigris.mtoolkit.iagent.DeviceConnector;
import org.tigris.mtoolkit.iagent.IAgentException;
import org.tigris.mtoolkit.iagent.spi.ConnectionEvent;
import org.tigris.mtoolkit.iagent.spi.ConnectionListener;
import org.tigris.mtoolkit.iagent.spi.ConnectionManager;
import org.tigris.mtoolkit.iagent.spi.DeviceConnectorSpi;
import org.tigris.mtoolkit.osgimanagement.Util;
import org.tigris.mtoolkit.osgimanagement.installation.FrameworkConnectorFactory;
import org.tigris.mtoolkit.osgimanagement.internal.FrameworkPlugin;
import org.tigris.mtoolkit.osgimanagement.internal.Messages;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameworkImpl;
import org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action.ActionsManager;

public final class PMPConnectionListener implements ConnectionListener {
  private final FrameworkImpl   fw;
  private final DeviceConnector connector;
  private final String          frameworkName;
  private final boolean         autoConnected;
  private boolean               instrumenting = false;

  public PMPConnectionListener(FrameworkImpl fw, String frameworkName, DeviceConnector connector, boolean autoConnected) {
    this.fw = fw;
    this.frameworkName = frameworkName;
    this.connector = connector;
    this.autoConnected = autoConnected;
    ((DeviceConnectorSpi) connector).getConnectionManager().addConnectionListener(this);
  }

  public void connectionChanged(ConnectionEvent e) {
    if (e.getConnection().getType() != ConnectionManager.PMP_CONNECTION) {
      return;
    }
    BrowserErrorHandler
        .debug("PMP Connection Changed " + (e.getType() == ConnectionEvent.CONNECTED ? "CONNECTED" : "DISCONNECTED") + " " + connector); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    if (e.getType() == ConnectionEvent.DISCONNECTED) {
      disconnected();
    } else if (e.getType() == ConnectionEvent.CONNECTED) {
      // in case we are instrumenting the framework, we want to ignore connection events
      if (!instrumenting) {
        connected();
      }
    }
  }

  public void disconnected() {
    if (!autoConnected) {
      ActionsManager.disconnectConsole(fw);
    }
    ((DeviceConnectorSpi) connector).getConnectionManager().removeConnectionListener(this);
    fw.disconnect();
  }

  public void connected() {
    if (fw.isConnected()) {
      return;
    }
    Job connectJob = new Job(frameworkName) {
      /* (non-Javadoc)
       * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
       */
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        SubMonitor sMonitor = SubMonitor.convert(monitor, FrameworkConnectorFactory.CONNECT_PROGRESS);
        try {
          sMonitor.setTaskName("Connecting " + frameworkName);
          SubMonitor connectMonitor = sMonitor.newChild(FrameworkConnectorFactory.CONNECT_PROGRESS_CONNECTING);
          try {
            // force creation of pmp connection
            if (!connector.getVMManager().isVMActive()) {
              return Util.newStatus(IStatus.ERROR, "Connection failed", null);
            }
          } catch (IAgentException e) {
            return Util.newStatus(IStatus.ERROR, "Connection failed", e);
          }

          try {
            if (!connector.getVMManager().isVMInstrumented(false)) {
              if (shouldInstallIAgent()) {
                try {
                  instrumenting = true;
                  connector.getVMManager().instrumentVM();
                } catch (IAgentException iae) {
                  if (monitor.isCanceled()) {
                    return Status.CANCEL_STATUS;
                  }
                  return new Status(IStatus.ERROR, FrameworkPlugin.PLUGIN_ID, "Unable to instrument VM.", iae);
                } finally {
                  instrumenting = false;
                }
              } else {
                return Status.OK_STATUS;
              }
            }
          } catch (IAgentException e) {
            return Util.newStatus(IStatus.ERROR, "Connection failed", e);
          }
          connectMonitor.worked(FrameworkConnectorFactory.CONNECT_PROGRESS_CONNECTING);
          fw.connect(connector, sMonitor);
          if (!autoConnected && fw.isConnected()) {
            ConsoleManager.connectConsole(connector, fw.getName(), fw.getFrameworkID());
          }
          return Status.OK_STATUS;
        } finally {
          if (!fw.isConnected() && fw.isAutoConnected()) {
            fw.dispose();
          }
          sMonitor.done();
        }
      }
    };
    connectJob.schedule();
  }

  public DeviceConnector getConnector() {
    return connector;
  }

  private boolean shouldInstallIAgent() {
    final Display display = PlatformUI.getWorkbench().getDisplay();
    final Boolean result[] = new Boolean[1];
    display.syncExec(new Runnable() {
      public void run() {
        Shell shell = display.getActiveShell();
        boolean install = MessageDialog.openQuestion(shell, Messages.framework_not_instrumented,
            Messages.framework_not_instrumented_msg);
        result[0] = new Boolean(install);
      }
    });
    return result[0] != null && result[0].booleanValue();
  }
}
