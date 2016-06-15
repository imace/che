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

import java.util.Map;

/**
 * Defines ide action model.
 *
 * @author Anton Korneta
 */
public interface Action {

    /**
     * Returns the identifier of this action instance.
     */
    String getId();

    /**
     * Returns properties of this action instance.
     */
    Map<String, String> getProperties();
}
