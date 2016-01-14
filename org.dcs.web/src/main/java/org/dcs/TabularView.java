package org.dcs;

import com.vaadin.annotations.AutoGenerated;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.VerticalLayout;

public class TabularView extends CustomComponent {

	/*- VaadinEditorProperties={"grid":"RegularGrid,20","showGrid":true,"snapToGrid":true,"snapToObject":true,"movingGuides":false,"snappingDistance":10} */

	@AutoGenerated
	private VerticalLayout mainLayout;
	@AutoGenerated
	private TabSheet gridViewTabs;
	@AutoGenerated
	private Panel controlPanel;
	@AutoGenerated
	private HorizontalLayout controlPanelHLayout;
	@AutoGenerated
	private Button localBtn;
	/**
	 * The constructor should first build the main layout, set the
	 * composition root and then do any custom initialization.
	 *
	 * The constructor will not be automatically regenerated by the
	 * visual editor.
	 */
	public TabularView() {
		buildMainLayout();
		setCompositionRoot(mainLayout);

		// TODO add user code here
	}

	@AutoGenerated
	private VerticalLayout buildMainLayout() {
		// common part: create layout
		mainLayout = new VerticalLayout();
		mainLayout.setImmediate(false);
		mainLayout.setWidth("100%");
		mainLayout.setHeight("100%");
		mainLayout.setMargin(false);
		
		// top-level component properties
		setWidth("100.0%");
		setHeight("100.0%");
		
		// controlPanel
		controlPanel = buildControlPanel();
		mainLayout.addComponent(controlPanel);
		mainLayout.setComponentAlignment(controlPanel, new Alignment(48));
		
		// gridViewTabs
		gridViewTabs = new TabSheet();
		gridViewTabs.setImmediate(false);
		gridViewTabs.setWidth("100.0%");
		gridViewTabs.setHeight("100.0%");
		mainLayout.addComponent(gridViewTabs);
		mainLayout.setExpandRatio(gridViewTabs, 1.0f);
		mainLayout.setComponentAlignment(gridViewTabs, new Alignment(48));
		
		return mainLayout;
	}

	@AutoGenerated
	private Panel buildControlPanel() {
		// common part: create layout
		controlPanel = new Panel();
		controlPanel.setImmediate(false);
		controlPanel.setWidth("100.0%");
		controlPanel.setHeight("70px");
		
		// controlPanelHLayout
		controlPanelHLayout = buildControlPanelHLayout();
		controlPanel.setContent(controlPanelHLayout);
		
		return controlPanel;
	}

	@AutoGenerated
	private HorizontalLayout buildControlPanelHLayout() {
		// common part: create layout
		controlPanelHLayout = new HorizontalLayout();
		controlPanelHLayout.setImmediate(false);
		controlPanelHLayout.setWidth("100.0%");
		controlPanelHLayout.setHeight("100.0%");
		controlPanelHLayout.setMargin(true);
		
		// localBtn
		localBtn = new Button();
		localBtn.setCaption("load");
		localBtn.setImmediate(true);
		localBtn.setWidth("-1px");
		localBtn.setHeight("30px");
		controlPanelHLayout.addComponent(localBtn);
		controlPanelHLayout.setComponentAlignment(localBtn, new Alignment(33));
		
		return controlPanelHLayout;
	}

}