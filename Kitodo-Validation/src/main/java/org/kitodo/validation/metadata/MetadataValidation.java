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

package org.kitodo.validation.metadata;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataeditor.rulesetmanagement.ComplexMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewWithValuesInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalStructure;
import org.kitodo.api.dataformat.PhysicalStructure;
import org.kitodo.api.dataformat.View;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.MetsXmlElementAccessInterface;
import org.kitodo.api.filemanagement.FileManagementInterface;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.api.validation.metadata.MetadataValidationInterface;
import org.kitodo.serviceloader.KitodoServiceLoader;

public class MetadataValidation implements MetadataValidationInterface {
    private static final Logger logger = LogManager.getLogger(MetadataValidation.class);

    /**
     * Message key if no media is assigned.
     */
    private static final String MESSAGE_MEDIA_MISSING = "metadataMediaError";

    /**
     * Message key if media is present but not assigned to a structure.
     */
    private static final String MESSAGE_MEDIA_UNASSIGNED = "metadataMediaUnassigned";

    /**
     * Message key if a structure has no media assigned.
     */
    private static final String MESSAGE_STRUCTURE_WITHOUT_MEDIA = "metadataStructureWithoutMedia";

    /**
     * Message key if the input is invalid.
     */
    private static final String MESSAGE_VALUE_INVALID = "metadataInvalidData";

    /**
     * Message key if the input is missing.
     */
    private static final String MESSAGE_VALUE_MISSING = "metadataMandatoryElement";

    /**
     * Message key if there are too many entries of a type.
     */
    private static final String MESSAGE_VALUE_TOO_OFTEN = "metadataNotOneElement";

    /**
     * Message key if there are too little entries of a type.
     */
    private static final String MESSAGE_VALUE_TOO_RARE = "metadataNotEnoughElements";

