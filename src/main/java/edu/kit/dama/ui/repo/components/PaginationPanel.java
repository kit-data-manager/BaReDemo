/*
 * Copyright 2014 Karlsruhe Institute of Technology.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.kit.dama.ui.repo.components;

import com.vaadin.event.LayoutEvents;
import com.vaadin.server.ThemeResource;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Link;
import com.vaadin.ui.NativeButton;
import com.vaadin.ui.VerticalLayout;
import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.commons.types.DigitalObjectId;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.ui.repo.MyVaadinUI;
import edu.kit.dama.ui.repo.util.DigitalObjectPersistenceHelper;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pagination panel component used to render and navigate through search
 * results.
 *
 * @author mf6319
 */
public class PaginationPanel extends CustomComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaginationPanel.class);

    private VerticalLayout mainLayout;
    private final VerticalLayout pageLayout = new VerticalLayout();
    private HorizontalLayout navigation = new HorizontalLayout();
    private final ShareObjectComponent shareComponent = new ShareObjectComponent();

    //special mode where only the entries that are not ingested yet are listed. In this mode, editing is not possible.
    private int currentPage = 0;
    private int overallPages = 0;
    //could be customizable
    private final int entriesPerPage = 10;
    private final MyVaadinUI parent;
    private final List<DigitalObjectId> allEntries = new LinkedList<>();

    /**
     * Default constructor.
     *
     * @param pParent The parent used to obtain the authorization context.
     */
    public PaginationPanel(MyVaadinUI pParent) {
        parent = pParent;
        buildMainLayout();
        setCompositionRoot(mainLayout);
    }

    /**
     * Set the list of all elements which can be rendered.
     *
     * @param pObjects All digital object ids.
     */
    public final void setAllEntries(List<DigitalObjectId> pObjects) {
        allEntries.clear();
        allEntries.addAll(pObjects);
        overallPages = allEntries.size() / entriesPerPage;
        overallPages += (allEntries.size() % entriesPerPage > 0) ? 1 : 0;
        currentPage = 0;
        updatePage();
    }

    /**
     * Get the parent UI.
     *
     * @return The parent UI.
     */
    public final MyVaadinUI getParentUI() {
        return parent;
    }

    @Override
    protected final void setCompositionRoot(Component compositionRoot) {
        super.setCompositionRoot(compositionRoot);
    }

    /**
     * Open the popup used to share access to the provided object.
     *
     * @param pObject The object to share.
     */
    protected final void showSharingPopup(DigitalObject pObject) {
        shareComponent.setup(pObject);
        shareComponent.getPopupView().setPopupVisible(true);
    }

    /**
     * Build the main layout.
     */
    private void buildMainLayout() {
        mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        pageLayout.setSizeFull();
        pageLayout.setSpacing(true);
        mainLayout.addComponent(pageLayout);
        mainLayout.addComponent(navigation);
        mainLayout.setComponentAlignment(pageLayout, Alignment.MIDDLE_CENTER);
        mainLayout.setComponentAlignment(navigation, Alignment.BOTTOM_CENTER);
        mainLayout.setExpandRatio(pageLayout, 1.0f);
        mainLayout.setExpandRatio(navigation, .1f);
        mainLayout.addComponent(shareComponent.getPopupView());
        mainLayout.setComponentAlignment(shareComponent.getPopupView(), Alignment.MIDDLE_CENTER);
    }

    /**
     * Update the currently rendered page.
     */
    private void updatePage() {
        pageLayout.removeAllComponents();
        List<DigitalObjectId> objectsOnPage = allEntries.subList(currentPage * entriesPerPage, Math.min(currentPage * entriesPerPage + entriesPerPage, allEntries.size()));
        int cnt = 0;
        IAuthorizationContext ctx = parent.getAuthorizationContext();
        while (cnt < entriesPerPage) {
            if (objectsOnPage.size() > cnt) {
                DigitalObject entry = null;
                String entryId = null;
                try {
                    entryId = objectsOnPage.get(cnt).getStringRepresentation().trim();
                    entry = DigitalObjectPersistenceHelper.getDigitalObjectByIdentifier(entryId, ctx);
                } catch (Exception ex) {
                    //do nothing, entry stays null
                    LOGGER.warn("Failed to get object by id '" + entryId + "'. Adding error placeholder component.", ex);
                }
                if (entry == null) {
                    entry = new DigitalObject();
                    entry.setDigitalObjectId(new DigitalObjectId(entryId));
                    entry.setLabel(EntryRenderPanel.ERROR_PLACEHOLDER);
                }
                pageLayout.addComponent(new EntryRenderPanel(this, entry, ctx));
            }
            cnt++;
        }

        //update navigation
        if (!objectsOnPage.isEmpty()) {
            HorizontalLayout newNavigation = buildNavigationComponent();
            mainLayout.replaceComponent(navigation, newNavigation);
            navigation = newNavigation;
        } else {
            String noResultsMessage = "<h2>No objects found for the provided search criteria.</h2>";
            HorizontalLayout newNavigation = new HorizontalLayout(new Label(noResultsMessage, ContentMode.HTML));
            mainLayout.replaceComponent(navigation, newNavigation);
            navigation = newNavigation;
        }
    }

    /**
     * Build the navigation layout including the appropriate buttons to navigate
     * through the pagination pages.
     *
     * @return The navigation layout component.
     */
    private HorizontalLayout buildNavigationComponent() {
        HorizontalLayout result = new HorizontalLayout();
        //add "JumpToFirstPage" button
        final NativeButton first = new NativeButton();
        first.setIcon(new ThemeResource("img/16x16/beginning.png"));
        first.setDescription("First Page");
        first.addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(Button.ClickEvent event) {
                currentPage = 0;
                updatePage();
            }
        });
        //add "PreviousPage" button
        final NativeButton prev = new NativeButton();
        prev.setIcon(new ThemeResource("img/16x16/prev.png"));
        prev.setDescription("Previous Page");
        prev.addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (currentPage > 0) {
                    currentPage--;
                    updatePage();
                }
            }
        });
        //add "NextPage" button
        final NativeButton next = new NativeButton();
        next.setDescription("Next Page");
        next.setIcon(new ThemeResource("img/16x16/next.png"));
        next.addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (currentPage + 1 < overallPages) {
                    currentPage++;
                    updatePage();
                }
            }
        });
        //add "JumpToLastPage" button
        final NativeButton last = new NativeButton();
        last.setDescription("Last Page");
        last.setIcon(new ThemeResource("img/16x16/end.png"));
        last.addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(Button.ClickEvent event) {
                currentPage = overallPages - 1;
                updatePage();
            }
        });

        //enable/disable buttons depending on the current page
        if (overallPages == 0) {
            first.setEnabled(false);
            prev.setEnabled(false);
            next.setEnabled(false);
            last.setEnabled(false);
        } else {
            first.setEnabled(!(currentPage == 0) || !(overallPages < 2));
            prev.setEnabled(!(currentPage == 0) || !(overallPages < 2));
            next.setEnabled(!(currentPage == overallPages - 1) || !(overallPages < 2));
            last.setEnabled(!(currentPage == overallPages - 1) || !(overallPages < 2));
        }

        Label leftFiller = new Label();
        result.addComponent(leftFiller);
        result.setExpandRatio(leftFiller, 1.0f);
        result.addComponent(first);
        result.addComponent(prev);
        int start = currentPage - 5;
        start = (start < 0) ? 0 : start;
        int end = start + 10;
        end = (end > overallPages) ? overallPages : end;

        if (end - start < 10 && overallPages > 10) {
            start = end - 10;
        }

        if (overallPages == 0) {
            Label noEntryLabel = new Label("<i>No entries</i>", ContentMode.HTML);
            //noEntryLabel.setWidth("80px");
            noEntryLabel.setSizeUndefined();
            result.addComponent(noEntryLabel);
            result.setComponentAlignment(noEntryLabel, Alignment.MIDDLE_CENTER);
        }
        //build the actual page entries
        for (int i = start; i < end; i++) {
            if (i == currentPage) {
                //the current page is marked with a special style
                Label pageLink = new Label("<b>" + Integer.toString(i + 1) + "</b>", ContentMode.HTML);
                pageLink.setStyleName("currentpage");
                pageLink.setWidth("15px");
                result.addComponent(pageLink);
                result.setComponentAlignment(pageLink, Alignment.MIDDLE_CENTER);
            } else {
                //otherwise normal links are added, click-events are handled via LayoutClickListener 
                Link pageLink = new Link(Integer.toString(i + 1), null);
                result.addComponent(pageLink);
                result.setComponentAlignment(pageLink, Alignment.MIDDLE_CENTER);
            }
        }
        //add right navigation buttons
        result.addComponent(next);
        result.addComponent(last);
        //...and fill the remaining space 
        Label rightFiller = new Label();
        result.addComponent(rightFiller);
        result.setExpandRatio(rightFiller, 1.0f);
        result.setSpacing(true);

        //put everything ot the middle
        result.setComponentAlignment(first, Alignment.MIDDLE_CENTER);
        result.setComponentAlignment(prev, Alignment.MIDDLE_CENTER);
        result.setComponentAlignment(next, Alignment.MIDDLE_CENTER);
        result.setComponentAlignment(last, Alignment.MIDDLE_CENTER);

        //add layout click listener to be able to navigate by clicking the single pages
        result.addLayoutClickListener(new LayoutEvents.LayoutClickListener() {
            @Override
            public void layoutClick(LayoutEvents.LayoutClickEvent event) {

                // Get the child component which was clicked
                Component child = event.getChildComponent();

                if (child == null) {
                    // Not over any child component
                } else // Over a child component
                {
                    if (child instanceof Link) {
                    }
                }
            }
        });

        //finalize
        result.setWidth("100%");
        result.setHeight("25px");
        return result;
    }
}
