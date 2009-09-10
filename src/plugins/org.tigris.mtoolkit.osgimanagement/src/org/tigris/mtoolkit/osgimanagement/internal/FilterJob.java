package org.tigris.mtoolkit.osgimanagement.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.tigris.mtoolkit.osgimanagement.browser.model.Model;
import org.tigris.mtoolkit.osgimanagement.browser.model.SimpleNode;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.ContentChangeEvent;
import org.tigris.mtoolkit.osgimanagement.internal.browser.logic.ContentChangeListener;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.FrameWork;
import org.tigris.mtoolkit.osgimanagement.internal.browser.model.TreeRoot;
import org.tigris.mtoolkit.osgimanagement.internal.browser.treeviewer.logic.ViewContentProvider;

public class FilterJob extends Job{
	private TreeViewer tree;
	private List treeItemsMatchingFilter = new ArrayList();
	private List expandedElements = new ArrayList();
	private MyViewerFilter filter;
	
	public FilterJob(TreeViewer tree) {
		super("FilterJob");
		this.tree = tree;
		((TreeRoot)tree.getInput()).addListener(new ContentChangeListener() {
			public void elementRemoved(ContentChangeEvent event) {
				expandedElements.remove(event.getTarget());
				clearExpandedElements();
			}
			public void elementChanged(ContentChangeEvent event) {
				schedule(400);
			}
			public void elementAdded(ContentChangeEvent event) {
				schedule(400);
			}
		});
		tree.addTreeListener(new ITreeViewerListener() {
			public void treeExpanded(TreeExpansionEvent event) {
				expandedElements.add(event.getElement());
			}
			
			public void treeCollapsed(TreeExpansionEvent event) {
				expandedElements.remove(event.getElement());
			}
		});
	}

	protected IStatus run(IProgressMonitor monitor) {
		Display display = Display.getDefault();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(new Runnable() {
				public void run() {
					try {
						ViewContentProvider contentProvider = (ViewContentProvider) tree.getContentProvider();
						treeItemsMatchingFilter.clear();
						childrenGlobal.clear();

						List parents = new ArrayList();
						List visibleElements = new ArrayList();
						
						findAllMatchingItems(contentProvider.getChildren(FrameWorkView.treeRoot), contentProvider);
						if (FrameWorkView.getFilter().equals("")) {
							visibleElements.addAll(treeItemsMatchingFilter);
							visibleElements.addAll(Arrays.asList(contentProvider.getChildren(FrameWorkView.treeRoot)));
						} else {
							for (int i = 0; i < treeItemsMatchingFilter.size(); i++) {
								Object parentElement = contentProvider.getParent(treeItemsMatchingFilter.get(i));
								while (parentElement != FrameWorkView.treeRoot) {
									if (!parents.contains(parentElement))
										parents.add(parentElement);
									parentElement = contentProvider.getParent(parentElement);
								}
							}
							List tmp = removeNodesWhichChildsAreNotInterested(treeItemsMatchingFilter);
							treeItemsMatchingFilter.removeAll(tmp);
							findChildren(treeItemsMatchingFilter.toArray(), contentProvider);
							treeItemsMatchingFilter.addAll(tmp);

							visibleElements.addAll(treeItemsMatchingFilter);
							visibleElements.addAll(parents);
							visibleElements.addAll(childrenGlobal);
						}
						ISelection selection = tree.getSelection();
						tree.getTree().setRedraw(false);
						tree.getTree().setLayoutDeferred(true);
						tree.collapseAll();

						if (filter == null)
							filter = new MyViewerFilter();
						tree.removeFilter(filter);
						filter.setInput(visibleElements);
						tree.addFilter(filter);

						tree.setExpandedElements(expandedElements.toArray());
						tree.setSelection(selection);
					} finally {
						tree.getTree().setRedraw(true);
						tree.getTree().setLayoutDeferred(false);
					}
				}
			});
		}
		return Status.OK_STATUS;
	}

	
	private List removeNodesWhichChildsAreNotInterested(List treeItemsMatchingFilter) {
		List result = new ArrayList();
		for (int i = 0; i < treeItemsMatchingFilter.size(); i++) {
			Object element = treeItemsMatchingFilter.get(i);
			if (element instanceof FrameWork || element instanceof SimpleNode)
				result.add(element);
		}
		return result;
	}

	private void findAllMatchingItems(Object[] parents, ViewContentProvider provider) {
		for (int i = 0; i < parents.length; i++) {
			if (parents[i].toString().toLowerCase().indexOf(FrameWorkView.getFilter().toLowerCase()) != -1 || FrameWorkView.getFilter().equals("")) {
				treeItemsMatchingFilter.add(parents[i]);	
			}
			Object[] children = provider.getChildren(parents[i]);
			if (children == null || children.length == 0) {
				continue;
			}
			
			for (int j = 0; j < children.length; j++) {
				if (children[j] instanceof Model
								&& ((Model) children[j]).getName().toLowerCase().indexOf(FrameWorkView.getFilter().toLowerCase()) != -1
								|| FrameWorkView.getFilter().equals("")) {
					treeItemsMatchingFilter.add(children[j]);
				}
			}
			findAllMatchingItems(children, provider);
		}
	}
	
	private void clearExpandedElements() {
		for (int i=expandedElements.size()-1; i>=0; i--) {
			Model node = (Model) expandedElements.get(i);
			while (node.getParent() != null && !(node.getParent() instanceof TreeRoot)) {
				node = node.getParent();
			}
			if (node == null || node.getParent() == null) {
				expandedElements.remove(i);
			}
		}
	}


	private List childrenGlobal = new ArrayList();

	private void findChildren(Object[] parents, ViewContentProvider provider) {
		for (int i = 0; i < parents.length; i++) {
			Object[] children = provider.getChildren(parents[i]);

			if (children == null || children.length == 0)
				continue;

			childrenGlobal.addAll(Arrays.asList(children));

			findChildren(children, provider);
		}
	}

	public void removeFilter() {
		if(filter != null)
			tree.removeFilter(filter);
	}

}
