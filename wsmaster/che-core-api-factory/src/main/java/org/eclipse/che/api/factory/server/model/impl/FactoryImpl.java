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

import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.factory.server.FactoryImage;
import org.eclipse.che.api.factory.shared.model.Button;
import org.eclipse.che.api.factory.shared.model.Factory;
import org.eclipse.che.api.factory.shared.model.Ide;
import org.eclipse.che.api.factory.shared.model.Policies;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.commons.lang.NameGenerator;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Data object for {@link Factory}.
 *
 * @author Anton Korneta
 */
public class FactoryImpl implements Factory {

    public static FactoryImplBuilder builder() {
        return new FactoryImplBuilder();
    }

    private final String              id;
    private final String              name;
    private final String              version;
    private final WorkspaceConfigImpl workspace;
    private final AuthorImpl          creator;
    private final PoliciesImpl        policies;
    private final ButtonImpl          button;
    private       IdeImpl             ide;
    private       Set<FactoryImage>   images;

    public FactoryImpl(String id,
                       String name,
                       String version,
                       WorkspaceConfig workspace,
                       AuthorImpl creator,
                       PoliciesImpl policies,
                       Ide ide,
                       ButtonImpl button,
                       Set<FactoryImage> images) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.workspace = new WorkspaceConfigImpl(workspace);
        this.creator = creator;
        this.policies = policies;
        if (ide != null) {
            this.ide = new IdeImpl(ide);
        }
        this.button = button;
        this.images = images;
    }

    public FactoryImpl(Factory factory, Set<FactoryImage> images) {
        this(factory.getId(),
             factory.getName(),
             factory.getV(),
             new WorkspaceConfigImpl(factory.getWorkspace()),
             new AuthorImpl(factory.getCreator()),
             new PoliciesImpl(factory.getPolicies()),
             new IdeImpl(factory.getIde()),
             new ButtonImpl(factory.getButton()),
             images);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getV() {
        return version;
    }

    @Override
    public WorkspaceConfigImpl getWorkspace() {
        return workspace;
    }

    @Override
    public AuthorImpl getCreator() {
        return creator;
    }

    @Override
    public Policies getPolicies() {
        return policies;
    }

    @Override
    public Button getButton() {
        return button;
    }

    @Override
    public IdeImpl getIde() {
        return ide;
    }

    public Set<FactoryImage> getImages() {
        if (images == null) {
            images = new HashSet<>();
        }
        return images;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FactoryImpl)) return false;
        final FactoryImpl other = (FactoryImpl)obj;
        return Objects.equals(id, other.id)
               && Objects.equals(name, other.name)
               && Objects.equals(version, other.version)
               && Objects.equals(workspace, other.workspace)
               && Objects.equals(creator, other.creator)
               && Objects.equals(policies, other.policies)
               && Objects.equals(ide, other.ide)
               && Objects.equals(button, other.button)
               && getImages().equals(other.getImages());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + Objects.hashCode(id);
        hash = 31 * hash + Objects.hashCode(name);
        hash = 31 * hash + Objects.hashCode(version);
        hash = 31 * hash + Objects.hashCode(workspace);
        hash = 31 * hash + Objects.hashCode(creator);
        hash = 31 * hash + Objects.hashCode(policies);
        hash = 31 * hash + Objects.hashCode(ide);
        hash = 31 * hash + Objects.hashCode(button);
        hash = 31 * hash + getImages().hashCode();
        return hash;
    }

    @Override
    public String toString() {
        return "FactoryImpl{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", version='" + version + '\'' +
               ", workspace=" + workspace +
               ", creator=" + creator +
               ", policies=" + policies +
               ", ide=" + ide +
               ", button=" + button +
               ", images=" + images +
               '}';
    }

    /**
     * Helps to create the instance of {@link FactoryImpl}.
     */
    public static class FactoryImplBuilder {

        private String              id;
        private String              name;
        private String              version;
        private WorkspaceConfigImpl workspace;
        private AuthorImpl          creator;
        private PoliciesImpl        policies;
        private IdeImpl             ide;
        private ButtonImpl          button;
        private Set<FactoryImage>   images;

        private FactoryImplBuilder() {}

        public FactoryImpl build() {
            return new FactoryImpl(id, name, version, workspace, creator, policies, ide, button, images);
        }

        public FactoryImplBuilder generateId() {
            id = NameGenerator.generate("", 16);
            return this;
        }

        public FactoryImplBuilder setId(String id) {
            this.id = id;
            return this;
        }

        public FactoryImplBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public FactoryImplBuilder setVersion(String version) {
            this.version = version;
            return this;
        }

        public FactoryImplBuilder setWorkspace(WorkspaceConfig workspace) {
            this.workspace = new WorkspaceConfigImpl(workspace);
            return this;
        }

        public FactoryImplBuilder setCreator(AuthorImpl creator) {
            this.creator = creator;
            return this;
        }

        public FactoryImplBuilder setPolicies(PoliciesImpl policies) {
            this.policies = policies;
            return this;
        }

        public FactoryImplBuilder setIde(IdeImpl ide) {
            this.ide = ide;
            return this;
        }

        public FactoryImplBuilder setButton(ButtonImpl button) {
            this.button = button;
            return this;
        }

        public FactoryImplBuilder setImages(Set<FactoryImage> images) {
            this.images = images;
            return this;
        }
    }
}
