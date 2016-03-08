/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kit.dama.ui.repo.util;

import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.authorization.exceptions.EntityNotFoundException;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.base.DigitalObjectType;
import edu.kit.dama.mdm.base.ObjectTypeMapping;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class providing some features for handling digital object types. As of
 * KIT DM 1.2 these features are part of the standard DigitalObjectTypeQueryHelper
 * provided by the MDM-BaseMetaData module.
 *
 * @author jejkal
 */
public class DigitalObjectTypeHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DigitalObjectTypeHelper.class);

    /**
     * Assigns the provided object type to the provided digital object. Both
     * arguments must be existing, persisted entities. If there is already a
     * mapping between the object and the type, the existing mapping will be
     * returned. Otherwise, a new mapping is created and returned.
     *
     * @param pInputObject The input object.
     * @param pType The digital object type to assign.
     * @param pContext The context used to authorize the access.
     *
     * @return The created/existing type-object mapping.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not authorized
     * to perform the operation.
     */
    public static ObjectTypeMapping assignTypeToObject(DigitalObject pInputObject, DigitalObjectType pType, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pInputObject == null) {
            throw new IllegalArgumentException("Argument pInputObject should not be null.");
        }

        if (pType == null) {
            throw new IllegalArgumentException("Argument pType should not be null.");
        }
        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(pContext);
        ObjectTypeMapping existingMapping = mdm.findSingleResult("SELECT m FROM ObjectTypeMapping m WHERE m.digitalObject.baseId=" + pInputObject.getBaseId() + "AND m.objectType.id=" + pType.getId(), ObjectTypeMapping.class);
        if (existingMapping == null) {
            //no mapping exist...create it.
            LOGGER.debug("No existing mapping found for base id {} and type id {}. Creating and returning new mapping.", pInputObject.getBaseId(), pType.getId());
            try {
                ObjectTypeMapping mapping = new ObjectTypeMapping();
                mapping.setDigitalObject(pInputObject);
                mapping.setObjectType(pType);
                return mdm.save(mapping);
            } finally {
                mdm.close();
            }
        } else {
            LOGGER.debug("Existing mapping found for base id {} and type id {}. Returning existing mapping.", pInputObject.getBaseId(), pType.getId());
            return existingMapping;
        }
    }

    /**
     * Check whether the provided type is already assigned to the provided
     * object type to the provided digital object. Both arguments must be
     * existing, persisted entities. If the type is assigned, true is returned.
     * Otherwise, false is returned.
     *
     * @param pInputObject The input object.
     * @param pType The digital object type to check.
     * @param pContext The context used to authorize the access.
     *
     * @return TRUE if pType is assigned to pInputObject.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not authorized
     * to perform the operaion.
     */
    public static boolean isTypeAssignedToObject(DigitalObject pInputObject, DigitalObjectType pType, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pInputObject == null) {
            throw new IllegalArgumentException("Argument pInputObject should not be null.");
        }

        if (pType == null) {
            throw new IllegalArgumentException("Argument pType should not be null.");
        }
        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(pContext);
        try {
            Number resultCount = mdm.findSingleResult("SELECT COUNT(m) FROM ObjectTypeMapping m WHERE m.digitalObject.baseId=" + pInputObject.getBaseId() + " AND m.objectType.id=" + pType.getId(), Number.class);
            return (resultCount == null) ? false : (resultCount.intValue() == 1);
        } finally {
            mdm.close();
        }
    }

    /**
     * Removed the provided object type from the provided digital object. Both
     * arguments must be existing, persisted entities. If there is no mapping
     * between both entities, the call just returns and logs an info message.
     * Otherwise, the mapping is removed..
     *
     * @param pInputObject The input object.
     * @param pType The digital object type to remove.
     * @param pContext The context used to authorize the access.
     *
     * @throws UnauthorizedAccessAttemptException if pContext is not authorized
     * to perform the operaion.
     */
    public static void removeTypeFromObject(DigitalObject pInputObject, DigitalObjectType pType, IAuthorizationContext pContext) throws UnauthorizedAccessAttemptException {
        if (pInputObject == null) {
            throw new IllegalArgumentException("Argument pInputObject should not be null.");
        }

        if (pType == null) {
            throw new IllegalArgumentException("Argument pType should not be null.");
        }

        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(pContext);
        ObjectTypeMapping existingMapping = mdm.findSingleResult("SELECT m FROM ObjectTypeMapping m WHERE m.digitalObject.baseId=" + pInputObject.getBaseId() + " AND m.objectType.id=" + pType.getId(), ObjectTypeMapping.class);
        if (existingMapping == null) {
            //no mapping exist...do nothing.
            LOGGER.info("No existing mapping found for base id {} and type id {}. Skip removal.", pInputObject.getBaseId(), pType.getId());
        } else {
            LOGGER.debug("Existing mapping found for base id {} and type id {}. Removing entity.", pInputObject.getBaseId(), pType.getId());
            try {
                mdm.remove(existingMapping);
            } catch (EntityNotFoundException ex) {
                LOGGER.warn("Failed to remove object mapping due to EntityNotFoundException. Actually, this should never happen so I'll ignore it.", ex);
            } finally {
                mdm.close();
            }
        }
    }

}
