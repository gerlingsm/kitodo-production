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

package org.kitodo.production.services.schema;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.kitodo.api.MdSec;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataformat.LogicalStructure;
import org.kitodo.api.dataformat.PhysicalStructure;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.LinkedMetsResource;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.enums.LinkingMode;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.helper.VariableReplacer;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;

/**
 * Service for schema manipulations.
 */
public class SchemaService {

    /**
     * Temporal method for separate file conversion from ExportMets class
     * (method writeMetsFile).
     *
     * @param workpiece
     *            class inside method is used
     * @param prefs
     *            preferences - Prefs object
     * @param process
     *            object
     */
    public void tempConvert(Workpiece workpiece, LegacyPrefsHelper prefs,
            Process process) throws IOException, DAOException, URISyntaxException {
        /*
         * wenn Filegroups definiert wurden, werden diese jetzt in die
         * Metsstruktur übernommen
         */
        // Replace all paths with the given VariableReplacer, also the file
        // group paths!
        VariableReplacer vp = new VariableReplacer(
                new LegacyMetsModsDigitalDocumentHelper(prefs.getRuleset(), workpiece), prefs,
                process, null);

        addVirtualFileGroupsToMetsMods(workpiece.getPhysicalStructureRoot(), process);
        replaceFLocatForExport(workpiece, process);

        // Replace rights and digiprov entries.
        set(workpiece, MdSec.RIGHTS_MD, "owner", vp.replace(process.getProject().getMetsRightsOwner()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerLogo", vp.replace(process.getProject().getMetsRightsOwnerLogo()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerSiteURL", vp.replace(process.getProject().getMetsRightsOwnerSite()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerContact", vp.replace(process.getProject().getMetsRightsOwnerMail()));
        set(workpiece, MdSec.DIGIPROV_MD, "presentation",
            vp.replace(process.getProject().getMetsDigiprovPresentation()));
        set(workpiece, MdSec.DIGIPROV_MD, "reference", vp.replace(process.getProject().getMetsDigiprovReference()));

        set(workpiece, MdSec.TECH_MD, "purlUrl", vp.replace(process.getProject().getMetsPurl()));
        set(workpiece, MdSec.TECH_MD, "contentIDs", vp.replace(process.getProject().getMetsContentIDs()));

        convertChildrenLinksForExportRecursive(workpiece, workpiece.getLogicalStructureRoot(), prefs);
        Process parentProcess = process.getParent();
        while (Objects.nonNull(parentProcess)) {
            addParentLinkForExport(prefs, workpiece, parentProcess);
            parentProcess = parentProcess.getParent();
        }

        assignViewsFromChildrenRecursive(workpiece.getLogicalStructureRoot());
    }

    /**
     * At all levels, assigns the views of the children to the included
     * structural elements.
     *
     * @param logicalStructure
     *            logical structure on which the recursion is performed
     */
    private void assignViewsFromChildrenRecursive(LogicalStructure logicalStructure) {
        List<LogicalStructure> children = logicalStructure.getChildren();
        if (!children.isEmpty()) {
            for (LogicalStructure child : children) {
                assignViewsFromChildrenRecursive(child);
            }
            if (!Objects.nonNull(logicalStructure.getType())) {
                MetadataEditor.assignViewsFromChildren(logicalStructure);
            }
        }
    }

    private void set(Workpiece workpiece, MdSec domain, String key, String value) {
        MetadataEntry entry = new MetadataEntry();
        entry.setKey(key);
        entry.setDomain(domain);
        entry.setValue(value);
        workpiece.getLogicalStructureRoot().getMetadata().add(entry);
    }

    private void addVirtualFileGroupsToMetsMods(PhysicalStructure physicalStructureRoot, Process process) {
        String canonical = ServiceManager.getFolderService().getCanonical(process, physicalStructureRoot);
        if (Objects.nonNull(canonical)) {
            removeFLocatsForUnwantedUses(process, physicalStructureRoot, canonical);
            addMissingUses(process, physicalStructureRoot, canonical);
        }
        for (PhysicalStructure child : physicalStructureRoot.getChildren()) {
            addVirtualFileGroupsToMetsMods(child, process);
        }
    }

    private void replaceFLocatForExport(Workpiece workpiece, Process process) throws URISyntaxException {
        List<Folder> folders = process.getProject().getFolders();
        VariableReplacer variableReplacer = new VariableReplacer(null, null, process, null);
        for (PhysicalStructure physicalStructure : workpiece.getMediaUnits()) {
            for (Entry<MediaVariant, URI> mediaFileForMediaVariant : physicalStructure.getMediaFiles().entrySet()) {
                for (Folder folder : folders) {
                    if (folder.getFileGroup().equals(mediaFileForMediaVariant.getKey().getUse())) {
                        int lastSeparator = mediaFileForMediaVariant.getValue().toString().lastIndexOf(File.separator);
                        String lastSegment = mediaFileForMediaVariant.getValue().toString()
                                .substring(lastSeparator + 1);
                        mediaFileForMediaVariant
                                .setValue(new URI(variableReplacer.replace(folder.getUrlStructure() + lastSegment)));
                    }
                }
            }
        }
    }

    /**
     * If the media unit contains a media variant that is unknown, has linking
     * mode NO or has linking mode EXISTING but the file does not exist, remove
     * it.
     */
    private void removeFLocatsForUnwantedUses(Process process,
            PhysicalStructure physicalStructure,
            String canonical) {
        for (Iterator<Entry<MediaVariant, URI>> mediaFilesForMediaVariants = physicalStructure.getMediaFiles().entrySet()
                .iterator(); mediaFilesForMediaVariants.hasNext();) {
            Entry<MediaVariant, URI> mediaFileForMediaVariant = mediaFilesForMediaVariants.next();
            String use = mediaFileForMediaVariant.getKey().getUse();
            Optional<Folder> optionalFolderForUse = process.getProject().getFolders().parallelStream()
                    .filter(folder -> use.equals(folder.getFileGroup())).findAny();
            if (!optionalFolderForUse.isPresent()
                    || optionalFolderForUse.get().getLinkingMode().equals(LinkingMode.NO)
                    || (optionalFolderForUse.get().getLinkingMode().equals(LinkingMode.EXISTING)
                            && new Subfolder(process, optionalFolderForUse.get()).getURIIfExists(canonical)
                                    .isPresent())) {
                mediaFilesForMediaVariants.remove();
            }
        }
    }

    /**
     * If the media unit is missing a variant that has linking mode ALL or has
     * linking mode EXISTING and the file does exist, add it.
     */
    private void addMissingUses(Process process, PhysicalStructure physicalStructure,
            String canonical) {
        for (Folder folder : process.getProject().getFolders()) {
            Subfolder useFolder = new Subfolder(process, folder);
            if (physicalStructure.getMediaFiles().entrySet().parallelStream().map(Entry::getKey).map(MediaVariant::getUse)
                    .noneMatch(use -> use.equals(folder.getFileGroup())) && (folder.getLinkingMode().equals(LinkingMode.ALL)
                        || (folder.getLinkingMode().equals(LinkingMode.EXISTING) && useFolder.getURIIfExists(canonical).isPresent()))) {
                addUse(useFolder, canonical, physicalStructure);
            }
        }
    }

    /**
     * Adds a use to a media unit.
     *
     * @param useFolder
     *            use folder for the use
     * @param canonical
     *            the canonical part of the file name of the media file
     * @param physicalStructure
     *            media unit to add to
     */
    private void addUse(Subfolder useFolder, String canonical, PhysicalStructure physicalStructure) {
        MediaVariant mediaVariant = new MediaVariant();
        mediaVariant.setUse(useFolder.getFolder().getFileGroup());
        mediaVariant.setMimeType(useFolder.getFolder().getMimeType());
        URI mediaFile = useFolder.getUri(canonical);
        physicalStructure.getMediaFiles().put(mediaVariant, mediaFile);
    }

    /**
     * Replaces internal links in child structure elements with a publicly
     * resolvable link. Checks whether the linked process has not yet been
     * exported, in which case the link from the parental list will be deleted.
     *
     * @param workpiece
     *            current workpiece
     * @param structure
     *            current structure
     * @param prefs
     *            legacy ruleset wrapper
     * @return whether the current structure shall be deleted
     */
    private boolean convertChildrenLinksForExportRecursive(Workpiece workpiece, LogicalStructure structure,
                                               LegacyPrefsHelper prefs) throws DAOException, IOException {

        LinkedMetsResource link = structure.getLink();
        if (Objects.nonNull(link)) {
            int linkedProcessId = ServiceManager.getProcessService().processIdFromUri(link.getUri());
            Process process = ServiceManager.getProcessService().getById(linkedProcessId);
            if (!process.isExported()) {
                return true;
            }
            setLinkForExport(structure, process, prefs, workpiece);
        }
        for (Iterator<LogicalStructure> iterator = structure.getChildren().iterator(); iterator.hasNext();) {
            if (convertChildrenLinksForExportRecursive(workpiece, iterator.next(), prefs)) {
                iterator.remove();
            }
        }
        return false;
    }

    private void addParentLinkForExport(LegacyPrefsHelper prefs, Workpiece workpiece, Process parent)
            throws IOException {

        LogicalStructure linkHolder = new LogicalStructure();
        linkHolder.setLink(new LinkedMetsResource());
        setLinkForExport(linkHolder, parent, prefs, workpiece);
        linkHolder.getChildren().add(workpiece.getLogicalStructureRoot());
        workpiece.setLogicalStructureRoot(linkHolder);
    }

    private void setLinkForExport(LogicalStructure structure, Process process, LegacyPrefsHelper prefs,
            Workpiece workpiece) throws IOException {

        LinkedMetsResource link = structure.getLink();
        link.setLoctype("URL");
        String uriWithVariables = process.getProject().getMetsPointerPath();
        VariableReplacer variableReplacer = new VariableReplacer(
                new LegacyMetsModsDigitalDocumentHelper(prefs.getRuleset(), workpiece), prefs, process, null);
        String linkUri = variableReplacer.replace(uriWithVariables);
        link.setUri(URI.create(linkUri));
        structure.setType(ServiceManager.getProcessService().getBaseType(process));
    }
}
