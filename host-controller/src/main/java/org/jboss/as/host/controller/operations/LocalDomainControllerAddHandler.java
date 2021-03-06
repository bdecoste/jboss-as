/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_CONTROLLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOTE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.host.controller.HostControllerConfigurationPersister;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @version $Revision: 1.1 $
 */
public class LocalDomainControllerAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "write-local-domain-controller";
    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host")).build();

    private final ManagementResourceRegistration rootRegistration;
    private final HostControllerConfigurationPersister overallConfigPersister;
    private final HostFileRepository fileRepository;
    private final LocalHostControllerInfoImpl hostControllerInfo;
    private final ContentRepository contentRepository;
    private final DomainController domainController;
    private final ExtensionRegistry extensionRegistry;
    private final PathManagerService pathManager;

    public static LocalDomainControllerAddHandler getInstance(final ManagementResourceRegistration rootRegistration,
                                                                 final LocalHostControllerInfoImpl hostControllerInfo,
                                                                 final HostControllerConfigurationPersister overallConfigPersister,
                                                                 final HostFileRepository fileRepository,
                                                                 final ContentRepository contentRepository,
                                                                 final DomainController domainController,
                                                                 final ExtensionRegistry extensionRegistry,
                                                                 final PathManagerService pathManager) {
        return new LocalDomainControllerAddHandler(rootRegistration, hostControllerInfo, overallConfigPersister,
                fileRepository, contentRepository, domainController, extensionRegistry, pathManager);
    }

    protected LocalDomainControllerAddHandler(final ManagementResourceRegistration rootRegistration,
                                    final LocalHostControllerInfoImpl hostControllerInfo,
                                    final HostControllerConfigurationPersister overallConfigPersister,
                                    final HostFileRepository fileRepository,
                                    final ContentRepository contentRepository,
                                    final DomainController domainController,
                                    final ExtensionRegistry extensionRegistry,
                                    final PathManagerService pathManager) {
        this.rootRegistration = rootRegistration;
        this.overallConfigPersister = overallConfigPersister;
        this.fileRepository = fileRepository;
        this.hostControllerInfo = hostControllerInfo;
        this.contentRepository = contentRepository;
        this.domainController = domainController;
        this.extensionRegistry = extensionRegistry;
        this.pathManager = pathManager;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        ModelNode dc = model.get(DOMAIN_CONTROLLER);
        dc.get(LOCAL).setEmptyObject();

        if (dc.has(REMOTE)) {
            dc.remove(REMOTE);
        }

        if (context.isBooting()) {
            initializeDomain();
        } else {
            context.reloadRequired();
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                if (!context.isBooting()) {
                    context.revertReloadRequired();
                }
            }
        });
    }

    protected void initializeDomain() {
        hostControllerInfo.setMasterDomainController(true);
        overallConfigPersister.initializeDomainConfigurationPersister(false);

        domainController.initializeMasterDomainRegistry(rootRegistration, overallConfigPersister.getDomainPersister(), contentRepository, fileRepository, extensionRegistry, pathManager);
    }
}
