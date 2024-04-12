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

package org.kitodo.data.interfaces;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * An interface to manage process properties. Properties are key-value pairs
 * that can be added to processes by third-party modules. They should not be
 * used to store technical metadata. Such should be part of the business domain.
 */
public interface PropertyInterface extends DataInterface {
    /**
     * Returns the key of the property's key-value pair.
     *
     * @return the key
     */
    String getTitle();

    /**
     * Sets the key of the property's key-value pair.
     *
     * @param title
     *            key to set
     */
    void setTitle(String title);

    /**
     * Returns the value of the property's key-value pair.
     *
     * @return the value
     */
    String getValue();

    /**
     * Sets the value of the property's key-value pair.
     *
     * @param value
     *            value to set
     */
    void setValue(String value);

    /**
     * Returns the creation time of the property. The string is formatted
     * according to {@link SimpleDateFormat}{@code ("yyyy-MM-dd HH:mm:ss")}.
     *
     * @return the creation time
     * @deprecated Use {@link #getCreationDate()}.
     */
    @Deprecated
    default String getCreationTime() {
        Date creationDate = getCreationDate();
        return Objects.nonNull(creationDate) ? new SimpleDateFormat(DATE_FORMAT).format(creationDate) : null;
    }

    /**
     * Returns the creation time of the property. {@link Date} is a specific
     * instant in time, with millisecond precision.
     *
     * @return the creation time
     */
    Date getCreationDate();

    /**
     * Sets the creation time of the property. The string must be parsable with
     * {@link SimpleDateFormat}{@code ("yyyy-MM-dd HH:mm:ss")}.
     *
     * @param creationDate
     *            creation time to set
     * @throws ParseException
     *             if the time cannot be converted
     * @deprecated Use {@link #setCreationDate(Date)}.
     */
    @Deprecated
    default void setCreationTime(String creationDate) throws ParseException {
        setCreationDate(Objects.nonNull(creationDate) ? new SimpleDateFormat(DATE_FORMAT).parse(creationDate) : null);
    }

    /**
     * Sets the creation time of the property. The string must be parsable with
     * {@link SimpleDateFormat}{@code ("yyyy-MM-dd HH:mm:ss")}.
     *
     * @param creationDate
     *            creation time to set
     * @throws ParseException
     *             if the time cannot be converted
     */
    void setCreationDate(Date creationDate);
}
