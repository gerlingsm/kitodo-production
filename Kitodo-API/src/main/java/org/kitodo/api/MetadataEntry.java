/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.api;

import java.util.Objects;

public class MetadataEntry extends Metadata {

    /**
     * The value of the metadata.
     */
    private String value;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MetadataEntry other = (MetadataEntry) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if ((Objects.nonNull(domain) ? domain : MdSec.DMD_SEC) != (Objects.nonNull(other.domain) ? other.domain
                : MdSec.DMD_SEC)) {
            return false;
        }
        if (value == null) {
            if (other.value != null) {
                return false;
            }
        } else if (!value.equals(other.value)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (Objects.nonNull(domain) ? domain : MdSec.DMD_SEC).hashCode();
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return (Objects.nonNull(domain) ? "(" + domain + ") " : "") + key + ": \"" + value + '"';
    }

    /**
     * Get the value of the metadata entry.
     *
     * @return The value of the metadata.
     */
    public String getValue() {
        if (Objects.isNull(value)) {
            throw new NullPointerException("Trying to get metadata value that was never set");
        }
        return value;
    }

    /**
     * Set the value of the metadata entry.
     *
     * @param value
     *            The value of the metadata entry.
     */
    public void setValue(String value) {
        if (Objects.isNull(value)) {
            throw new NullPointerException("Metadata value must not be null");
        }
        this.value = value;
    }
}
