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

package org.kitodo.data.database.beans;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "search_field")
public class SearchField extends BaseBean {

    @Column(name = "field_label")
    private String label;

    @Column(name = "field_value")
    private String value;

    // TODO: invert (eg.g "show" instead of "hide")
    @Column(name = "hidden")
    private boolean hidden;

    @Column(name = "parent_element")
    private boolean parentElement;

    @ManyToOne
    @JoinColumn(name = "import_configuration_id",
            foreignKey = @ForeignKey(name = "FK_search_field_import_configuration_id"))
    private ImportConfiguration importConfiguration;

    /**
     * Get label.
     *
     * @return value of label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Set label.
     *
     * @param label as java.lang.String
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Get value.
     *
     * @return value of value
     */
    public String getValue() {
        return value;
    }

    /**
     * Set value.
     *
     * @param value as java.lang.String
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Get hidden.
     *
     * @return value of hidden
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * Set hidden.
     *
     * @param hidden as boolean
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * Get parentElement.
     *
     * @return value of parentElement
     */
    public boolean isParentElement() {
        return parentElement;
    }

    /**
     * Set parentElement.
     *
     * @param parentElement as boolean
     */
    public void setParentElement(boolean parentElement) {
        this.parentElement = parentElement;
    }

    /**
     * Get importConfiguration.
     *
     * @return value of importConfiguration
     */
    public ImportConfiguration getImportConfiguration() {
        return importConfiguration;
    }

    /**
     * Set importConfiguration.
     *
     * @param importConfiguration as org.kitodo.data.database.beans.ImportConfiguration
     */
    public void setImportConfiguration(ImportConfiguration importConfiguration) {
        this.importConfiguration = importConfiguration;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof SearchField) {
            SearchField searchField = (SearchField) object;
            return Objects.equals(this.getId(), searchField.getId());
        }
        return false;
    }

}
