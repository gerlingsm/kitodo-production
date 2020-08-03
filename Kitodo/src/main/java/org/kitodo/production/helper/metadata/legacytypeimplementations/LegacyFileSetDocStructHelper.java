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

package org.kitodo.production.helper.metadata.legacytypeimplementations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kitodo.api.dataformat.PhysicalStructure;

/**
 * Connects a legacy file set its corresponding doc struct to a media units
 * list. This is a soldering class to keep legacy code operational which is
 * about to be removed. Do not use this class.
 */

public class LegacyFileSetDocStructHelper implements LegacyDocStructHelperInterface {

    /**
     * The media units list accessed via this soldering class.
     */
    private List<PhysicalStructure> physicalStructures;

    @Deprecated
    public LegacyFileSetDocStructHelper(List<PhysicalStructure> physicalStructures) {
        this.physicalStructures = physicalStructures;
    }

    @Override
    @Deprecated
    public void addMetadata(LegacyMetadataHelper metadata) {
        /*
         * Legacy code tries to add (empty) metadata entries here. I guess this
         * is a bug.
         */
    }

    @Override
    @Deprecated
    public List<LegacyDocStructHelperInterface> getAllChildren() {
        List<LegacyDocStructHelperInterface> allChildren = new ArrayList<>(physicalStructures.size());
        for (PhysicalStructure physicalStructure : physicalStructures) {
            allChildren.add(new LegacyInnerPhysicalDocStructHelper(physicalStructure));
        }
        return allChildren;
    }

    @Override
    @Deprecated
    public List<LegacyDocStructHelperInterface> getAllChildrenByTypeAndMetadataType(String page, String asterisk) {
        List<LegacyDocStructHelperInterface> allChildren = new ArrayList<>(physicalStructures.size());
        for (PhysicalStructure physicalStructure : physicalStructures) {
            allChildren.add(new LegacyInnerPhysicalDocStructHelper(physicalStructure));
        }
        return allChildren;
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadata() {
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public List<LegacyMetadataHelper> getAllMetadataByType(LegacyMetadataTypeHelper metadataType) {
        return Collections.emptyList();
    }
}
