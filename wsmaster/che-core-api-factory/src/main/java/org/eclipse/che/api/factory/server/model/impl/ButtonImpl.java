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

import org.eclipse.che.api.factory.shared.model.Button;
import org.eclipse.che.api.factory.shared.model.ButtonAttributes;

import java.util.Objects;

/**
 * Data object for {@link Button}.
 *
 * @author Anton Korneta
 */
public class ButtonImpl implements Button {

    private ButtonAttributesImpl attributes;
    private ButtonType           type;

    public ButtonImpl(ButtonAttributesImpl attributes,
                      ButtonType type) {
        this.attributes = attributes;
        this.type = type;
    }

    public ButtonImpl(Button button) {
        this(new ButtonAttributesImpl(button.getAttributes()), button.getType());
    }

    @Override
    public ButtonAttributes getAttributes() {
        return attributes;
    }

    @Override
    public ButtonType getType() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ButtonImpl)) return false;
        final ButtonImpl other = (ButtonImpl)obj;
        return Objects.equals(attributes, other.attributes)
               && Objects.equals(type, other.type);
    }

    @Override
    public int hashCode() {
        int result = 7;
        result = 31 * result + Objects.hashCode(attributes);
        result = 31 * result + Objects.hashCode(type);
        return result;
    }

    @Override
    public String toString() {
        return "ButtonImpl{" +
               "attributes=" + attributes +
               ", type=" + type +
               '}';
    }
}
