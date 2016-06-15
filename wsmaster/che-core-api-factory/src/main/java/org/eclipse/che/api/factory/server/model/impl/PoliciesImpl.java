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

import org.eclipse.che.api.factory.shared.model.Policies;

import java.util.Objects;

/**
 * Data object for {@link Policies}.
 *
 * @author Anton Korneta
 */
public class PoliciesImpl implements Policies {

    public static PoliciesImplBuilder builder() {
        return new PoliciesImplBuilder();
    }

    private String referer;
    private String match;
    private String create;
    private Long   until;
    private Long   since;

    public PoliciesImpl(String referer,
                        String match,
                        String create,
                        Long until,
                        Long since) {
        this.referer = referer;
        this.match = match;
        this.create = create;
        this.until = until;
        this.since = since;
    }

    public PoliciesImpl(Policies policies) {
        this(policies.getReferer(),
             policies.getMatch(),
             policies.getCreate(),
             policies.getUntil(),
             policies.getSince());
    }

    @Override
    public String getReferer() {
        return referer;
    }

    @Override
    public String getMatch() {
        return match;
    }

    @Override
    public String getCreate() {
        return create;
    }

    @Override
    public Long getUntil() {
        return until;
    }

    @Override
    public Long getSince() {
        return since;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PoliciesImpl)) return false;
        final PoliciesImpl other = (PoliciesImpl)obj;
        return Objects.equals(referer, other.referer)
               && Objects.equals(match, other.match)
               && Objects.equals(create, other.create)
               && Objects.equals(until, other.until)
               && Objects.equals(since, other.since);
    }

    @Override
    public int hashCode() {
        int result = 7;
        result = 31 * result + Objects.hashCode(referer);
        result = 31 * result + Objects.hashCode(match);
        result = 31 * result + Objects.hashCode(create);
        result = 31 * result + Long.hashCode(until);
        result = 31 * result + Long.hashCode(since);
        return result;
    }

    @Override
    public String toString() {
        return "PoliciesImpl{" +
               "referer='" + referer + '\'' +
               ", match='" + match + '\'' +
               ", create='" + create + '\'' +
               ", until=" + until +
               ", since=" + since +
               '}';
    }

    /**
     * Helps to create the instance of {@link PoliciesImpl}.
     */
    public static class PoliciesImplBuilder {

        private String referer;
        private String match;
        private String create;
        private Long   until;
        private Long   since;

        public PoliciesImpl build() {
            return new PoliciesImpl(referer, match, create, until, since);
        }

        public PoliciesImplBuilder() {}

        public PoliciesImplBuilder setReferer(String referer) {
            this.referer = referer;
            return this;
        }

        public PoliciesImplBuilder setMatch(String match) {
            this.match = match;
            return this;
        }

        public PoliciesImplBuilder setCreate(String create) {
            this.create = create;
            return this;
        }

        public PoliciesImplBuilder setUntil(Long until) {
            this.until = until;
            return this;
        }

        public PoliciesImplBuilder setSince(Long since) {
            this.since = since;
            return this;
        }
    }
}
