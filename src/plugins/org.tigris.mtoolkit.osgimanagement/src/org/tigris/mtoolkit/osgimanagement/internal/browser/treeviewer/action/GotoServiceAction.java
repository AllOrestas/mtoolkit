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
package org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.action;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.actions.SelectionProviderAction;
import org.tigris.mtoolkit.iagent.RemoteService;
import org.tigris.mtoolkit.osgimanagement.IStateAction;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.Bundle;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameworkImpl;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.ObjectClass;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.ServicesCategory;
import org.tigris.mtoolkit.osgimanagement.model.Framework;
import org.tigris.mtoolkit.osgimanagement.model.Model;

public class GotoServiceAction extends SelectionProviderAction implements IStateAction {

	public GotoServiceAction(ISelectionProvider provider, String text) {
		super(provider, text);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	public void run() {
		try {
			ObjectClass objectClass = (ObjectClass) getStructuredSelection().getFirstElement();
			RemoteService service = objectClass.getService();
			String searchName = objectClass.getName();
			if (((ServicesCategory) objectClass.getParent()).getType() == ServicesCategory.USED_SERVICES) {
				Model[] services = null;
				FrameworkImpl fw = ((FrameworkImpl) objectClass.findFramework());
				if (fw.getViewType() == Framework.BUNDLES_VIEW) {
					Bundle bundle = fw.findBundleForService(service.getServiceId());
					if (bundle == null)
						return;
					ServicesCategory category = (ServicesCategory) bundle.getChildren()[0];
					services = category.getChildren();
				} else {
					services = fw.getChildren();
				}
				for (int i = 0; i < services.length; i++) {
					if (services[i].getName().equals(searchName)) {
						StructuredSelection selection = new StructuredSelection(services[i]);
						getSelectionProvider().setSelection(new StructuredSelection(services[i]));
						if (!selection.equals(getSelectionProvider().getSelection())) {
							Object expanded[] = ((TreeViewer) getSelectionProvider()).getExpandedElements();
							try {
								((TreeViewer) getSelectionProvider()).getTree().setRedraw(false);
								((TreeViewer) getSelectionProvider()).expandAll();
								((TreeViewer) getSelectionProvider()).setExpandedElements(expanded);
							} finally {
								((TreeViewer) getSelectionProvider()).getTree().setRedraw(true);
							}
							getSelectionProvider().setSelection(selection);
						}
						return;
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.actions.SelectionProviderAction#selectionChanged(org.eclipse
	 * .jface.viewers.IStructuredSelection)
	 */
	public void selectionChanged(IStructuredSelection selection) {
		updateState(selection);
	}

	public void updateState(IStructuredSelection selection) {
		if (selection.size() == 1 && getStructuredSelection().getFirstElement() instanceof ObjectClass) {
			ObjectClass oClass = (ObjectClass) getStructuredSelection().getFirstElement();
			if ((oClass.getParent() instanceof FrameworkImpl))
				return;
			ServicesCategory category = (ServicesCategory) oClass.getParent();
			if (category.getType() == ServicesCategory.REGISTERED_SERVICES) {
				this.setEnabled(false);
			} else {
				this.setEnabled(true);
			}
		} else {
			this.setEnabled(false);
		}
	}
}
