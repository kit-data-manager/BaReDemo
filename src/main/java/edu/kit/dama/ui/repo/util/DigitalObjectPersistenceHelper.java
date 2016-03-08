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
package edu.kit.dama.ui.repo.util;

import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import edu.kit.dama.mdm.core.authorization.SecureMetaDataManager;
import edu.kit.dama.mdm.dataorganization.entity.core.IFileNode;
import edu.kit.dama.mdm.dataorganization.entity.core.IFileTree;
import edu.kit.dama.mdm.dataorganization.impl.util.Util;
import edu.kit.dama.mdm.dataorganization.service.core.DataOrganizationServiceLocal;
import edu.kit.dama.mdm.dataorganization.service.exception.EntityNotFoundException;
import edu.kit.dama.staging.entities.ingest.IngestInformation;
import edu.kit.dama.staging.services.impl.ingest.IngestInformationServiceLocal;
import edu.kit.dama.util.CryptUtil;
import edu.kit.dama.util.Constants;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class used to get information on persisted digital objects. Some of
 * the methods are not used by the BaReDemonstrator but may help for extension
 * projects.
 *
 * @author mf6319
 */
public final class DigitalObjectPersistenceHelper {

    private final static Logger LOGGER = LoggerFactory.getLogger(DigitalObjectPersistenceHelper.class);

    /**
     * Hidden constructor.
     */
    private DigitalObjectPersistenceHelper() {
    }

    /**
     * Update the object visibility. Basically, the associated digital object is
     * set to "visible" or "invisible". Secondly, the elasticsearch index will
     * be modified depending on the visibility. If visibility changes and the
     * object is registered in elasticsearch, it will be unregistered.
     * Otherwise, it will be registered.
     *
     * @param pObject The object to change.
     * @param pAccessible TRUE = visible, FALSE = not visible.
     * @param pContext The authorization context used to access the object. and
     * the associated digital object.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not allowed to
     * access the digital object.
     */
    public static void updateDigitalObjectAccessibility(DigitalObject pObject, boolean pAccessible, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pObject != null) {
            if (pAccessible != pObject.isVisible()) {
                //accessibility has changed
                pObject.setVisible(pAccessible);
                //save the object
                IMetaDataManager mdm = null;
                try {
                    mdm = SecureMetaDataManager.factorySecureMetaDataManager(pContext);
                    mdm.save(pObject);
                } finally {
                    if (mdm != null) {
                        mdm.close();
                    }
                }
            }

            //Reindex object
            if (pObject.isVisible()) {
                //add to index
                ElasticsearchHelper.indexEntry(pObject);
            } else {
                //remove from index
                ElasticsearchHelper.unindexEntry(pObject);
            }
        } else {
            LOGGER.error("Failed to update object accessibility. Provided object is 'null'.");
        }
    }

    /**
     * Find an object by its identifier.
     *
     * @param pIdentifier The identifier.
     * @param pContext The authorization context used to find the object.
     *
     * @return The object or null if nothing was found.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not allowed to
     * access the object.
     */
    public static DigitalObject getDigitalObjectByIdentifier(String pIdentifier, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        IMetaDataManager mdm = null;
        try {
            mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
            mdm.setAuthorizationContext(pContext);
            return mdm.findSingleResult("SELECT o FROM DigitalObject o WHERE o.digitalObjectIdentifier=?1", new Object[]{pIdentifier}, DigitalObject.class);
        } finally {
            if (mdm != null) {
                mdm.close();
            }
        }
    }

    /**
     * Get the ingest associated with the provided object.
     *
     * @param pObject The object the ingest is associated with.
     * @param pContext The authorization context used to obtain the ingest.
     *
     * @return The ingest or null if nothing was found or the provided object is
     * null.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not allowed to
     * access the ingest.
     */
    public static IngestInformation getIngestForObject(DigitalObject pObject, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pObject == null) {
            return null;
        }
        return IngestInformationServiceLocal.getSingleton().getIngestInformationByDigitalObjectId(pObject.getDigitalObjectId(), pContext);
    }

    /**
     * Get a list of all accessible objects. This method returns visible and
     * invisible entities.
     *
     * @param pContext The authorization context used to obtain the objects.
     *
     * @return A list of objects or an empty list.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not allowed to
     * access any object.
     */
    public static List<DigitalObject> getAllObjects(IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        return getAllObjects(false, pContext);
    }

    /**
     * Get a list of all accessible objects.
     *
     * @param pVisibleOnly TRUE = Only return visible entities.
     * @param pContext The authorization context used to obtain the objects.
     *
     * @return A list of objects or an empty list.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not allowed to
     * access the ingest.
     */
    public static List<DigitalObject> getAllObjects(boolean pVisibleOnly, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        IMetaDataManager mdm = null;
        try {
            mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
            mdm.setAuthorizationContext(pContext);
            //get all accessible digital objects
            return mdm.findResultList("SELECT o FROM DigitalObject o WHERE o.visible=?1", new Object[]{Boolean.TRUE}, DigitalObject.class);
        } finally {
            if (mdm != null) {
                mdm.close();
            }
        }
    }

    /**
     * Get the data organization node that holds the zipped data archive for the
     * provided object.
     *
     * @param pObject The object.
     * @param pContext The context used to access the data.
     *
     * @return The filenode containing the ZIP archive or null if no filenode is
     * available (yet).
     *
     * @throws UnauthorizedAccessAttemptException If pContext is not authorized
     * to access the data.
     */
    public static IFileNode getDataZipFileNode(DigitalObject pObject, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pObject != null) {
            try {
                IFileTree tree = DataOrganizationServiceLocal.getSingleton().loadFileTree(pObject.getDigitalObjectId(), Constants.STAGING_GENERATED_FOLDER_NAME, pContext);
                return (IFileNode) Util.getNodeByName(tree.getRootNode(), CryptUtil.stringToSHA1(pObject.getDigitalObjectIdentifier()) + ".zip");
            } catch (EntityNotFoundException ex) {
                return null;
            }
        }
        return null;
    }

}
