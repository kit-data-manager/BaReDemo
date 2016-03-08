/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.kit.dama.ui.repo.staging;

import edu.kit.dama.authorization.entities.GroupId;
import edu.kit.dama.authorization.entities.IAuthorizationContext;
import edu.kit.dama.authorization.entities.ReferenceId;
import edu.kit.dama.authorization.entities.Role;
import edu.kit.dama.authorization.entities.UserId;
import edu.kit.dama.authorization.entities.impl.AuthorizationContext;
import edu.kit.dama.authorization.exceptions.EntityAlreadyExistsException;
import edu.kit.dama.authorization.exceptions.EntityNotFoundException;
import edu.kit.dama.authorization.exceptions.UnauthorizedAccessAttemptException;
import edu.kit.dama.authorization.services.administration.ResourceServiceLocal;
import edu.kit.dama.commons.exceptions.ConfigurationException;
import edu.kit.dama.commons.exceptions.PropertyValidationException;
import edu.kit.dama.mdm.base.DigitalObject;
import edu.kit.dama.mdm.core.IMetaDataManager;
import edu.kit.dama.mdm.core.MetaDataManagement;
import edu.kit.dama.rest.staging.types.TransferTaskContainer;
import edu.kit.dama.staging.exceptions.StagingProcessorException;
import edu.kit.dama.staging.processor.AbstractStagingProcessor;
import edu.kit.dama.util.Constants;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Staging Processor responsible for changing permissions to newly created
 * objects. By default, all digital objects ingested in the repository system
 * can be accessed by the group in whose name the ingest was done with the
 * permissions a user has within this group. This staging processor does two
 * things: It enables to assign access grants on a single user basis in order to
 * share digital objects with single user and it removes all group permissions
 * and replaces them by allowing READ access for the group USERS. Afterwards,
 * every registered member of the repository is able to read the object as USERS
 * is the default user group everybody should be in. Furthermore, a dummy user
 * who is also member of this group can be used to realize open access to the
 * objects.
 *
 * @author jejkal
 */
public class ChangePermissionProcessor extends AbstractStagingProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangePermissionProcessor.class);

    /**
     * Default constructor.
     *
     * @param pUniqueIdentifier The unique identifier.
     */
    public ChangePermissionProcessor(String pUniqueIdentifier) {
        super(pUniqueIdentifier);
    }

    @Override
    public String getName() {
        return "ChangePermissionProcessor";
    }

    @Override
    public String[] getInternalPropertyKeys() {
        return new String[]{};
    }

    @Override
    public String getInternalPropertyDescription(String pKey) {
        return "";
    }

    @Override
    public String[] getUserPropertyKeys() {
        return new String[]{};
    }

    @Override
    public String getUserPropertyDescription(String pKey) {
        return "";
    }

    @Override
    public void validateProperties(Properties pProperties) throws PropertyValidationException {
    }

    @Override
    public void configure(Properties pProperties) throws PropertyValidationException, ConfigurationException {
    }

    @Override
    public void performPreTransferProcessing(TransferTaskContainer pContainer) throws StagingProcessorException {
    }

    @Override
    public void finalizePreTransferProcessing(TransferTaskContainer pContainer) throws StagingProcessorException {
    }

    @Override
    public void performPostTransferProcessing(TransferTaskContainer pContainer) throws StagingProcessorException {
        LOGGER.debug("Changing access permissions");
        IAuthorizationContext ctx = AuthorizationContext.factorySystemContext();
        String objectId = pContainer.getTransferInformation().getDigitalObjectId();
        IMetaDataManager mdm = MetaDataManagement.getMetaDataManagement().getMetaDataManager();
        mdm.setAuthorizationContext(ctx);
        try {
            LOGGER.debug("Obtaining digital object.");
            DigitalObject object = mdm.findSingleResult("SELECT o FROM DigitalObject o WHERE o.digitalObjectIdentifier='" + objectId + "'", DigitalObject.class);
            LOGGER.debug("Enabling grants for digital object with id {}", object.getDigitalObjectIdentifier());
            ResourceServiceLocal.getSingleton().allowGrants(object.getSecurableResourceId(), Role.MANAGER, ctx);
            LOGGER.debug("Adding grant for user with id {}", pContainer.getTransferInformation().getOwnerId());
            ResourceServiceLocal.getSingleton().addGrant(object.getSecurableResourceId(), new UserId(pContainer.getTransferInformation().getOwnerId()), Role.MANAGER, ctx);
            LOGGER.debug("Obtaining existing references.");
            List<ReferenceId> references = ResourceServiceLocal.getSingleton().getReferences(object.getSecurableResourceId(), ctx);
            LOGGER.debug("Removing {} existing references", references.size());
            for (ReferenceId reference : references) {
                LOGGER.debug(" - Removing reference for group {}", reference.getGroupId());
                ResourceServiceLocal.getSingleton().deleteReference(reference, ctx);
            }
            LOGGER.debug("Adding GUEST access for default group USERS");
            ReferenceId refId = new ReferenceId(object.getSecurableResourceId(), new GroupId(Constants.USERS_GROUP_ID));
            ResourceServiceLocal.getSingleton().createReference(refId, Role.GUEST, ctx);
            LOGGER.debug("Successfully changed access permissions.");
        } catch (UnauthorizedAccessAttemptException | EntityNotFoundException | EntityAlreadyExistsException ex) {
            throw new StagingProcessorException("Failed to update access permissions.", ex);
        }

    }

    @Override
    public void finalizePostTransferProcessing(TransferTaskContainer pContainer) throws StagingProcessorException {
    }
}
