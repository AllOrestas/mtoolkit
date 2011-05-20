/*******************************************************************************
 * Copyright (c) 2011 ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package org.tigris.mtoolkit.maven.internal.installation;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.resource.ImageDescriptor;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.project.IMavenProjectFacade;
import org.tigris.mtoolkit.common.installation.InstallationItem;
import org.tigris.mtoolkit.common.installation.InstallationItemProvider;
import org.tigris.mtoolkit.maven.internal.images.ImageHolder;
import org.tigris.mtoolkit.maven.launching.MavenProcess;

import com.prosyst.tools.maven.MavenUtils;
import com.prosyst.tools.maven.internal.MavenCorePlugin;

public class InstallationProvider implements InstallationItemProvider {

  public boolean isCapable(Object resource) {
    resource = adaptItem(resource);
    if (resource instanceof IFile)
      return isFileSupported((IFile) resource);
    if (resource instanceof IMavenProjectFacade)
      return isMavenProjectSupported((IMavenProjectFacade) resource);
    return false;
  }

  private Object adaptItem(Object resource) {
    if (resource instanceof IMavenProjectFacade)
      return resource;
    if (resource instanceof IResource) {
      Object adapted = adaptResource((IResource) resource);
      if (adapted != null)
        return adapted;
    }
    if (resource instanceof IAdaptable) {
      Object adapted = ((IAdaptable) resource).getAdapter(IResource.class);
      if (adapted != null)
        adapted = adaptResource((IResource) adapted);
      if (adapted != null)
        return adapted;
    }
    return resource;
  }

  private Object adaptResource(IResource resource) {
    if (resource.getType() == IResource.PROJECT) {
      IProject project = (IProject) resource;
      IMavenProjectFacade facade = MavenPlugin.getDefault().getMavenProjectManager().getProject(project);
      if (facade != null)
        return facade;
      resource = getPomFileFromContainer(project);
    }
    if (resource.getType() == IResource.FOLDER)
      resource = getPomFileFromContainer((IContainer) resource);
    if (resource.getType() == IResource.FILE)
      return resource;
    return null;
  }

  private IFile getPomFileFromContainer(IContainer project) {
    IFile pomFile = project.getFile(new Path("pom.xml"));
    return pomFile;
  }

  private boolean isFileSupported(IFile file) {
    if (!file.isAccessible())
      return false;
    return file.getName().equals("pom.xml");
  }

  private boolean isMavenProjectSupported(IMavenProjectFacade facade) {
    if (!facade.getPackaging().equals("pom"))
      return true;
    return false;
  }

  public InstallationItem getInstallationItem(Object resource) {
    resource = adaptItem(resource);
    if (resource instanceof IFile)
      return new FileItem(this, (IFile) resource);
    else if (resource instanceof IMavenProjectFacade)
      return new ProjectItem(this, (IMavenProjectFacade) resource);
    else
      return null;
  }

  @SuppressWarnings("rawtypes")
  public IStatus prepareItems(List items, Map properties, IProgressMonitor monitor) {
    @SuppressWarnings("unchecked")
    List<InstallationItem> castedItems = items;
    if (monitor == null)
      monitor = new NullProgressMonitor();
    monitor.beginTask("Preparing installation items...", items.size());
    try {
      for (InstallationItem item : castedItems) {
        if (item instanceof BaseItem) {
          monitor.subTask(((BaseItem) item).getDisplayName());
          IStatus status = prepareItem((BaseItem) item, properties, monitor);
          if (status.matches(IStatus.ERROR))
            return status;
        }
        monitor.worked(1);
      }
    } finally {
      monitor.done();
    }
    return Status.OK_STATUS;
  }

  private IStatus prepareItem(BaseItem item, Map properties, IProgressMonitor monitor) {
    File pomFile = item.getPomLocationAtFilesystem();
    if (pomFile == null || !pomFile.exists())
      return MavenCorePlugin.newStatus(IStatus.ERROR, "Cannot find pom.xml for " + item.getDisplayName(), null);
    try {
      MavenProcess.launchDefaultBuild(pomFile.getParentFile(), monitor);
      File artifact = MavenUtils.locateMavenArtifact(pomFile);
      if (!artifact.exists())
        return MavenCorePlugin.newStatus(IStatus.ERROR, "Unable to find Maven artifact at expected location: "
            + artifact.getAbsolutePath(), null);
      item.setGeneratedArtifact(artifact);
      IStatus status = item.completePrepare(monitor, properties);
      if (status.matches(IStatus.ERROR))
        return status;
      else if (!status.isOK())
        MavenCorePlugin.log(status);
      return Status.OK_STATUS;
    } catch (CoreException e) {
      return MavenCorePlugin.newStatus(IStatus.ERROR, "Unable to execute Maven build for " + item.getDisplayName(), e);
    }
  }

  public void init(IConfigurationElement element) throws CoreException {
  }

  public String getName() {
    return "Maven projects provider";
  }

  public ImageDescriptor getImageDescriptor() {
    return ImageHolder.getImageDescriptor(ImageHolder.POM_ICON);
  }
}
