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

import org.eclipse.che.api.factory.shared.model.Ide;
import org.eclipse.che.api.factory.shared.model.OnAppClosed;
import org.eclipse.che.api.factory.shared.model.OnAppLoaded;
import org.eclipse.che.api.factory.shared.model.OnProjectsLoaded;

import java.util.Objects;

/**
 * Data object for {@link Ide}.
 *
 * @author Anton Korneta
 */
public class IdeImpl implements Ide {
    private OnAppLoadedImpl      onAppLoaded;
    private OnProjectsLoadedImpl onProjectsLoaded;
    private OnAppClosedImpl      onAppClosed;

    public IdeImpl(OnAppLoadedImpl onAppLoaded,
                   OnProjectsLoadedImpl onProjectsLoaded,
                   OnAppClosedImpl onAppClosed) {
        this.onAppLoaded = onAppLoaded;
        this.onProjectsLoaded = onProjectsLoaded;
        this.onAppClosed = onAppClosed;
    }

    public IdeImpl(Ide ide) {
        this(new OnAppLoadedImpl(ide.getOnAppLoaded()),
             new OnProjectsLoadedImpl(ide.getOnProjectsLoaded()),
             new OnAppClosedImpl(ide.getOnAppClosed()));
    }

    @Override
    public OnAppLoaded getOnAppLoaded() {
        return onAppLoaded;
    }

    @Override
    public OnProjectsLoaded getOnProjectsLoaded() {
        return onProjectsLoaded;
    }

    @Override
    public OnAppClosed getOnAppClosed() {
        return onAppClosed;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IdeImpl)) return false;
        final IdeImpl other = (IdeImpl)obj;
        return Objects.equals(onAppLoaded, other.onAppLoaded)
               && Objects.equals(onProjectsLoaded, other.onProjectsLoaded)
               && Objects.equals(onAppClosed, other.onAppClosed);
    }

    @Override
    public int hashCode() {
        int result = 7;
        result = 31 * result + Objects.hashCode(onAppLoaded);
        result = 31 * result + Objects.hashCode(onProjectsLoaded);
        result = 31 * result + Objects.hashCode(onAppClosed);
        return result;
    }

    @Override
    public String toString() {
        return "IdeImpl{" +
               "onAppLoaded=" + onAppLoaded +
               ", onProjectsLoaded=" + onProjectsLoaded +
               ", onAppClosed=" + onAppClosed +
               '}';
    }
}
