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

import com.vaadin.server.FileDownloader;
import com.vaadin.server.Page;
import com.vaadin.server.StreamResource;
import com.vaadin.server.StreamResource.StreamSource;
import com.vaadin.server.ThemeResource;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.AbstractOrderedLayout;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.NativeButton;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.BaseTheme;
import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.authorization.entities.Role;
import edu.kit.dama.authorization.entities.impl.AuthorizationContext;
import edu.kit.dama.authorization.exceptions.EntityNotFoundException;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.authorization.services.administration.ResourceServiceLocal;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.base.DigitalObjectType;
import edu.kit.dama.mdm.base.Investigation;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import edu.kit.dama.mdm.dataorganization.entity.core.IAttribute;
import edu.kit.dama.mdm.dataorganization.entity.core.IFileNode;
import edu.kit.dama.ui.commons.util.UIUtils7;
import edu.kit.dama.ui.repo.MyVaadinUI;
import edu.kit.dama.ui.repo.util.DigitalObjectPersistenceHelper;
import edu.kit.dama.ui.repo.util.DigitalObjectTypeHelper;
import edu.kit.dama.ui.repo.util.ElasticsearchHelper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render panel for a single entry in the {@link PaginationPanel}. The panel
 * contains everything to visualize a digital object and to modify its content.
 *
 * @author mf6319
 */
