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
package org.tigris.mtoolkit.common.installation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.tigris.mtoolkit.common.UtilitiesPlugin;
import org.tigris.mtoolkit.common.certificates.CertUtils;

public class WorkspaceFileItem implements InstallationItem {

  protected IFile  file;
  protected String mimeType;
  protected File   signedFile;

  public WorkspaceFileItem(IFile file, String mimeType) {
    this.file = file;
    this.mimeType = mimeType;
  }

  public InputStream getInputStream() throws IOException {
    try {
      if (signedFile != null) {
        return new FileInputStream(signedFile);
      }
      return file.getContents();
    } catch (CoreException e) {
      UtilitiesPlugin.error(NLS.bind("Failed to retrieve contents of file: {0}", file.getFullPath()), e);
      return null;
    }
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getName() {
    return file.getName();
  }

  public IFile getFile() {
    return file;
  }

  public IStatus prepare(IProgressMonitor monitor, Map properties) {
    try {
      file.refreshLocal(IFile.DEPTH_ZERO, monitor);
      signedFile = CertUtils.signJar(file.getLocation().toFile(), monitor, properties);
    } catch (CoreException e) {
      return UtilitiesPlugin.newStatus(IStatus.ERROR, "Failed to prepare file for installation", e);
    } catch (IOException ioe) {
      return UtilitiesPlugin.newStatus(IStatus.ERROR, "Failed to prepare file for installation", ioe);
    }
    return Status.OK_STATUS;
  }

  public void dispose() {
    if (signedFile != null) {
      signedFile.delete();
      signedFile = null;
    }
  }

  public Object getAdapter(Class adapter) {
    if (adapter.equals(IResource.class)) {
      return file;
    } else {
      return null;
    }
  }
}
