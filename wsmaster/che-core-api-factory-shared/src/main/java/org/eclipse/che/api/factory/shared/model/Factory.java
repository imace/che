/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.factory.shared.model;

import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;

/**
 * Defines factory model and specifies contract for factory instances.
 *
 * @author Anton Korneta
 */
public interface Factory {

    /**
     * Returns the identifier of this factory instance.
     * It is mandatory and unique.
     */
    String getId();

    /**
     * Returns the version of this factory instance.
     * It is mandatory for every factory instance.
     */
    String getV();

    /**
     * Returns a name of this factory instance,
     * the name is unique for creator.
     */
    String getName();

    /**
     * Returns author of this factory instance.
     */
    Author getCreator();

    /**
     * Returns a workspace configuration of this factory instance.
     * It is mandatory for every factory instance.
     */
    WorkspaceConfig getWorkspace();

    /**
     * Returns restrictions of this factory instance.
     */
    Policies getPolicies();

    /**
     * Returns factory button for this instance.
     */
    Button getButton();

    /**
     * Returns ide behavior of this factory instance.
     */
    Ide getIde();
}
