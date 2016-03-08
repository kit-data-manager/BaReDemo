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
package edu.kit.dama.ui.repo;

import edu.kit.dama.ui.repo.components.EntryRenderPanel;
import edu.kit.dama.ui.repo.components.PaginationPanel;
import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Theme;
import javax.servlet.annotation.WebServlet;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.server.Page;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.NativeButton;
import com.vaadin.ui.Notification;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.PopupView;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.BaseTheme;
import edu.kit.dama.authorization.entities.GroupId;
import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.authorization.entities.IRoleRestriction;
import edu.kit.dama.authorization.entities.Role;
import edu.kit.dama.authorization.entities.UserId;
import edu.kit.dama.authorization.entities.impl.AuthorizationContext;
import edu.kit.dama.authorization.entities.util.FindUtil;
import edu.kit.dama.authorization.entities.util.PU;
import edu.kit.dama.authorization.exceptions.EntityAlreadyExistsException;
import edu.kit.dama.authorization.exceptions.EntityNotFoundException;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.authorization.services.administration.GroupServiceLocal;
import edu.kit.dama.authorization.services.administration.UserServiceLocal;
import edu.kit.dama.commons.types.DigitalObjectId;
import edu.kit.dama.mdm.admin.ServiceAccessToken;
import edu.kit.dama.mdm.admin.util.ServiceAccessUtil;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.base.DigitalObjectType;
import edu.kit.dama.mdm.base.UserData;
import edu.kit.dama.mdm.content.search.impl.BaseSearchTerm;
import edu.kit.dama.mdm.content.search.impl.FulltextElasticSearchProvider;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import edu.kit.dama.mdm.tools.DigitalObjectTypeQueryHelper;
import edu.kit.dama.ui.admin.AdminUIMainView;
import edu.kit.dama.ui.commons.util.UIUtils7;
import edu.kit.dama.util.Constants;
import edu.kit.dama.ui.repo.util.DigitalObjectPersistenceHelper;
import edu.kit.dama.ui.repo.util.ElasticsearchHelper;
import edu.kit.dama.util.DataManagerSettings;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Theme("mytheme")
@SuppressWarnings("serial")
@PreserveOnRefresh
public class MyVaadinUI extends UI {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyVaadinUI.class);

    public static final String MAIN_LOGIN_TOKEN_KEY = "mainLogin";
    public static final String SEARCH_PATH = "/search";
    public static final String REFERENCE_PATH = "/reference";

    //GUEST user and USERS group
    private final UserId GUEST_USER = new UserId("GUEST");
    private final GroupId USERS_GROUP = new GroupId(Constants.USERS_GROUP_ID);

    //favorite object type
    public static final String FAVORITE_TYPE_IDENTIFIER = "favorite";
    public static final String FAVORITE_TYPE_DOMAIN = "http://kitdatamanager.net/types";
    public static final int FAVORITE_TYPE_VERSION = 1;

    //special search queries
    public static final String FAVORITE_SEARCH_TERM = "FAVORITES";

    //UI components
    private TextField email;
    private PasswordField password;
    private GridLayout loginForm;
    private UserData loggedInUser = UserData.NO_USER;
    private GridLayout searchLayout;
    private VerticalLayout mainLayout;
    private FulltextElasticSearchProvider searchProvider = null;
    private HorizontalLayout memberLayout;
    private TextField searchField;
    private PaginationPanel paginationPanel;
    private final NativeButton adminButton = new NativeButton("Administration");
    private final NativeButton loginButton = new NativeButton("Login");
    private final NativeButton logoutButton = new NativeButton("Logout");

    @WebServlet(value = "/*", asyncSupported = true)
    @VaadinServletConfiguration(productionMode = true, ui = MyVaadinUI.class, widgetset = "edu.kit.dama.ui.repo.AppWidgetSet")
    public static class RepositoryServlet extends VaadinServlet {
    }

    @WebServlet(value = "/admin/*", asyncSupported = true)
    @VaadinServletConfiguration(productionMode = true, ui = AdminUIMainView.class, widgetset = "edu.kit.dama.ui.repo.AppWidgetSet")
    public static class AdminServlet extends VaadinServlet {
    }

    /**
     * Get the authorization context which consists of the logged in user, the
     * group USERS and the maximum role the user can have in the group. If no
     * user is logged in, {@link #getDummyContext()} is returned.
     *
     * @return The Authorization context.
     */
    public final IAuthorizationContext getAuthorizationContext() {
        if (!isUserLoggedIn()) {
            //use dummy system user without permissions if nobody is logged in
            return getDummyContext();
        }
        //return valid context
        try {
            IRoleRestriction maxRole = GroupServiceLocal.getSingleton().getMaximumRole(USERS_GROUP, new UserId(loggedInUser.getDistinguishedName()), AuthorizationContext.factorySystemContext());
            return new AuthorizationContext(new UserId(loggedInUser.getDistinguishedName()), USERS_GROUP, (Role) maxRole);
        } catch (EntityNotFoundException | UnauthorizedAccessAttemptException ex) {
            LOGGER.error("Failed to get authorization context. Returning dummy context.", ex);
            return getDummyContext();
        }
    }

    /**
     * Check if a user is logged in or not.
     *
     * @return TRUE if a user is logged in.
     */
    public final boolean isUserLoggedIn() {
        return !(loggedInUser == null || UserData.NO_USER.equals(loggedInUser));
    }

    @Override
    protected final void init(VaadinRequest request) {
        checkData();
        LOGGER.info("Accessing path {}", request.getPathInfo());

        if (null != request.getPathInfo()) {
            switch (request.getPathInfo()) {
                case REFERENCE_PATH:
                    LOGGER.info("Using pid {}", request.getParameter("pid"));
                    buildRefView(request.getParameter("pid"));
                    break;
                case SEARCH_PATH:
                    LOGGER.info("Using query {}", request.getParameter("q"));
                    //search view WITH external query support
                    buildSearchView(request.getParameter("q"));
                    break;
                default:
                    LOGGER.info("Unknown path info. Using default view.");
                    //search view without external query support
                    buildSearchView(null);
            }
        } else {
            LOGGER.info("No path info. Using default view.");
            buildSearchView(null);
        }
        setSizeFull();
    }

    @Override
    protected final void refresh(VaadinRequest request) {
        if (searchField == null) {
            init(request);
            return;
        }
        LOGGER.info("Refreshing path {}", request.getPathInfo());
        if (null != request.getPathInfo()) {
            switch (request.getPathInfo()) {
                case SEARCH_PATH:
                    LOGGER.info("Using query {}", request.getParameter("q"));
                    if (request.getParameter("q") != null) {
                        searchField.setValue(request.getParameter("q"));
                    } else {
                        searchField.setValue("*");
                    }
                    doSearch();
                    break;
                default:
                    LOGGER.info("Only SEARCH_PATH is supported in refresh mode.");
            }
        }
    }

    /**
     * Refresh the main layout depending on the logged in user and the current
     * view.
     */
    private void refreshMainLayout() {
        if (isUserLoggedIn()) {
            //if user is now logged in, show the logout button
            memberLayout.replaceComponent(loginButton, logoutButton);
        } else {
            //a user is not logged in any longer, so reset the input fields and show the login button
            memberLayout.replaceComponent(logoutButton, loginButton);
            email.setValue("");
            password.setValue("");
        }
        //remove all entries in order to avoid permission issues on logout/user change
        paginationPanel.setAllEntries(new LinkedList<DigitalObjectId>());
    }

    /**
     * Check relevant data needed for the simple repository. This includes e.g.
     * the favorite object type.
     */
    private void checkData() {
        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());

        try {
            DigitalObjectType favoriteType = mdm.findSingleResult("SELECT t FROM DigitalObjectType t WHERE t.identifier='" + MyVaadinUI.FAVORITE_TYPE_IDENTIFIER + "' AND t.typeDomain='" + MyVaadinUI.FAVORITE_TYPE_DOMAIN + "'", DigitalObjectType.class);

            if (favoriteType == null) {
                LOGGER.debug("'Favorite' object type does not exist. Creating it.");
                DigitalObjectType type = new DigitalObjectType();
                type.setIdentifier(FAVORITE_TYPE_IDENTIFIER);
                type.setTypeDomain(FAVORITE_TYPE_DOMAIN);
                type.setVersion(1);
                type.setDescription("Digital Object Type that can be assigned to identify favorized objects for better searchability.");
                mdm.save(type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to change 'favorite' status of digital object.", e);
        } finally {
            mdm.close();
        }
        boolean guestExists = false;
        try {
            EntityManager em = PU.entityManager();
            guestExists = (FindUtil.findUser(em, GUEST_USER) != null);
            em.close();
        } catch (EntityNotFoundException ex) {
            //guest user not exists
        }
        if (!guestExists) {
            mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
            mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());
            try {
                LOGGER.debug("GUEST user not exists. Registering new user.");
                UserServiceLocal.getSingleton().register(GUEST_USER, Role.GUEST, AuthorizationContext.factorySystemContext());
                UserData user = new UserData();
                user.setDistinguishedName(GUEST_USER.getStringRepresentation());
                user.setEmail("dama@kit.edu");
                user.setFirstName("Guest");
                user.setLastName("User");
                mdm.save(user);
                LOGGER.debug("Adding GUEST user to USERS group.");
                GroupServiceLocal.getSingleton().addUser(USERS_GROUP, GUEST_USER, Role.GUEST, AuthorizationContext.factorySystemContext());
                LOGGER.debug("GUEST user successfully created.");
            } catch (UnauthorizedAccessAttemptException | EntityAlreadyExistsException | EntityNotFoundException ex) {
                LOGGER.error("Failed to create GUEST user.", ex);
            } finally {
                mdm.close();
            }
        }
    }

    /**
     * Build the reference view. This view is used to show digital object
     * information directly via URL without accessing the search interface.
     *
     * @param pIdentifier The identifier of the object to query for.
     */
    private void buildRefView(String pIdentifier) {
        if (pIdentifier == null) {
            Label error = new Label("<h3>No object identifier (pid) provided in URL.<h3>", ContentMode.HTML);
            mainLayout = new VerticalLayout(error);
            return;
        }
        try {
            //in ref view we use the dummy context as references should be accessible publicly but without write permissions
            IAuthorizationContext ctx = getDummyContext();
            DigitalObject object = DigitalObjectPersistenceHelper.getDigitalObjectByIdentifier(pIdentifier, ctx);
            if (object == null || !object.isVisible()) {
                Label error = new Label("<h3>The object '" + pIdentifier + "' seems to be temporarily not available.<h3>", ContentMode.HTML);
                mainLayout = new VerticalLayout(error);
            } else {
                EntryRenderPanel rp = new EntryRenderPanel(null, object, ctx);

                mainLayout = new VerticalLayout(rp);
                mainLayout.setComponentAlignment(rp, Alignment.TOP_CENTER);
                rp.setWidth("1024px");
                rp.setHeight("768px");
            }
        } catch (UnauthorizedAccessAttemptException ex) {
            Label error = new Label("<h3>The object with the identifier '" + pIdentifier + "' seems to be no longer publicly accessible.<h3>", ContentMode.HTML);
            mainLayout = new VerticalLayout(error);
        }
        setContent(mainLayout);
    }

    /**
     * Build the search view and execute the provided query immediately.
     *
     * @param pQuery The query to execute or null if an empty view should be
     * shown.
     */
    private void buildSearchView(String pQuery) {
        loginButton.setWidth("70px");
        loginButton.setStyleName(BaseTheme.BUTTON_LINK);
        logoutButton.setWidth("70px");
        logoutButton.setStyleName(BaseTheme.BUTTON_LINK);
        adminButton.setWidth("70px");
        adminButton.setStyleName(BaseTheme.BUTTON_LINK);

        logoutButton.addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                loggedInUser = UserData.NO_USER;
                refreshMainLayout();
            }
        });

        adminButton.addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                Page.getCurrent().open(DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.GENERAL_BASE_URL_ID, "http://localhost:8889/BaReDemo") + "/admin", "_blank");
            }
        });

        searchField = UIUtils7.factoryTextField(null, "Search for...");
        searchField.setWidth("920px");
        searchField.setHeight("60px");
        searchField.addStyleName("searchField");

        paginationPanel = new PaginationPanel(this);
        paginationPanel.setSizeFull();
        paginationPanel.setAllEntries(new LinkedList<DigitalObjectId>());

        searchProvider = new FulltextElasticSearchProvider(DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_CLUSTER_ID, "KITDataManager"),
                DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_HOST_ID, "localhost"),
                DataManagerSettings.getSingleton().getStringProperty(DataManagerSettings.ELASTIC_SEARCH_DEFAULT_INDEX_ID, ElasticsearchHelper.ELASTICSEARCH_TYPE),
                ElasticsearchHelper.ELASTICSEARCH_TYPE);
        NativeButton goButton = new NativeButton();
        goButton.setIcon(new ThemeResource("img/24x24/search.png"));
        goButton.setWidth("60px");
        goButton.setHeight("60px");
        goButton.addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                doSearch();
            }
        });
        goButton.setClickShortcut(KeyCode.ENTER);

        setupLoginForm();
        loginForm.setWidth("320px");
        loginForm.setHeight("150px");
        final PopupView loginPopup = new PopupView(null, loginForm);
        loginPopup.setHideOnMouseOut(false);
        loginButton.addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                //mainLayout.replaceComponent(searchLayout, loginForm);
                loginPopup.setPopupVisible(true);
            }
        });

        Label filler = new Label();
        memberLayout = new HorizontalLayout(filler, adminButton, loginButton, loginPopup);
        memberLayout.setComponentAlignment(loginButton, Alignment.TOP_RIGHT);
        memberLayout.setComponentAlignment(adminButton, Alignment.TOP_RIGHT);
        memberLayout.setComponentAlignment(loginPopup, Alignment.TOP_RIGHT);

        memberLayout.setExpandRatio(filler, 1.0f);
        memberLayout.setMargin(false);
        memberLayout.setSpacing(false);
        memberLayout.setWidth("100%");
        memberLayout.setHeight("30px");

        Label spacer = new Label("<hr/>", ContentMode.HTML);
        spacer.setHeight("20px");

        searchLayout = new UIUtils7.GridLayoutBuilder(3, 4).addComponent(searchField, Alignment.TOP_LEFT, 0, 0, 2, 1).addComponent(goButton, Alignment.TOP_RIGHT, 2, 0, 1, 1).
                fillRow(spacer, 0, 1, 1).
                addComponent(paginationPanel, Alignment.MIDDLE_CENTER, 0, 2, 3, 2).getLayout();
        searchLayout.addStyleName("paper");
        searchLayout.setSpacing(true);
        searchLayout.setMargin(true);
        paginationPanel.setWidth("980px");
        //wrapper
        Label icon8Link = new Label("<a href=\"http://icons8.com\">Icons by icon8.com</a>", ContentMode.HTML);
        mainLayout = new VerticalLayout(memberLayout, searchLayout, icon8Link);
        mainLayout.setComponentAlignment(memberLayout, Alignment.TOP_CENTER);
        mainLayout.setComponentAlignment(searchLayout, Alignment.TOP_CENTER);
        mainLayout.setComponentAlignment(icon8Link, Alignment.BOTTOM_RIGHT);
        mainLayout.setExpandRatio(memberLayout, .05f);
        mainLayout.setExpandRatio(searchLayout, .93f);
        mainLayout.setExpandRatio(icon8Link, .02f);

        VerticalLayout fullscreen = new VerticalLayout(mainLayout);
        fullscreen.setComponentAlignment(mainLayout, Alignment.TOP_CENTER);
        fullscreen.setSizeFull();
        setContent(fullscreen);

        mainLayout.setWidth("1024px");
        mainLayout.setHeight("768px");

        if (pQuery != null) {
            searchField.setValue(pQuery);
            doSearch();
        }
    }

    /**
     * Perform the search and set the results in the pagination view.
     */
    private void doSearch() {
        String value = searchField.getValue();
        if (value != null && !value.isEmpty()) {
            //check for specific search of pending ingests. 
            //This search is carried out by using a special search term and only works if there is a user logged in.
            //The result page will contain all ingests of the logged in user that are open.
            switch (value) {
                case FAVORITE_SEARCH_TERM:
                    //search only for favorites
                    IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
                    mdm.setAuthorizationContext(AuthorizationContext.factorySystemContext());
                    List<DigitalObjectId> ids = new LinkedList<>();
                    try {
                        DigitalObjectType favoriteType = mdm.findSingleResult("SELECT t FROM DigitalObjectType t WHERE t.identifier='" + MyVaadinUI.FAVORITE_TYPE_IDENTIFIER + "' AND t.typeDomain='" + MyVaadinUI.FAVORITE_TYPE_DOMAIN + "'", DigitalObjectType.class);

                        if (favoriteType != null) {
                            List<DigitalObject> favorites = DigitalObjectTypeQueryHelper.getDigitalObjectsByDigitalObjectType(favoriteType, AuthorizationContext.factorySystemContext());
                            for (DigitalObject object : favorites) {
                                ids.add(object.getDigitalObjectId());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to change 'favorite' status of digital object.", e);
                    } finally {
                        mdm.close();
                    }
                    paginationPanel.setAllEntries(ids);
                    break;
                default:
                    //perform fulltext search in _all field of documents indexed by elasticsearch.
                    BaseSearchTerm term = searchProvider.getSearchTerms().get(0);
                    term.setValue(value);
                    searchProvider.performSearch(Arrays.asList(term), AuthorizationContext.factorySystemContext());
                    paginationPanel.setAllEntries(searchProvider.getResults());
                    break;
            }
        } else {
            //no search input
        }
    }

    /**
     * Get a dummy authorization context which is allowed to read but not to
     * write. This context is used if no user is logged in or if the reference
     * view is visible. As this context is used for authorization decisions
     * UserId and GroupId must have valid values. As UserId the default admin
     * user id is used, the GroupId is the standard group USERS. The role is set
     * to GUEST which allows read access to all data. If this is not wanted, a
     * login should be mandatory for all search requests.
     *
     * @return A dummy authorization context.
     */
    private IAuthorizationContext getDummyContext() {
        IAuthorizationContext ctx = AuthorizationContext.factorySystemContext();
        ctx.setUserId(GUEST_USER);
        ctx.setGroupId(USERS_GROUP);
        ctx.setRoleRestriction(Role.GUEST);
        return ctx;
    }

    /**
     * Setup the login form including its logic.
     */
    private void setupLoginForm() {
        email = UIUtils7.factoryTextField("Email", "Please enter your email.", "300px", true, -1, 255);
        password = UIUtils7.factoryPasswordField("Password", "300px", true, -1, 255);
        Button doLoginButton = new Button("Login");
        //login.setClickShortcut(KeyCode.ENTER);
        doLoginButton.setWidth("100px");
        loginForm = new UIUtils7.GridLayoutBuilder(2, 3).addComponent(email, 0, 0, 2, 1).addComponent(password, 0, 1, 2, 1).addComponent(doLoginButton, 0, 2, 1, 1).getLayout();
        loginForm.setComponentAlignment(doLoginButton, Alignment.MIDDLE_LEFT);

        //login listener
        doLoginButton.addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (!UIUtils7.validate(loginForm)) {
                    new Notification("Warning",
                            "Please correct the error(s) above.", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                    return;
                }
                String userMail = email.getValue();
                String userPassword = password.getValue();
                IMetaDataManager manager = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
                manager.setAuthorizationContext(AuthorizationContext.factorySystemContext());
                try {
                    ServiceAccessToken token = ServiceAccessUtil.getAccessToken(manager, userMail, MAIN_LOGIN_TOKEN_KEY);
                    if (token == null) {
                        new Notification("Login Failed",
                                "No login information found for email " + userMail + ".", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                        return;
                    }

                    if (!userPassword.equals(token.getSecret())) {
                        new Notification("Login Failed",
                                "Wrong password for email " + userMail + ".", Notification.Type.WARNING_MESSAGE).show(Page.getCurrent());
                    } else {
                        //login successful
                        UserData template = new UserData();
                        template.setDistinguishedName(token.getUserId());
                        List<UserData> result = manager.find(template, template);
                        if (result.isEmpty() || result.size() > 1) {
                            throw new Exception("Invalid number of user entries found for userId " + token.getUserId() + ". Please contact a system administrator.");
                        }
                        //done
                        loggedInUser = result.get(0);
                        refreshMainLayout();
                    }
                } catch (Exception ex) {
                    new Notification("Login Failed",
                            "Failed to access login database. Please contact an administrator.", Notification.Type.ERROR_MESSAGE).show(Page.getCurrent());
                    LOGGER.error("Login failed.", ex);
                } finally {
                    manager.close();
                }
            }
        });

        loginForm.setSpacing(true);
        loginForm.setMargin(true);
    }

}
