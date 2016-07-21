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
package org.eclipse.che.api.factory.server;

import javax.persistence.Basic;
import javax.persistence.Embeddable;
import java.util.Arrays;

/** Class to hold image information such as data, name, media type */
@Embeddable
public class FactoryImage {

    @Basic
    private byte[] imageData;

    @Basic
    private String mediaType;

    @Basic
    private String name;

    public FactoryImage() {}

    public FactoryImage(byte[] data, String mediaType, String name) {
        setMediaType(mediaType);
        this.name = name;
        setImageData(data);
    }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        if (mediaType != null) {
            switch (mediaType) {
                case "image/jpeg":
                case "image/png":
                case "image/gif":
                    this.mediaType = mediaType;
                    return;
                default:
                    throw new IllegalArgumentException("Image media type '" + mediaType + "' is unsupported.");
            }
        }
        throw new IllegalArgumentException("Image media type 'null' is unsupported.");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasContent() {
        return imageData != null && imageData.length > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FactoryImage)) return false;

        FactoryImage that = (FactoryImage)o;

        if (!Arrays.equals(imageData, that.imageData)) return false;
        if (mediaType != null ? !mediaType.equals(that.mediaType) : that.mediaType != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = imageData != null ? Arrays.hashCode(imageData) : 0;
        result = 31 * result + (mediaType != null ? mediaType.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