    @Override
    public ValidationResult validate(URI metsFileUri, URI rulesetFileUri, List<LanguageRange> metadataLanguage,
            Map<String, String> translations) {
        try {
            FileManagementInterface fileManagement = getFileManagement();
            Workpiece workpiece;
            try (InputStream inputStream = fileManagement.read(metsFileUri)) {
                workpiece = createMetsXmlElementAccess().read(inputStream);
            }
            RulesetManagementInterface ruleset = getRulesetManagement();
            ruleset.load(new File(rulesetFileUri.getPath()));

            return validate(workpiece, ruleset, metadataLanguage, translations);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ValidationResult validate(Workpiece workpiece, RulesetManagementInterface ruleset,
            List<LanguageRange> metadataLanguage, Map<String, String> translations) {

        Collection<ValidationResult> results = new ArrayList<>();

        results.add(checkForStructuresWithoutMedia(workpiece, translations));
        results.add(checkForUnlinkedMedia(workpiece, translations));

        for (LogicalStructure logicalStructure : treeStream(workpiece.getLogicalStructureRoot(),
            LogicalStructure::getChildren)
                .collect(Collectors.toList())) {
            results.addAll(checkMetadataRules(logicalStructure.toString(), logicalStructure.getType(),
                getMetadata(logicalStructure), ruleset, metadataLanguage, translations));
        }

        for (PhysicalStructure physicalStructure : treeStream(workpiece.getPhysicalStructureRoot(), PhysicalStructure::getChildren)
                .collect(Collectors.toList())) {
            results.addAll(checkMetadataRules(physicalStructure.toString(), physicalStructure.getType(), getMetadata(physicalStructure),
                    ruleset, metadataLanguage, translations));
        }

        return merge(results);
    }

    private static Collection<Metadata> getMetadata(LogicalStructure logicalStructure) {
        Collection<Metadata> metadata = new ArrayList<>(logicalStructure.getMetadata());
        if (Objects.nonNull(logicalStructure.getLabel())) {
            MetadataEntry labelEntry = new MetadataEntry();
            labelEntry.setKey("LABEL");
            labelEntry.setValue(logicalStructure.getLabel());
            metadata.add(labelEntry);
        }
        if (Objects.nonNull(logicalStructure.getOrderlabel())) {
            MetadataEntry orderlabelEntry = new MetadataEntry();
            orderlabelEntry.setKey("ORDERLABEL");
            orderlabelEntry.setValue(logicalStructure.getOrderlabel());
            metadata.add(orderlabelEntry);
        }
        return metadata;
    }

    private static Collection<Metadata> getMetadata(PhysicalStructure physicalStructure) {
        Collection<Metadata> metadata = new ArrayList<>(physicalStructure.getMetadata());
        if (Objects.nonNull(physicalStructure.getOrderlabel())) {
            MetadataEntry orderlabelEntry = new MetadataEntry();
            orderlabelEntry.setKey("ORDERLABEL");
            orderlabelEntry.setValue(physicalStructure.getOrderlabel());
            metadata.add(orderlabelEntry);
        }
        return metadata;
    }

    private Collection<ValidationResult> checkMetadataRules(String elementString,
                                                            String type,
                                                            Collection<Metadata> metadata,
                                                            RulesetManagementInterface ruleset,
                                                            List<LanguageRange> metadataLanguage,
                                                            Map<String, String> translations) {
        Collection<ValidationResult> results = new ArrayList<>();
        StructuralElementViewInterface divisionView = ruleset.getStructuralElementView(type, null,
                metadataLanguage);
        results.add(checkForMandatoryQuantitiesOfTheMetadataRecursive(metadata.parallelStream().collect(
                Collectors.toMap(Function.identity(), Metadata::getKey)),
                divisionView, elementString.concat(": "), translations));
        results.add(checkForDetailsInTheMetadataRecursive(metadata.parallelStream().collect(
                Collectors.toMap(Function.identity(), Metadata::getKey)),
                divisionView, elementString.concat(": "), translations));
        return results;
    }

    /**
     * Reports structures that have no assigned media units. These structures
     * are undesirable because you cannot look at them. It is also checked if
     * the linked media are even referenced in the document.
     *
     * @param workpiece
     *            workpiece to be examined
     * @return the validation result
     */
    private static ValidationResult checkForStructuresWithoutMedia(Workpiece workpiece,
            Map<String, String> translations) {
        boolean error = false;
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        Collection<String> structuresWithoutMedia = treeStream(workpiece.getLogicalStructureRoot(), LogicalStructure::getChildren)
                .filter(struc -> Objects.nonNull(struc.getType()) && struc.getViews().isEmpty() && struc.getChildren().isEmpty())
                    .map(structure -> translations.get(MESSAGE_STRUCTURE_WITHOUT_MEDIA) + ' ' + structure)
                    .collect(Collectors.toSet());
        if (!structuresWithoutMedia.isEmpty()) {
            messages.addAll(structuresWithoutMedia);
            warning = true;
        }

        if (!treeStream(workpiece.getLogicalStructureRoot(), LogicalStructure::getChildren)
                .flatMap(structure -> structure.getViews().stream()).map(View::getPhysicalStructure)
                .allMatch(workpiece.getMediaUnits()::contains)) {
            messages.add(translations.get(MESSAGE_MEDIA_MISSING));
            error = true;
        }

        return new ValidationResult(error ? State.ERROR : warning ? State.WARNING : State.SUCCESS, messages);
    }

    /**
     * Checks whether media are referenced in the document that are not assigned
     * to a structure. Maybe not a mistake but sloppy.
     *
     * @param workpiece
     *            workpiece to be examined
     * @return the validation result
     */
    private static ValidationResult checkForUnlinkedMedia(Workpiece workpiece,
            Map<String, String> translations) {
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        KeySetView<PhysicalStructure, ?> unassignedMediaUnits = ConcurrentHashMap.newKeySet();
        unassignedMediaUnits.addAll(workpiece.getMediaUnits());
        treeStream(workpiece.getLogicalStructureRoot(), LogicalStructure::getChildren).flatMap(structure -> structure.getViews().stream())
                .map(View::getPhysicalStructure)
                .forEach(unassignedMediaUnits::remove);
        if (!unassignedMediaUnits.isEmpty()) {
            for (PhysicalStructure physicalStructure : unassignedMediaUnits) {
                messages.add(translations.get(MESSAGE_MEDIA_UNASSIGNED) + ' ' + physicalStructure);
            }
            warning = true;
        }

        return new ValidationResult(warning ? State.WARNING : State.SUCCESS, messages);
    }

    /**
     * Checks if all description data occur in the given frequency (minimum /
     * maximum).
     *
     * @param containedMetadata
     *            metadata
     * @param containingMetadataView
     *            associated hierarchy node of the rule set
     * @param location
     *            specifies which structure element or which metadata group in
     *            it is checked
     * @return the validation result
     */
    private static ValidationResult checkForMandatoryQuantitiesOfTheMetadataRecursive(
            Map<Metadata, String> containedMetadata, ComplexMetadataViewInterface containingMetadataView,
            String location, Map<String, String> translations) {
        boolean error = false;
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        for (Entry<MetadataViewInterface, Collection<Metadata>> metadataViewWithValues : squash(
            containingMetadataView.getSortedVisibleMetadata(containedMetadata, Collections.emptyList())).entrySet()) {

            MetadataViewInterface metadataView = metadataViewWithValues.getKey();
            int min = metadataView.getMinOccurs();
            int max = metadataView.getMaxOccurs();
            int count = metadataViewWithValues.getValue().size();

            if (count == 0 && (min == 1 && max == 1)) {
                messages.add(MessageFormat.format(translations.get(MESSAGE_VALUE_MISSING),
                    location.concat(metadataView.getLabel())));
                error = true;
            } else if (count < min) {
                messages.add(MessageFormat.format(translations.get(MESSAGE_VALUE_TOO_RARE),
                    location.concat(metadataView.getLabel()), Integer.toString(count), Integer.toString(min)));
                error = true;
            } else if (count > max) {
                messages.add(MessageFormat.format(translations.get(MESSAGE_VALUE_TOO_OFTEN),
                    location.concat(metadataView.getLabel()), Integer.toString(count), Integer.toString(min)));
                error = true;
            }

            if (metadataView instanceof ComplexMetadataViewInterface) {
                for (Metadata metadata : metadataViewWithValues.getValue()) {
                    if (metadata instanceof MetadataGroup) {
                        ValidationResult validationResult = checkForMandatoryQuantitiesOfTheMetadataRecursive(
                            ((MetadataGroup) metadata).getGroup().parallelStream()
                                    .collect(Collectors.toMap(Function.identity(), Metadata::getKey)),
                            (ComplexMetadataViewInterface) metadataView, location + metadataView.getLabel() + " - ",
                            translations);
                        if (validationResult.getState().equals(State.WARNING)) {
                            warning = true;
                        }
                        messages.addAll(validationResult.getResultMessages());
                    } else {
                        throw new IllegalStateException("metadataView is a " + metadataView.getClass().getSimpleName()
                                + ", but metadata is a " + metadata.getClass().getSimpleName());
                    }
                }
            }
        }

        return new ValidationResult(error ? State.ERROR : warning ? State.WARNING : State.SUCCESS, messages);
    }

    /**
     * Checks if all description data meet their individually defined validity
     * criteria. This essentially checks the data type. Actually, this should
     * already happen at the input and not get here, but we check it anyway. For
     * example, corrupt data may have been imported.
     *
     * @param containedMetadata
     *            metadata
     * @param containingMetadataView
     *            associated hierarchy node of the rule set
     * @param location
     *            specifies which structure element or which metadata group in
     *            it is checked
     * @return the validation result
     */
    private static ValidationResult checkForDetailsInTheMetadataRecursive(
            Map<Metadata, String> containedMetadata, ComplexMetadataViewInterface containingMetadataView,
            String location, Map<String, String> translations) {
        boolean error = false;
        Collection<String> messages = new HashSet<>();

        List<MetadataViewWithValuesInterface<Metadata>> metadataViewsWithValues = containingMetadataView
                .getSortedVisibleMetadata(containedMetadata, Collections.emptyList());
        for (MetadataViewWithValuesInterface<Metadata> metadataViewWithValues : metadataViewsWithValues) {
            MetadataViewInterface metadataView = metadataViewWithValues.getMetadata()
                    .orElseThrow(IllegalStateException::new);
            for (Metadata metadata : metadataViewWithValues.getValues()) {
                if (metadata instanceof MetadataEntry
                        && metadataView instanceof SimpleMetadataViewInterface) {
                    String value = ((MetadataEntry) metadata).getValue();
                    if (!((SimpleMetadataViewInterface) metadataView).isValid(value)) {
                        messages.add(MessageFormat.format(translations.get(MESSAGE_VALUE_INVALID), value,
                            location.concat(metadataView.getLabel())));
                        error = true;
                    }
                } else if (metadata instanceof MetadataGroup
                        && metadataView instanceof ComplexMetadataViewInterface) {
                    ValidationResult validationResult = checkForDetailsInTheMetadataRecursive(
                        ((MetadataGroup) metadata).getGroup().parallelStream()
                                .collect(Collectors.toMap(Function.identity(), Metadata::getKey)),
                        (ComplexMetadataViewInterface) metadataView, location + metadataView.getLabel() + " - ",
                        translations);
                    if (validationResult.getState().equals(State.ERROR)) {
                        error = true;
                    }
                    messages.addAll(validationResult.getResultMessages());
                } else {
                    throw new IllegalStateException("metadataView is a " + metadataView.getClass().getSimpleName()
                            + ", but metadata is a " + metadata.getClass().getSimpleName());
                }
            }
        }

        return new ValidationResult(error ? State.ERROR : State.SUCCESS, messages);
    }

    // helper methods

    /**
     * Creates a new METS XML element access to read the METS file.
     *
     * @return a new METS XML element access
     */
    private static MetsXmlElementAccessInterface createMetsXmlElementAccess() {
        return new KitodoServiceLoader<MetsXmlElementAccessInterface>(MetsXmlElementAccessInterface.class).loadModule();
    }

    /**
     * Returns a file management to read the METS file.
     *
     * @return a file management
     */
    private static FileManagementInterface getFileManagement() {
        return new KitodoServiceLoader<FileManagementInterface>(FileManagementInterface.class).loadModule();
    }

    /**
     * Returns a ruleset management to validate the METS file.
     *
     * @return a ruleset management
     */
    private static RulesetManagementInterface getRulesetManagement() {
        return new KitodoServiceLoader<RulesetManagementInterface>(RulesetManagementInterface.class).loadModule();
    }

    /**
     * Merges multiple metadata lines of identical type. The output format of
     * the rule set refers to the display form and therefore generates a
     * separate line for each metadata value. In the validation we need the
     * number. To get that, the lines are summarized here.
     *
     * @param metadataViewsWithValues
     *            list of metadata view objects, each with their value
     * @return merged lines of identical type
     */
    private static Map<MetadataViewInterface, Collection<Metadata>> squash(
            List<MetadataViewWithValuesInterface<Metadata>> metadataViewsWithValues) {
        Map<MetadataViewInterface, Collection<Metadata>> squashed = new HashMap<>();
        for (MetadataViewWithValuesInterface<Metadata> metadataViewWithValues : metadataViewsWithValues) {
            Optional<MetadataViewInterface> optionalMetadataView = metadataViewWithValues.getMetadata();
            if (!optionalMetadataView.isPresent()) {
                continue;
            }
            squashed.computeIfAbsent(optionalMetadataView.get(), each -> new ArrayList<>());
            squashed.get(optionalMetadataView.get()).addAll(metadataViewWithValues.getValues());
        }
        return squashed;
    }

    /**
     * Merges several individual validation results into one validation result.
     *
     * @param results
     *            individual validation results
     * @return merged validation result
     */
    private static ValidationResult merge(Collection<ValidationResult> results) {
        boolean error = false;
        boolean warning = false;
        Collection<String> messages = new HashSet<>();

        for (ValidationResult result : results) {
            if (result.getState().equals(State.ERROR)) {
                error = true;
            } else if (result.getState().equals(State.WARNING)) {
                warning = true;
            }
            messages.addAll(result.getResultMessages());
        }

        return new ValidationResult(error ? State.ERROR : warning ? State.WARNING : State.SUCCESS, messages);
    }

    /**
     * Generates a stream of nodes from a tree-like structure.
     *
     * @param tree
     *            starting node
     * @param childAccessor
     *            function to access the children of the node
     * @return all nodes as stream
     */
    private static <T> Stream<T> treeStream(T tree, Function<T, Collection<T>> childAccessor) {
        return Stream.concat(Stream.of(tree),
            childAccessor.apply(tree).stream().flatMap(child -> treeStream(child, childAccessor)));
    }
}