public class EntryRenderPanel extends CustomComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntryRenderPanel.class);

    public final static String ERROR_PLACEHOLDER = "error";

    private AbstractOrderedLayout mainLayout;
    private final PaginationPanel parent;
    private GridLayout dcLayout;
    private HorizontalLayout miscActionLayout;
    private HorizontalLayout editActionLayout;
    private TextField titleField;
    private Label titleLabel;
    private Label creatorLabel;
    private Label creationLabel;
    private Label objectIdLabel;
    private TextArea descriptionArea;
    private Label descriptionLabel;
    private NativeButton downloadButton;
    private NativeButton shareButton;
    private NativeButton editButton;
    private NativeButton starButton;
    private NativeButton saveEditButton;
    private NativeButton cancelEditButton;
    private Image typeImage;
    private boolean editMode = false;
    private DigitalObject object;

    /**
     * Default constructor.
     *
     * @param pParent The parent component.
     * @param pObject The entry to render.
     * @param pContext The authorization context obtained from the main app.
     */
    public EntryRenderPanel(PaginationPanel pParent, DigitalObject pObject, IAuthorizationContext pContext) {
        parent = pParent;
        object = pObject;
        buildMainLayout(pContext);
        setCompositionRoot(mainLayout);
    }

    @Override
    protected final void setCompositionRoot(Component compositionRoot) {
        super.setCompositionRoot(compositionRoot); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Build the main layout of the representation of one digital object.
     *
     * @param pContext The authorization context used to decide whether special
     * administrative features are available or not.
     */
    private void buildMainLayout(IAuthorizationContext pContext) {
        //check if the object could be obtained or not, e.g. due to missing permissions. If not, show an error message.
        if (ERROR_PLACEHOLDER.equals(object.getLabel())) {
            Label warnLabel = new Label("<h3>Failed to obtain entry with identifier '" + object.getDigitalObjectIdentifier() + "' from database.</h3>", ContentMode.HTML);
            final Button cleanup = new Button("Cleanup");
            cleanup.setDescription("Click to remove object from search index.");
            cleanup.addClickListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    cleanup.setEnabled(false);
                    new Notification("Information",
                            "Cleanup not implemented, yet.", Notification.Type.TRAY_NOTIFICATION).show(Page.getCurrent());
                }
            });
            if (pContext.getRoleRestriction().atLeast(Role.ADMINISTRATOR)) {
                //show cleanup button
                mainLayout = new HorizontalLayout(warnLabel, cleanup);
            } else {
                //no cleanup available
                mainLayout = new HorizontalLayout(warnLabel);
            }
            mainLayout.setSizeFull();
            return;
        }

        //initialize image field
        typeImage = new Image();
        typeImage.setSizeFull();
        setImage(object);

        //initialize title label/field
        titleField = UIUtils7.factoryTextField(null, "dc:title");
        titleField.addStyleName("basic_title");
        titleLabel = new Label("dc:title");
        titleLabel.setWidth("100%");
        titleLabel.addStyleName("basic_title");

        //initialize creator label
        if (object.getUploader() != null) {
            creatorLabel = new Label(StringUtils.abbreviate(object.getUploader().getFullname(), 100));
            creatorLabel.setEnabled(true);
        } else {
            creatorLabel = new Label("dc:creator");
            creatorLabel.setEnabled(false);
        }
        creatorLabel.setWidth("100%");
        creatorLabel.addStyleName("basic_left");

        //initialize creation label
        if (object.getStartDate() != null) {
            creationLabel = new Label(new SimpleDateFormat().format(object.getStartDate()));
            creationLabel.setEnabled(true);
        } else {
            creationLabel = new Label("dc:date");
            creationLabel.setEnabled(false);
        }

        creationLabel.setWidth("100%");

        //initialize identifier label
        objectIdLabel = new Label(StringUtils.abbreviate(object.getDigitalObjectIdentifier(), 100));
        objectIdLabel.setWidth("100%");
        //initialize description label/area
        descriptionLabel = new Label("dc:description");
        descriptionArea = UIUtils7.factoryTextArea(null, "dc:description");
        descriptionArea.setHeight("50px");
        descriptionLabel.setHeight("50px");
        descriptionLabel.setWidth("100%");

        //action buttons
        downloadButton = new NativeButton("Download");
        downloadButton.setIcon(new ThemeResource("img/32x32/download.png"));
        downloadButton.setStyleName(BaseTheme.BUTTON_LINK);
        downloadButton.setDescription("Download the data of this digital object.");
        downloadButton.setWidth("100%");

        shareButton = new NativeButton("Share");
        shareButton.setIcon(new ThemeResource("img/16x16/share.png"));
        shareButton.setStyleName(BaseTheme.BUTTON_LINK);
        shareButton.setDescription("Share this digital object.");
        Role eligibleRole = Role.GUEST;
        if (parent.getParentUI().isUserLoggedIn()) {
            //obtain role only if a user is logged in and we are not in ingest mode.
            //Otherwise, the dummy context of MyVaadinUI would be used and will cause unwanted access.
            try {
                //Determine eligible role of currently logged in user
                eligibleRole = ResourceServiceLocal.getSingleton().getGrantRole(object.getSecurableResourceId(), parent.getParentUI().getAuthorizationContext().getUserId(), AuthorizationContext.factorySystemContext());
            } catch (EntityNotFoundException | UnauthorizedAccessAttemptException ex) {
                LOGGER.warn("Failed to determine eligable role for context " + parent.getParentUI().getAuthorizationContext() + ". Continue with GUEST permissions.", ex);
            }
        }

        //Update share button depending on role. Only possessing the role MANAGER (being the owner) entitles to share an object. 
        if (eligibleRole.atLeast(Role.MANAGER)) {
            shareButton.setEnabled(true);
            shareButton.addClickListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    if (parent != null) {
                        parent.showSharingPopup(object);
                    }
                }
            });
        } else {
            shareButton.setEnabled(false);
            shareButton.setDescription("Only the object owner is allowed to change sharing information.");
        }

        editButton = new NativeButton("Edit Metadata");
        editButton.setIcon(new ThemeResource("img/16x16/edit.png"));
        editButton.setStyleName(BaseTheme.BUTTON_LINK);
        editButton.setDescription("Edit this digital object's metadata.");

        //Update edit button depending on role. If the object is shared with or owned by the logged in user, editing will be allowed.
        if (eligibleRole.atLeast(Role.MEMBER)) {
            editButton.setEnabled(true);
            editButton.addClickListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    switchEditMode();
                }
            });
        } else {
            editButton.setEnabled(false);
            editButton.setDescription("Only the object owner and users the object is shared with are allowed to change metadata information.");
        }

        starButton = new NativeButton("Favorite");
        starButton.setImmediate(true);
        starButton.setIcon(new ThemeResource("img/16x16/unstarred.png"));
        starButton.setStyleName(BaseTheme.BUTTON_LINK);
        starButton.setDescription("Add/remove digital object to/from favorites.");

        //Update star button depending on role. If the object is shared with or owned by the logged in user, "star'ing" will be allowed.
        if (eligibleRole.atLeast(Role.MEMBER)) {
            starButton.setEnabled(true);
            starButton.addClickListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
                    mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());

                    try {
                        DigitalObjectType favoriteType = mdm.findSingleResult("SELECT t FROM DigitalObjectType t WHERE t.identifier='" + MyVaadinUI.FAVORITE_TYPE_IDENTIFIER + "' AND t.typeDomain='" + MyVaadinUI.FAVORITE_TYPE_DOMAIN + "'", DigitalObjectType.class);
                        if (DigitalObjectTypeHelper.isTypeAssignedToObject(object, favoriteType, AuthorizationContext.factorySystemContext())) {
                            //remove favorite status
                            DigitalObjectTypeHelper.removeTypeFromObject(object, favoriteType, AuthorizationContext.factorySystemContext());
                            starButton.setIcon(new ThemeResource("img/16x16/unstarred.png"));
                            new Notification("Information",
                                    "Successfully removed favorite tag from object " + object.getDigitalObjectIdentifier() + ".", Notification.Type.TRAY_NOTIFICATION).show(Page.getCurrent());
                        } else {
                            //assign favorite status
                            DigitalObjectTypeHelper.assignTypeToObject(object, favoriteType, AuthorizationContext.factorySystemContext());
                            starButton.setIcon(new ThemeResource("img/16x16/starred.png"));
                            new Notification("Information",
                                    "Successfully added favorite tag to object " + object.getDigitalObjectIdentifier() + ".", Notification.Type.TRAY_NOTIFICATION).show(Page.getCurrent());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to change 'favorite' status of digital object.", e);
                        new Notification("Warning",
                                "Failed to update favorite status.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                    }
                }
            });
        } else {
            starButton.setEnabled(false);
            starButton.setDescription("Only the object owner and users the object is shared with are allowed to change the favorite state.");
        }

        dcLayout = new UIUtils7.GridLayoutBuilder(3, 5)
                .addComponent(titleLabel, 0, 0, 2, 1).addComponent(creationLabel, 2, 0, 1, 1)
                .addComponent(creatorLabel, 0, 1, 3, 1)
                .addComponent(objectIdLabel, 0, 2, 3, 1)
                .fill(descriptionLabel, 0, 3)
                .getLayout();
        dcLayout.setSizeFull();

        Button.ClickListener saveCancelButtonListener = new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (saveEditButton.equals(event.getButton())) {
                    //do save
                    IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
                    mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());
                    try {
                        String title = titleField.getValue();
                        String description = descriptionArea.getValue();
                        Investigation investigation = object.getInvestigation();
                        boolean wasError = false;
                        if (description != null && description.length() <= 1024 && investigation != null) {
                            if (!description.equals(investigation.getDescription())) {
                                investigation.setDescription(description);
                                //store investigation
                                mdm.save(investigation);
                            }
                        } else {
                            LOGGER.warn("Failed to commit updated description '{}'. Either length is exceeded or investigation '{}' is null.", description, investigation);
                            wasError = true;
                        }
                        //store object
                        if (title != null && title.length() >= 3 && title.length() <= 255) {
                            if (!title.equals(object.getLabel())) {
                                //store object
                                object.setLabel(title);
                                object = mdm.save(object);
                            }
                        } else {
                            LOGGER.warn("Failed to commit updated title '{}'. Length is invalid (3<=l<=255).", title);
                            wasError = true;
                        }
                        if (wasError) {
                            new Notification("Warning",
                                    "Failed to update title and/or description. See logfile for details.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                        }

                        //As there is not automatic sync between database and search index the entry has to be reindexed at this point in order
                        //to keep both systems consistent. However, changes taking place in between are lost.
                        LOGGER.debug("Object committed to database. Updating index.");
                        ElasticsearchHelper.indexEntry(object);
                    } catch (UnauthorizedAccessAttemptException ex) {
                        LOGGER.error("Failed to commit changes.", ex);
                        new Notification("Warning",
                                "Failed to commit changes. See logfile for details.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());

                    } finally {
                        mdm.close();
                    }
                }
                //do cancel/reload and switch back to read-mode
                reset();
                switchEditMode();
            }
        };

        //save/cancel buttons
        saveEditButton = new NativeButton("Commit Update");
        saveEditButton.setIcon(new ThemeResource("img/16x16/save.png"));
        saveEditButton.setStyleName(BaseTheme.BUTTON_LINK);
        saveEditButton.setDescription("Save changes to this digital object's metadata.");
        saveEditButton.addClickListener(saveCancelButtonListener);
        cancelEditButton = new NativeButton("Cancel Update");
        cancelEditButton.setIcon(new ThemeResource("img/16x16/cancel.png"));
        cancelEditButton.setStyleName(BaseTheme.BUTTON_LINK);
        cancelEditButton.setDescription("Withdraw all changes to this digital object's metadata.");
        cancelEditButton.addClickListener(saveCancelButtonListener);

        //default action layout
        Label spacerMiscActionLayout = new Label();
        miscActionLayout = new HorizontalLayout(editButton, shareButton, starButton, spacerMiscActionLayout);
        miscActionLayout.setWidth("100%");
        miscActionLayout.setHeight("18px");
        miscActionLayout.setSpacing(false);
        miscActionLayout.setMargin(false);
        miscActionLayout.setExpandRatio(spacerMiscActionLayout, .9f);

        //edit action layout
        Label spacerEditActionLayout = new Label();
        editActionLayout = new HorizontalLayout(saveEditButton, cancelEditButton, spacerEditActionLayout);
        editActionLayout.setWidth("100%");
        editActionLayout.setHeight("18px");
        editActionLayout.setSpacing(false);
        editActionLayout.setMargin(false);
        editActionLayout.setExpandRatio(spacerEditActionLayout, .9f);

        //divider generation
        Label dividerTop = new Label();
        dividerTop.setHeight("5px");
        dividerTop.addStyleName("horizontal-line");
        dividerTop.setWidth("90%");
        Label dividerBottom = new Label();
        dividerBottom.setHeight("5px");
        dividerBottom.addStyleName("horizontal-line");
        dividerBottom.setWidth("90%");
        Label dividerLeft = new Label();
        dividerLeft.addStyleName("vertical-line");
        dividerLeft.setWidth("5px");
        dividerLeft.setHeight("90%");
        Label dividerRight = new Label();
        dividerRight.addStyleName("vertical-line");
        dividerRight.setWidth("5px");
        dividerRight.setHeight("90%");

        //build content layout
        HorizontalLayout contentLayout = new HorizontalLayout(typeImage, dividerLeft, dcLayout, dividerRight, downloadButton);
        contentLayout.setSizeFull();
        contentLayout.setSpacing(true);
        contentLayout.setComponentAlignment(typeImage, Alignment.TOP_RIGHT);
        contentLayout.setComponentAlignment(dividerLeft, Alignment.MIDDLE_CENTER);
        contentLayout.setComponentAlignment(dcLayout, Alignment.MIDDLE_CENTER);
        contentLayout.setComponentAlignment(dividerRight, Alignment.MIDDLE_CENTER);
        contentLayout.setComponentAlignment(downloadButton, Alignment.BOTTOM_CENTER);
        contentLayout.setExpandRatio(typeImage, .1f);
        contentLayout.setExpandRatio(dcLayout, .8f);
        contentLayout.setExpandRatio(downloadButton, .1f);

        //build main layout
        mainLayout = new VerticalLayout(dividerTop, contentLayout, dividerBottom, miscActionLayout);
        mainLayout.setExpandRatio(dividerTop, .05f);
        mainLayout.setComponentAlignment(dividerTop, Alignment.TOP_LEFT);
        mainLayout.setExpandRatio(contentLayout, .80f);
        mainLayout.setExpandRatio(dividerBottom, .05f);
        mainLayout.setComponentAlignment(dividerBottom, Alignment.BOTTOM_RIGHT);
        mainLayout.setExpandRatio(miscActionLayout, .1f);
        mainLayout.setSpacing(true);
        mainLayout.setMargin(true);
        mainLayout.addStyleName("basic");
        mainLayout.setWidth("100%");
        mainLayout.setHeight("185px");
        //do reset to load title and description
        reset();
        LOGGER.debug("Layout successfully build up.");
    }

    /**
     * Reset a single entry, e.g. to reload its state from the database.
     */
    private void reset() {
        titleField.setValue(object.getLabel());
        titleField.setDescription(object.getLabel());

        if (object.getLabel() != null) {
            titleLabel.setValue(StringUtils.abbreviate(object.getLabel(), 100));
            titleLabel.setDescription(object.getLabel());
        } else {
            titleLabel.setValue("dc:title");
            titleLabel.setEnabled(false);
        }

        if (object.getInvestigation() != null) {
            descriptionArea.setValue(object.getInvestigation().getDescription());
            descriptionLabel.setValue(StringUtils.abbreviate(object.getInvestigation().getDescription(), 250));
            descriptionLabel.setDescription(object.getInvestigation().getDescription());
            descriptionLabel.setEnabled(true);
        } else {
            descriptionLabel.setValue("dc:description");
            descriptionLabel.setEnabled(false);
        }

        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());

        try {
            DigitalObjectType favoriteType = mdm.findSingleResult("SELECT t FROM DigitalObjectType t WHERE t.identifier='" + MyVaadinUI.FAVORITE_TYPE_IDENTIFIER + "' AND t.typeDomain='" + MyVaadinUI.FAVORITE_TYPE_DOMAIN + "'", DigitalObjectType.class);

            if (DigitalObjectTypeHelper.isTypeAssignedToObject(object, favoriteType, AuthorizationContext.factorySystemContext())) {
                //set favorite status
                starButton.setIcon(new ThemeResource("img/16x16/starred.png"));
            } else {
                //set no-favorite status
                starButton.setIcon(new ThemeResource("img/16x16/unstarred.png"));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reset 'favorite' status of digital object.", e);
        }

        downloadButton.setCaption("Download");
        downloadButton.setIcon(new ThemeResource("img/32x32/download.png"));
        downloadButton.setDescription("Download the data of this digital object.");
        setupDownloadButton();
    }

    /**
     * Setup the download button for download-mode. The button click will be
     * linked to a download of the zipped version of the digital object's data.
     * If no data is available/accessible, the button will be disabled.
     */
    private void setupDownloadButton() {
        boolean haveDownload = false;
        try {
            //obtain the zip file node
            IFileNode zipNode = DigitalObjectPersistenceHelper.getDataZipFileNode(object, AuthorizationContext.factorySystemContext());
            if (zipNode != null) {
                //zip file node is valid, check URL
                final String zipUrl = zipNode.getLogicalFileName().asString();
                if (zipUrl != null) {
                    try {
                        //URL seems to be valid, try to get file input stream
                        final File toDownload = new File(new URL(zipUrl).toURI());
                        final FileInputStream fin = new FileInputStream(toDownload);

                        final FileDownloader downloader = new FileDownloader(new StreamResource(new StreamSource() {
                            @Override
                            public InputStream getStream() {
                                return fin;
                            }
                        }, object.getDigitalObjectIdentifier() + ".zip"));

                        downloader.extend(downloadButton);

                        //obtain stored file size from the according attribute
                        IAttribute[] attribs = zipNode.getAttributes().toArray(new IAttribute[]{});
                        long size = -1;

                        if (attribs != null) {
                            for (IAttribute attrib : attribs) {
                                if ("size".equals(attrib.getKey())) {
                                    try {
                                        size = Long.parseLong(attrib.getValue());
                                    } catch (NumberFormatException ex) {
                                        //no long
                                    }
                                }
                            }
                        }

                        downloadButton.setDescription("Download file " + toDownload.getName() + " (" + size + " bytes)");
                        haveDownload = true;
                    } catch (MalformedURLException | URISyntaxException | FileNotFoundException ex) {
                        LOGGER.error("Failed to setup download.", ex);
                        downloadButton.setDescription("Failed to obtain data URL.");
                    }
                } else {
                    downloadButton.setDescription("No data available, yet.");
                }
            } else {
                downloadButton.setDescription("No data available, yet.");
            }
        } catch (UnauthorizedAccessAttemptException ex) {
            LOGGER.error("No data available, yet.", ex);
            downloadButton.setDescription("No data available, yet.");
        }
        //set button only enabled if the data is there and can be accessed
        downloadButton.setEnabled(haveDownload);
    }

    /**
     * Switch to the edit mode and back. In edit mode, editable labels are
     * changed to textfields/-areas allowing to edit the according values that
     * can then be committed before switching back.
     */
    private void switchEditMode() {
        if (!editMode) {
            dcLayout.replaceComponent(titleLabel, titleField);
            dcLayout.replaceComponent(descriptionLabel, descriptionArea);
            mainLayout.replaceComponent(miscActionLayout, editActionLayout);
            editMode = true;
        } else {
            dcLayout.replaceComponent(titleField, titleLabel);
            dcLayout.replaceComponent(descriptionArea, descriptionLabel);
            mainLayout.replaceComponent(editActionLayout, miscActionLayout);
            editMode = false;
        }

    }

    /**
     * Set the image component. Currently, an automatically generated
     * placeholder image is used. This could be changed by e.g. storing
     * thumbnails in the data organization of an object or by storing additional
     * image information as new entity in the database.
     *
     * @param pObject The object to set the image for.
     */
    private void setImage(DigitalObject pObject) {

        //just use test for image...later check data organization
        final String text = pObject.getLabel();

        typeImage = new Image(null, new StreamResource(new StreamResource.StreamSource() {

            @Override
            public InputStream getStream() {
                try {
                    return new ByteArrayInputStream(TextImage.from(text).withSize(40).getBytes());
                } catch (IOException ex) {
                    //error...should not occur..but return empty array to avoid failing
                    return new ByteArrayInputStream(new byte[]{});
                }
            }
        }, "dummy.png"));
    }
}
