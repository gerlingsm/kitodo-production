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

package org.kitodo.api.dataformat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The administrative structure of the product of an element that passes through
 * a Production workflow.
 */
public class Workpiece {
    /**
     * The time this file was first created.
     */
    private GregorianCalendar creationDate = new GregorianCalendar();

    /**
     * The processing history.
     */
    private List<ProcessingNote> editHistory = new ArrayList<>();

    /**
     * The identifier of the workpiece.
     */
    private String id;

    /**
     * The media unit that belongs to this workpiece. The media unit can have
     * children, such as a bound book that can have pages.
     */
    private MediaUnit mediaUnit = new MediaUnit();

    /**
     * The logical included structural element.
     */
    private IncludedStructuralElement rootElement = new IncludedStructuralElement();

    /**
     * Returns the creation date of the workpiece.
     *
     * @return the creation date
     */
    public GregorianCalendar getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date of the workpiece.
     *
     * @param creationDate
     *            creation date to set
     */
    public void setCreationDate(GregorianCalendar creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Returns the edit history.
     *
     * @return the edit history
     */
    public List<ProcessingNote> getEditHistory() {
        return editHistory;
    }

    /**
     * Returns the ID of the workpiece.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the ID of the workpiece.
     *
     * @param id
     *            ID to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the media unit of this workpiece.
     *
     * @return the media units
     */
    public MediaUnit getMediaUnit() {
        return mediaUnit;
    }

    /**
     * Returns the media units of this workpiece.
     *
     * @return the media units
     * @deprecated Use {@code getMediaUnit().getChildren()}.
     */
    @Deprecated
    public List<MediaUnit> getMediaUnits() {
        return mediaUnit.getChildren();
    }

    /**
     * Returns the root element of the included structural element.
     *
     * @return root element of the included structural element
     */
    public IncludedStructuralElement getRootElement() {
        return rootElement;
    }

    /**
     * Sets the media unit of the workpiece.
     *
     * @param mediaUnit
     *            media unit to set
     */
    public void setMediaUnit(MediaUnit mediaUnit) {
        this.mediaUnit = mediaUnit;
    }

    /**
     * Sets the included structural element of the workpiece.
     *
     * @param rootElement
     *            included structural element to set
     */
    public void setRootElement(IncludedStructuralElement rootElement) {
        this.rootElement = rootElement;
    }

    @Override
    public String toString() {
        return id + ", " + rootElement;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;
        hashCode = prime * hashCode + ((id == null) ? 0 : id.hashCode());
        return hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Workpiece workpiece = (Workpiece) o;
        return Objects.equals(creationDate, workpiece.creationDate)
                && Objects.equals(editHistory, workpiece.editHistory)
                && Objects.equals(id, workpiece.id)
                && Objects.equals(mediaUnit, workpiece.mediaUnit)
                && Objects.equals(rootElement, workpiece.rootElement);
    }

    /**
     * Returns all included structural elements of the root element of the
     * workpiece as a flat list. The list isn’t backed by the included
     * structural elements, which means that insertions and deletions in the
     * list would not change the included structural elements. Therefore a list
     * that cannot be modified is returned.
     *
     * @return all included structural elements of the workpiece as an
     *         unmodifiable list
     */
    public List<IncludedStructuralElement> getAllIncludedStructuralElements() {
        return Collections.unmodifiableList(treeStream(rootElement).collect(Collectors.toList()));
    }

    /**
     * Returns all media units of the media unit of the workpiece sorted by
     * their {@code order} as a flat list. The list isn’t backed by the media
     * units, which means that insertions and deletions in the list would not
     * change the media units. Therefore a list that cannot be modified is
     * returned.
     *
     * @return all media units of the workpiece sorted by their {@code order} as
     *         an unmodifiable list
     */
    public List<MediaUnit> getAllMediaUnitsSorted() {
        List<MediaUnit> mediaUnits = treeStream(mediaUnit).collect(Collectors.toList());
        mediaUnits.sort(Comparator.comparing(MediaUnit::getOrder));
        return Collections.unmodifiableList(mediaUnits);
    }

    /**
     * Returns all media units of the media unit of the workpiece as a flat
     * list. The list isn’t backed by the media units, which means that
     * insertions and deletions in the list would not change the media units.
     * Therefore a list that cannot be modified is returned.
     *
     * @return all media units of the workpiece as an unmodifiable list
     */
    public List<MediaUnit> getAllMediaUnits() {
        return Collections.unmodifiableList(treeStream(mediaUnit).collect(Collectors.toList()));
    }

    /**
     * Generates a stream of nodes from structure tree.
     *
     * @param tree
     *            starting node
     * @return all nodes as stream
     */
    @SuppressWarnings("unchecked")
    public static <T extends Parent<T>> Stream<T> treeStream(Parent<T> tree) {
        return Stream.concat(Stream.of((T) tree), tree.getChildren().stream().flatMap(child -> treeStream(child)));
    }
}
