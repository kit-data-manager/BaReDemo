/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kit.dama.ui.repo.components;

import com.vaadin.server.Page;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.NativeButton;
import com.vaadin.ui.Notification;
import com.vaadin.ui.PopupView;
import com.vaadin.ui.TwinColSelect;
import com.vaadin.ui.VerticalLayout;
import edu.kit.dama.authorization.entities.Role;
import edu.kit.dama.authorization.entities.UserId;
import edu.kit.dama.authorization.entities.impl.AuthorizationContext;
import edu.kit.dama.authorization.exceptions.EntityAlreadyExistsException;
import edu.kit.dama.authorization.exceptions.EntityNotFoundException;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.authorization.services.administration.ResourceServiceLocal;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.base.UserData;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sharing object component implementation. The component consists of a
 * TwinColSelect allowing to (un-)share an object with a user depending on which
 * side of the list the user is located.
 *
 * @author jejkal
 */
public class ShareObjectComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareObjectComponent.class);

    private VerticalLayout mainLayout;
    private PopupView sharePopup;
    private TwinColSelect shareList;
    private DigitalObject object;

    /**
     * Default constructor.
     */
    public ShareObjectComponent() {
        buildMainLayout();
    }

    /**
     * Setup the component by obtaining all users from the database and place
     * them in the list according to their current share status regarding
     * pObject.
     *
     * @param pObject The object for which the sharing information should be
     * changed.
     */
    public final void setup(DigitalObject pObject) {
        object = pObject;
        shareList.removeAllItems();
        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());

        List<UserId> authorizedUsers = new LinkedList<>();

        try {
            //obtain all users
            List<UserData> users = mdm.find(UserData.class);
            //sort alphabetically by user names
            Collections.sort(users, new Comparator<UserData>() {

                @Override
                public int compare(UserData o1, UserData o2) {
                    return o1.getFullname().compareToIgnoreCase(o2.getFullname());
                }
            });

            //add all users to the left side
            for (UserData user : users) {
                shareList.addItem(user.getDistinguishedName());
                shareList.setItemCaption(user.getDistinguishedName(), user.getFullname() + " (" + user.getEmail() + ")");
            }

            //obtain a list of all users authorized to access the object
            authorizedUsers = ResourceServiceLocal.getSingleton().getAuthorizedUsers(pObject.getSecurableResourceId(), Role.MEMBER, AuthorizationContext.factorySystemContext());
        } catch (EntityNotFoundException | UnauthorizedAccessAttemptException ex) {
            LOGGER.error("Failed to setup sharing component for object with resource id " + object.getSecurableResourceId(), ex);
        } finally {
            mdm.close();
        }

        //transform the list of authorized users into a set for setting the selection value
        Set<String> select = new TreeSet<>();
        for (UserId userId : authorizedUsers) {
            select.add(userId.getStringRepresentation());
        }

        //select all authorized users to be located in the "shared with" part of the list
        shareList.setValue(select);
    }

    /**
     * Get the popup view containing this component.
     *
     * @return The popup view.
     */
    public final PopupView getPopupView() {
        if (sharePopup == null) {
            sharePopup = new PopupView(null, mainLayout);
            sharePopup.setHideOnMouseOut(false);
        }

        return sharePopup;
    }

    /**
     * Build the main layout of the component.
     */
    private void buildMainLayout() {
        shareList = new TwinColSelect();
        shareList.setRows(10);
        shareList.setMultiSelect(true);
        shareList.setImmediate(true);
        shareList.setLeftColumnCaption("Not accessible by");
        shareList.setRightColumnCaption("Accessible by");

        shareList.setWidth("500px");
        shareList.setHeight("400px");

        final NativeButton shareButton = new NativeButton("Share");
        final NativeButton cancelButton = new NativeButton("Cancel");

        Button.ClickListener listener = new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (shareButton.equals(event.getButton())) {
                    syncShares();
                }
                sharePopup.setPopupVisible(false);
            }
        };

        shareButton.addClickListener(listener);
        cancelButton.addClickListener(listener);

        HorizontalLayout buttonLayout = new HorizontalLayout(cancelButton, shareButton);
        mainLayout = new VerticalLayout(shareList, buttonLayout);
        mainLayout.setComponentAlignment(buttonLayout, Alignment.BOTTOM_RIGHT);
        mainLayout.setExpandRatio(shareList, .9f);
        mainLayout.setExpandRatio(buttonLayout, .1f);
    }

    /**
     * Synchronize the selected values with the database.
     */
    private void syncShares() {
        Set<Object> selection = (Set<Object>) shareList.getValue();
        if (selection.isEmpty()) {
            //not allowed
            new Notification("Warning",
                    "Failed to update sharing information. At least one user must have access.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
            return;
        }
        List<UserId> authorizedUsers;
        List<UserId> privilegedUsers;
        try {
            //obtain all users allowed to access the object with write access
            authorizedUsers = ResourceServiceLocal.getSingleton().getAuthorizedUsers(object.getSecurableResourceId(), Role.MEMBER, AuthorizationContext.factorySystemContext());
            //obtain all users allowed to manage the object (add new shares)...this should be typically only one user
            privilegedUsers = ResourceServiceLocal.getSingleton().getAuthorizedUsers(object.getSecurableResourceId(), Role.MANAGER, AuthorizationContext.factorySystemContext());
        } catch (EntityNotFoundException | UnauthorizedAccessAttemptException ex) {
            LOGGER.error("Failed to obtain authorized users. Committing sharing information failed.", ex);
            new Notification("Warning",
                    "Failed to obtain authorized users. Updating sharing information failed.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
            return;
        }

        List<UserId> newShares = new LinkedList<>();
        //flag that tells us whether there is at least one privileged user (user with MANAGER role) left.
        boolean privilegedUserRemains = false;

        for (Object o : selection) {
            final UserId uid = new UserId(((String) o));
            final UserId existing = (UserId) CollectionUtils.find(authorizedUsers, new Predicate() {

                @Override
                public boolean evaluate(Object o) {
                    return ((UserId) o).getStringRepresentation().equals(uid.getStringRepresentation());
                }
            });

            if (existing == null) {
                //not shared with this user yet ... add
                newShares.add(uid);
            } else {
                //The object is already shared with this user and therefore the user should be in the list of authorized users.
                //As we use this list later for removal, remove the current user id from the list as nothing changes for this user.
                authorizedUsers.remove(existing);
                if (!privilegedUserRemains) {
                    //check if the current user is a privileged user. If this is the case, the object is shared with at least one privileged user.
                    UserId privilegedUser = (UserId) CollectionUtils.find(privilegedUsers, new Predicate() {

                        @Override
                        public boolean evaluate(Object o) {
                            return ((UserId) o).getStringRepresentation().equals(existing.getStringRepresentation());
                        }
                    });

                    if (privilegedUser != null) {
                        privilegedUserRemains = true;
                    }
                }
            }
        }

        //At least one user with the role MANAGER must be left. However, normally only this one user should be able to change sharing information,
        //therefore this check is actually just to avoid removing yourself from the list of authorized users.
        if (!privilegedUserRemains) {
            new Notification("Warning",
                    "Failed to update sharing information. Obviously you tried to remove the owner who has privileged permissions.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
            return;
        }

        //finally, newShares contains all userIds that have to be added as authorized users,
        //and authorizedUsers contains all userIds that were authorized before but are currently 
        //not in the selected list, so their sharing status will be dropped.
        for (UserId newShare : newShares) {
            try {
                ResourceServiceLocal.getSingleton().addGrant(object.getSecurableResourceId(), newShare, Role.MEMBER, AuthorizationContext.factorySystemContext());
            } catch (EntityNotFoundException | EntityAlreadyExistsException | UnauthorizedAccessAttemptException ex) {
                LOGGER.error("Failed to grant access for user " + newShare + " to resource " + object.getSecurableResourceId(), ex);
                new Notification("Error",
                        "Failed to update sharing information.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                return;
            }
        }

        //remove all users that have been authorized before but aren't in the "shared with" side of the list.
        for (UserId dropShare : authorizedUsers) {
            try {
                ResourceServiceLocal.getSingleton().revokeGrant(object.getSecurableResourceId(), dropShare, AuthorizationContext.factorySystemContext());
            } catch (EntityNotFoundException | UnauthorizedAccessAttemptException ex) {
                LOGGER.error("Failed to revoke access for user " + dropShare + " to resource " + object.getSecurableResourceId(), ex);
                new Notification("Error",
                        "Failed to update sharing information.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                return;
            }
        }

        new Notification("Information",
                "Successfully updated sharing information.", Notification.Type.TRAY_NOTIFICATION).show(Page.getCurrent());
    }

}
