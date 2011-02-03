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

import org.maven.ide.eclipse.project.IMavenProjectFacade;

public class ProjectItem extends BaseItem {

	protected IMavenProjectFacade facade;

	protected ProjectItem(InstallationProvider provider, IMavenProjectFacade facade) {
		super(provider);
		this.facade = facade;
	}

	@Override
	public File getPomLocationAtFilesystem() {
		return facade.getPomFile();
	}

	@Override
	public String getDisplayName() {
		return "workspace project " + facade.getProject().getName();
	}
}