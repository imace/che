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
package org.eclipse.che.api.factory.server.model.impl;

import org.eclipse.che.api.factory.shared.model.Action;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data object for {@link Action}.
 *
 * @author Anton Korneta
 */
public class ActionImpl implements Action {

    private final String              id;
    private       Map<String, String> properties;

    public ActionImpl(String id, Map<String, String> properties) {
        this.id = id;
        this.properties = properties;
    }

    public ActionImpl(Action action) {
        this(action.getId(), action.getProperties());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, String> getProperties() {
        if (properties == null) {
            return new HashMap<>();
        }
        return properties;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ActionImpl)) return false;
        final ActionImpl other = (ActionImpl)obj;
        return Objects.equals(id, other.getId())
               && getProperties().equals(other.getProperties());
    }

    @Override
    public int hashCode() {
        int result = 7;
        result = 31 * result + Objects.hashCode(id);
        result = 31 * result + getProperties().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ActionImpl{" +
               "id='" + id + '\'' +
               ", properties=" + properties +
               '}';
    }
}
