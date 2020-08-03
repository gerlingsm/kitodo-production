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

package org.kitodo.production.forms.dataeditor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.LogicalStructure;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.PhysicalStructure;
import org.kitodo.api.dataformat.View;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.file.FileService;
import org.primefaces.PrimeFaces;
import org.primefaces.event.DragDropEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 * Backing bean for the gallery panel of the metadata editor.
 */
public class GalleryPanel {
    private static final Logger logger = LogManager.getLogger(GalleryPanel.class);

    private static final FileService fileService = ServiceManager.getFileService();

    // Structured media
    private static final Pattern DRAG_STRIPE_IMAGE = Pattern
            .compile("imagePreviewForm:structuredPages:(\\d+):structureElementDataList:(\\d+):structuredPagePanel");

    private static final Pattern DROP_STRIPE = Pattern
            .compile("imagePreviewForm:structuredPages:(\\d+):structureElementDataList");

    private static final Pattern DROP_MEDIA_AREA = Pattern
            .compile("imagePreviewForm:structuredPages:(\\d+):structureElementDataList:(\\d+):structuredPageDropArea");

    private static final Pattern DROP_MEDIA_LAST_AREA = Pattern
            .compile("imagePreviewForm:structuredPages:(\\d+):structureElementDataList:(\\d+):structuredPageLastDropArea");

    // Unstructured media
    private static final Pattern DRAG_UNSTRUCTURED_MEDIA = Pattern
            .compile("imagePreviewForm:unstructuredMediaList:(\\d+):unstructuredMediaPanel");

    private static final Pattern DROP_UNSTRUCTURED_STRIPE = Pattern
            .compile("imagePreviewForm:unstructuredMediaList");

    private static final Pattern DROP_UNSTRUCTURED_MEDIA_AREA = Pattern
            .compile("imagePreviewForm:unstructuredMediaList:(\\d+):unstructuredPageDropArea");

    private static final Pattern DROP_UNSTRUCTURED_MEDIA_LAST_AREA = Pattern
            .compile("imagePreviewForm:unstructuredMediaList:(\\d+):unstructuredPageLastDropArea");

    private final DataEditorForm dataEditor;
    private GalleryViewMode galleryViewMode = GalleryViewMode.LIST;
    private List<GalleryMediaContent> medias = Collections.emptyList();

    private MediaVariant mediaViewVariant;
    private Map<String, GalleryMediaContent> previewImageResolver = new HashMap<>();
    private MediaVariant previewVariant;

    private List<GalleryStripe> stripes;

    private Subfolder previewFolder;

    private String cachingUUID = "";

    GalleryPanel(DataEditorForm dataEditor) {
        this.dataEditor = dataEditor;
    }

    String getAcquisitionStage() {
        return dataEditor.getAcquisitionStage();
    }

    /**
     * Get galleryViewMode.
     *
     * @return value of galleryViewMode
     */
    public String getGalleryViewMode() {
        return galleryViewMode.toString().toLowerCase();
    }

    /**
     * Get lastSelection.
     *
     * @return value of lastSelection
     */
    public Pair<PhysicalStructure, LogicalStructure> getLastSelection() {
        if (dataEditor.getSelectedMedia().size() > 0) {
            return dataEditor.getSelectedMedia().get(dataEditor.getSelectedMedia().size() - 1);
        } else {
            return null;
        }
    }

    /**
     * Check if passed galleryMediaContent is the last selection.
     * @param galleryMediaContent GalleryMediaContent to be checked
     * @return boolean
     */
    public boolean isLastSelection(GalleryMediaContent galleryMediaContent, GalleryStripe galleryStripe) {
        if (isSelected(galleryMediaContent, galleryStripe) && dataEditor.getSelectedMedia().size() > 0
                && Objects.nonNull(galleryMediaContent)) {
            return Objects.equals(galleryMediaContent.getView().getPhysicalStructure(),
                    dataEditor.getSelectedMedia().get(dataEditor.getSelectedMedia().size() - 1).getKey());
        }
        return false;
    }

    /**
     * Get the list of image file paths for the current process.
     *
     * @return List of fullsize PNG images
     */
    public List<GalleryMediaContent> getMedias() {
        return medias;
    }

    String getMediaViewMimeType() {
        return mediaViewVariant.getMimeType();
    }

    /**
     * Returns the media content of the preview media. This is the method that
     * is called when the web browser wants to retrieve the media file itself.
     *
     * @return a Primefaces object that handles the output of media data
     */
    public StreamedContent getPreviewData() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (context.getCurrentPhaseId() != PhaseId.RENDER_RESPONSE) {
            String id = context.getExternalContext().getRequestParameterMap().get("mediaId");
            GalleryMediaContent mediaContent = previewImageResolver.get(id);
            if (Objects.nonNull(mediaContent)) {
                logger.trace("Serving image request {}", id);
                return mediaContent.getPreviewData();
            }
            logger.debug("Cannot serve image request, mediaId = {}", id);
        }
        return new DefaultStreamedContent();
    }

    String getPreviewMimeType() {
        return previewVariant.getMimeType();
    }

    List<LanguageRange> getPriorityList() {
        return dataEditor.getPriorityList();
    }

    RulesetManagementInterface getRuleset() {
        return dataEditor.getRuleset();
    }

    /**
     * Get list of all logical structure elements for this process.
     *
     * @return List of logical elements
     */
    public List<GalleryStripe> getStripes() {
        return stripes;
    }

    /**
     * Handle event of page being dragged and dropped in gallery.
     *
     * @param event
     *            JSF drag'n'drop event description object
     */
    public void onPageDrop(DragDropEvent event) {
        int toStripeIndex = getDropStripeIndex(event);

        if (toStripeIndex == -1 || !dragStripeIndexMatches(event)) {
            logger.error("Unsupported drag'n'drop event from {} to {}", event.getDragId(), event.getDropId());
            return;
        }

        GalleryStripe toStripe = stripes.get(toStripeIndex);

        // move views
        List<Pair<View, LogicalStructure>> viewsToBeMoved = new ArrayList<>();
        for (Pair<PhysicalStructure, LogicalStructure> selectedElement : dataEditor.getSelectedMedia()) {
            for (View view : selectedElement.getValue().getViews()) {
                if (Objects.equals(view.getPhysicalStructure(), selectedElement.getKey())) {
                    viewsToBeMoved.add(new ImmutablePair<>(view, selectedElement.getValue()));
                }
            }
        }

        int toMediaIndex = getMediaIndex(event);
        try {
            updateData(toStripe, viewsToBeMoved, toMediaIndex);
        } catch (Exception e) {
            PrimeFaces.current().executeScript("$('#loadingScreen').hide();");
            PrimeFaces.current().executeScript("PF('corruptDataWarning').show();");
        }
        dataEditor.getStructurePanel().show();
        updateAffectedStripes(toStripe, viewsToBeMoved);
    }

    private boolean dragStripeIndexMatches(DragDropEvent event) {
        Matcher dragStripeImageMatcher = DRAG_STRIPE_IMAGE.matcher(event.getDragId());
        Matcher dragUnstructuredMediaMatcher = DRAG_UNSTRUCTURED_MEDIA.matcher(event.getDragId());
        return dragUnstructuredMediaMatcher.matches() || dragStripeImageMatcher.matches();
    }

    private int getDropStripeIndex(DragDropEvent event) {
        // empty stripe of structure element
        Matcher dropStripeMatcher = DROP_STRIPE.matcher(event.getDropId());
        // between two pages of structure element
        Matcher dropMediaAreaMatcher = DROP_MEDIA_AREA.matcher(event.getDropId());
        // after last page of structure element
        Matcher dropMediaLastAreaMatcher = DROP_MEDIA_LAST_AREA.matcher(event.getDropId());
        // empty unstructured media stripe
        Matcher dropUnstructuredMediaStripeMatcher = DROP_UNSTRUCTURED_STRIPE.matcher(event.getDropId());
        // between two pages of unstructured media stripe
        Matcher dropUnstructuredMediaAreaMatcher = DROP_UNSTRUCTURED_MEDIA_AREA.matcher(event.getDropId());
        // after last page of unstructured media stripe
        Matcher dropUnstructuredMediaLastAreaMatcher = DROP_UNSTRUCTURED_MEDIA_LAST_AREA.matcher(event.getDropId());
        if (dropStripeMatcher.matches()) {
            return Integer.parseInt(dropStripeMatcher.group(1));
        } else if (dropMediaAreaMatcher.matches()) {
            return Integer.parseInt(dropMediaAreaMatcher.group(1));
        } else if (dropMediaLastAreaMatcher.matches()) {
            return Integer.parseInt(dropMediaLastAreaMatcher.group(1));
        } else if (dropUnstructuredMediaStripeMatcher.matches()
                || dropUnstructuredMediaLastAreaMatcher.matches()
                || dropUnstructuredMediaAreaMatcher.matches()) {
            // First (0) stripe represents logical root element (unstructured media)
            return 0;
        } else {
            return -1;
        }
    }

    private int getMediaIndex(DragDropEvent event) {
        Matcher dropMediaAreaMatcher = DROP_MEDIA_AREA.matcher(event.getDropId());
        Matcher dropMediaLastAreaMatcher = DROP_MEDIA_LAST_AREA.matcher(event.getDropId());
        Matcher dropUnstructuredMediaAreaMatcher = DROP_UNSTRUCTURED_MEDIA_AREA.matcher(event.getDropId());
        Matcher dropUnstructuredMediaLastAreaMatcher = DROP_UNSTRUCTURED_MEDIA_LAST_AREA.matcher(event.getDropId());
        if (dropMediaAreaMatcher.matches()) {
            return Integer.parseInt(dropMediaAreaMatcher.group(2));
        } else if (dropMediaLastAreaMatcher.matches()) {
            return Integer.parseInt(dropMediaLastAreaMatcher.group(2)) + 1;
        } else if (dropUnstructuredMediaAreaMatcher.matches()) {
            return Integer.parseInt(dropUnstructuredMediaAreaMatcher.group(1));
        } else if (dropUnstructuredMediaLastAreaMatcher.matches()) {
            return Integer.parseInt(dropUnstructuredMediaLastAreaMatcher.group(1)) + 1;
        } else {
            return -1;
        }
    }

    private void updateData(GalleryStripe toStripe, List<Pair<View, LogicalStructure>> viewsToBeMoved, int toMediaIndex) {
        dataEditor.getStructurePanel().changeLogicalOrderFields(toStripe.getStructure(), viewsToBeMoved, toMediaIndex);
        dataEditor.getStructurePanel().reorderPhysicalStructures(toStripe.getStructure(), viewsToBeMoved, toMediaIndex);
        dataEditor.getStructurePanel().moveViews(toStripe.getStructure(), viewsToBeMoved, toMediaIndex);
        dataEditor.getStructurePanel().changePhysicalOrderFields(toStripe.getStructure(), viewsToBeMoved);
    }

    private void updateAffectedStripes(GalleryStripe toStripe, List<Pair<View, LogicalStructure>> viewsToBeMoved) {
        for (Pair<View, LogicalStructure> viewToBeMoved : viewsToBeMoved) {
            GalleryStripe fromStripe = getGalleryStripe(viewToBeMoved.getValue());
            if (Objects.nonNull(fromStripe)) {
                fromStripe.getMedias().clear();
                for (View remainingView : fromStripe.getStructure().getViews()) {
                    fromStripe.getMedias().add(createGalleryMediaContent(remainingView));
                }
            }
        }
        toStripe.getMedias().clear();

        dataEditor.getSelectedMedia().clear();

        List<View> movedViews = viewsToBeMoved.stream().map(Pair::getKey).collect(Collectors.toList());
        for (View toStripeView : toStripe.getStructure().getViews()) {
            GalleryMediaContent galleryMediaContent = createGalleryMediaContent(toStripeView);
            toStripe.getMedias().add(galleryMediaContent);
            if (movedViews.contains(toStripeView)) {
                select(galleryMediaContent, toStripe, "multi");
            }
        }
    }

    private GalleryStripe getGalleryStripe(LogicalStructure structuralElement) {
        for (GalleryStripe galleryStripe : stripes) {
            if (Objects.equals(galleryStripe.getStructure(), structuralElement)) {
                return galleryStripe;
            }
        }
        return null;
    }

    /**
     * Set galleryViewMode.
     *
     * @param galleryViewMode
     *            as java.lang.String
     */
    public void setGalleryViewMode(String galleryViewMode) {
        this.galleryViewMode = GalleryViewMode.valueOf(galleryViewMode.toUpperCase());
    }

    /**
     * Set galleryViewMode.
     * The new value can be passed from a {@code <p:remoteCommand/>} as request parameter.
     */
    public void setGalleryViewMode() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        this.galleryViewMode = GalleryViewMode.valueOf(params.get("galleryViewMode").toUpperCase());
    }

    /**
     * Update the selected TreeNode in the physical structure tree.
     */
    private void updateStructure(GalleryMediaContent galleryMediaContent, LogicalStructure structure) {
        dataEditor.getStructurePanel().updateNodeSelection(galleryMediaContent, structure);
    }

    void updateSelection(PhysicalStructure physicalStructure, LogicalStructure structuralElement) {
        if (physicalStructure.getMediaFiles().size() > 0) {

            // Update structured view
            if (this.galleryViewMode.equals(GalleryViewMode.LIST)) {
                for (GalleryStripe galleryStripe : getStripes()) {
                    if (Objects.isNull(structuralElement) || Objects.equals(structuralElement, galleryStripe.getStructure())) {
                        for (GalleryMediaContent galleryMediaContent : galleryStripe.getMedias()) {
                            if (Objects.equals(physicalStructure, galleryMediaContent.getView().getPhysicalStructure())) {
                                dataEditor.getSelectedMedia().clear();
                                dataEditor.getSelectedMedia().add(new ImmutablePair<>(physicalStructure, galleryStripe.getStructure()));
                                break;
                            }
                        }
                    }
                }
            }
            // Update unstructured view
            else {
                for (GalleryMediaContent galleryMediaContent : getMedias()) {
                    if (Objects.equals(physicalStructure, galleryMediaContent.getView().getPhysicalStructure())) {
                        dataEditor.getSelectedMedia().clear();
                        dataEditor.getSelectedMedia().add(new ImmutablePair<>(
                                physicalStructure, getLogicalStructureOfMedia(galleryMediaContent).getStructure()));
                        break;
                    }
                }
            }
        }
    }

    void show() {
        Process process = dataEditor.getProcess();
        Project project = process.getProject();
        List<PhysicalStructure> physicalStructures = dataEditor.getWorkpiece().getAllPhysicalStructuresSorted();

        Folder previewSettings = project.getPreview();
        previewVariant = Objects.nonNull(previewSettings) ? getMediaVariant(previewSettings, physicalStructures) : null;
        Folder mediaViewSettings = project.getMediaView();
        mediaViewVariant = Objects.nonNull(mediaViewSettings) ? getMediaVariant(mediaViewSettings, physicalStructures) : null;

        medias = new ArrayList<>(physicalStructures.size());
        stripes = new ArrayList<>();
        previewImageResolver = new HashMap<>();
        cachingUUID = UUID.randomUUID().toString();

        previewFolder = new Subfolder(process, project.getPreview());
        for (PhysicalStructure physicalStructure : physicalStructures) {
            View wholePhysicalStructureView = new View();
            wholePhysicalStructureView.setPhysicalStructure(physicalStructure);
            GalleryMediaContent mediaContent = createGalleryMediaContent(wholePhysicalStructureView);
            medias.add(mediaContent);
            if (mediaContent.isShowingInPreview()) {
                previewImageResolver.put(mediaContent.getId(), mediaContent);
            }
        }

        addStripesRecursive(dataEditor.getWorkpiece().getLogicalStructureRoot());
        int imagesInStructuredView = stripes.parallelStream().mapToInt(stripe -> stripe.getMedias().size()).sum();
        if (imagesInStructuredView > 200) {
            logger.warn("Number of images in structured view: {}", imagesInStructuredView);
        }
    }

    void updateStripes() {
        stripes = new ArrayList<>();
        addStripesRecursive(dataEditor.getWorkpiece().getLogicalStructureRoot());
    }

    private static MediaVariant getMediaVariant(Folder folderSettings, List<PhysicalStructure> physicalStructures) {
        String use = folderSettings.getFileGroup();
        Optional<MediaVariant> optionalMediaVariant = physicalStructures.parallelStream().map(PhysicalStructure::getMediaFiles)
                .flatMap(mediaFiles -> mediaFiles.entrySet().parallelStream()).map(Entry::getKey)
                .filter(mediaVariant -> use.equals(mediaVariant.getUse())).findAny();
        if (optionalMediaVariant.isPresent()) {
            return optionalMediaVariant.get();
        } else {
            MediaVariant mediaVariant = new MediaVariant();
            mediaVariant.setMimeType(folderSettings.getMimeType());
            mediaVariant.setUse(use);
            return mediaVariant;
        }
    }

    private void addStripesRecursive(LogicalStructure structure) {
        GalleryStripe galleryStripe = new GalleryStripe(this, structure);
        for (View view : structure.getViews()) {
            for (GalleryMediaContent galleryMediaContent : medias) {
                if (Objects.equals(view.getPhysicalStructure(), galleryMediaContent.getView().getPhysicalStructure())) {
                    galleryStripe.getMedias().add(galleryMediaContent);
                    if (galleryMediaContent.isShowingInPreview()) {
                        previewImageResolver.put(galleryMediaContent.getId(), galleryMediaContent);
                    }
                    break;
                }
            }
        }
        stripes.add(galleryStripe);
        for (LogicalStructure child : structure.getChildren()) {
            if (Objects.isNull(child.getLink())) {
                addStripesRecursive(child);
            }
        }
    }

    private GalleryMediaContent createGalleryMediaContent(View view) {
        PhysicalStructure physicalStructure = view.getPhysicalStructure();
        URI previewUri = physicalStructure.getMediaFiles().get(previewVariant);
        URI resourcePreviewUri = null;
        if (Objects.nonNull(previewUri)) {
            resourcePreviewUri = previewUri.isAbsolute() ? previewUri
                    : fileService.getResourceUriForProcessRelativeUri(dataEditor.getProcess(), previewUri);
        }
        URI mediaViewUri = physicalStructure.getMediaFiles().get(mediaViewVariant);
        URI resourceMediaViewUri = null;
        if (Objects.nonNull(mediaViewUri)) {
            resourceMediaViewUri = mediaViewUri.isAbsolute() ? mediaViewUri
                    : fileService.getResourceUriForProcessRelativeUri(dataEditor.getProcess(), mediaViewUri);
        }
        String canonical = Objects.nonNull(resourcePreviewUri) ? previewFolder.getCanonical(resourcePreviewUri) : null;
        return new GalleryMediaContent(this, view, canonical, resourcePreviewUri, resourceMediaViewUri);
    }

    /**
     * Return the GalleryStripe instance representing the logical structure element to which the Media represented
     * by the given GalleryMediaContent instance is assigned. Return null, if Media is not assigned to any logical
     * structure element.
     *
     * @param galleryMediaContent
     *          Media
     * @return GalleryStripe representing the logical structure element to which the Media is assigned
     */
    GalleryStripe getLogicalStructureOfMedia(GalleryMediaContent galleryMediaContent) {
        for (GalleryStripe galleryStripe : stripes) {
            for (GalleryMediaContent mediaContent : galleryStripe.getMedias()) {
                if (galleryMediaContent.getId().equals(mediaContent.getId())) {
                    return galleryStripe;
                }
            }
        }
        return null;
    }

    GalleryMediaContent getGalleryMediaContent(View view) {
        if (Objects.nonNull(view)) {
            for (GalleryMediaContent galleryMediaContent : this.medias) {
                if (galleryMediaContent.getView().getPhysicalStructure().equals(view.getPhysicalStructure())) {
                    return galleryMediaContent;
                }
            }
        }
        return null;
    }

    /**
     * Get the GalleryMediaContent object of the passed PhysicalStructure.
     * @param physicalStructure Object to find the GalleryMediaContent for
     * @return GalleryMediaContent
     */
    public GalleryMediaContent getGalleryMediaContent(PhysicalStructure physicalStructure) {
        for (GalleryStripe galleryStripe : stripes) {
            for (GalleryMediaContent media : galleryStripe.getMedias()) {
                if (Objects.equals(media.getView().getPhysicalStructure(), physicalStructure)) {
                    return media;
                }
            }
        }
        return null;
    }

    /**
     * Get a List of all PhysicalStructuress and the LogicalStructures they are
     * assigned to which are displayed between two selected PhysicalStructuress.
     * This method selects the Stripes that are affected by the selection and
     * delegates the selection of the contained PhysicalStructures.
     *
     * @param first
     *            First selected PhysicalStructure. A Pair of the
     *            PhysicalStructure and the LogicalStructure to which the
     *            PhysicalStructure is assigned.
     * @param last
     *            Last selected PhysicalStructure. A Pair of the
     *            PhysicalStructure and the Logical Structure to which the
     *            PhysicalStructure is assigned.
     * @return A List of all selected PhysicalStructures
     */
    private List<Pair<PhysicalStructure, LogicalStructure>> getMediaWithinRange(Pair<PhysicalStructure, LogicalStructure> first,
                                                          Pair<PhysicalStructure, LogicalStructure> last) {
        // Pairs of stripe index and media index
        Pair<Integer, Integer> firstIndices = getIndices(first.getKey(), first.getValue());
        Pair<Integer, Integer> lastIndices = getIndices(last.getKey(), last.getValue());

        if (!Objects.nonNull(firstIndices) || !Objects.nonNull(lastIndices)) {
            return new LinkedList<>();
        }

        List<GalleryStripe> stripesWithinRange = new LinkedList<>();

        /* Stripe with index 0 represents "unstructured media".
           This stripe is displayed last, but is actually the root element (first stripe). */
        if (Objects.equals(firstIndices.getKey(), lastIndices.getKey())) {
            stripesWithinRange.add(stripes.get(firstIndices.getKey()));
        } else if (lastIndices.getKey() == 0) {
            // count up, last stripe is "unstructured media"
            for (int i = firstIndices.getKey(); i <= stripes.size() - 1; i++) {
                stripesWithinRange.add(stripes.get(i));
            }
            stripesWithinRange.add(stripes.get(0));
        } else if (firstIndices.getKey() != 0 && firstIndices.getKey() < lastIndices.getKey()) {
            // count up first stripe and last stripe are not "unstructured media"
            for (int i = firstIndices.getKey(); i <= lastIndices.getKey(); i++) {
                stripesWithinRange.add(stripes.get(i));
            }
        } else if (firstIndices.getKey() == 0) {
            // count down, first stripe is "unstructured media"
            stripesWithinRange.add(stripes.get(0));
            for (int i = stripes.size() - 1; i >= lastIndices.getKey(); i--) {
                stripesWithinRange.add(stripes.get(i));
            }
        } else {
            // count down
            for (int i = firstIndices.getKey(); i >= lastIndices.getKey(); i--) {
                stripesWithinRange.add(stripes.get(i));
            }
        }

        return getMediaWithinRangeFromSelectedStripes(firstIndices, lastIndices, stripesWithinRange);
    }

    /**
     * Get a List of all PhysicalStructures and the LogicalStructures they are
     * assigned to which are displayed between two selected PhysicalStructures.
     * This method selected the PhysicalStructures between and including the two
     * indices.
     *
     * @param firstIndices
     *            First selected PhysicalStructure. A Pair of indices of the
     *            PhysicalStructure and the LogicalStructure to which the
     *            PhysicalStructure is assigned.
     * @param lastIndices
     *            Last selected PhysicalStructure. A Pair of indices of the
     *            PhysicalStructure and the Logical Structure to which the
     *            PhysicalStructure is assigned.
     * @param galleryStripes
     *            A List of GalleryStripes which contain the two selected
     *            PhysicalStructures and all in between
     * @return A List of all selected PhysicalStructures
     */
    private List<Pair<PhysicalStructure, LogicalStructure>> getMediaWithinRangeFromSelectedStripes(
            Pair<Integer, Integer> firstIndices, Pair<Integer, Integer> lastIndices, List<GalleryStripe> galleryStripes) {
        boolean countDown = false;

        /* Count down if firstIndices are larger than lastIndices.
           (Each Pair represents a stripe index and the index of the selected media within this stripe.)
         */
        if (firstIndices.getKey() > lastIndices.getKey() && lastIndices.getKey() != 0
                || Objects.equals(firstIndices.getKey(), lastIndices.getKey()) && firstIndices.getValue() > lastIndices.getValue()
                || !Objects.equals(firstIndices.getKey(), lastIndices.getKey()) && firstIndices.getKey() == 0) {
            countDown = true;
        }

        if (galleryStripes.size() == 0) {
            return new LinkedList<>();
        }

        if (countDown) {
            return getMediaBackwards(firstIndices.getValue(), lastIndices.getValue(), galleryStripes);
        } else {
            return getMediaForwards(firstIndices.getValue(), lastIndices.getValue(), galleryStripes);
        }
    }

    private List<Pair<PhysicalStructure, LogicalStructure>> getMediaForwards(Integer firstIndex, Integer lastIndex,
                                                                              List<GalleryStripe> galleryStripes) {
        List<Pair<PhysicalStructure, LogicalStructure>> mediaWithinRange = new LinkedList<>();
        GalleryStripe firstStripe = galleryStripes.get(0);

        if (galleryStripes.size() == 1) {
            for (int i = firstIndex; i <= lastIndex; i++) {
                mediaWithinRange.add(
                        new ImmutablePair<>(firstStripe.getMedias().get(i).getView().getPhysicalStructure(), firstStripe.getStructure()));
            }
        } else {

            for (int i = firstIndex; i <= firstStripe.getMedias().size() - 1; i++) {
                mediaWithinRange.add(
                        new ImmutablePair<>(firstStripe.getMedias().get(i).getView().getPhysicalStructure(), firstStripe.getStructure()));
            }

            for (int i = 1; i <= galleryStripes.size() - 2; i++) {
                GalleryStripe galleryStripe = galleryStripes.get(i);
                for (GalleryMediaContent media : galleryStripe.getMedias()) {
                    mediaWithinRange.add(new ImmutablePair<>(media.getView().getPhysicalStructure(), galleryStripe.getStructure()));
                }
            }

            GalleryStripe lastStripe = galleryStripes.get(galleryStripes.size() - 1);
            for (int i = 0; i <= lastIndex; i++) {
                mediaWithinRange.add(
                        new ImmutablePair<>(lastStripe.getMedias().get(i).getView().getPhysicalStructure(), lastStripe.getStructure()));
            }
        }
        return mediaWithinRange;
    }

    private List<Pair<PhysicalStructure, LogicalStructure>> getMediaBackwards(Integer firstIndex, Integer lastIndex,
                                                                               List<GalleryStripe> galleryStripes) {
        List<Pair<PhysicalStructure, LogicalStructure>> mediaWithinRange = new LinkedList<>();
        GalleryStripe firstStripe = galleryStripes.get(0);

        if (galleryStripes.size() == 1) {
            for (int i = firstIndex; i >= lastIndex; i--) {
                mediaWithinRange.add(
                        new ImmutablePair<>(firstStripe.getMedias().get(i).getView().getPhysicalStructure(), firstStripe.getStructure()));
            }
        } else {
            for (int i = firstIndex; i >= 0; i--) {
                mediaWithinRange.add(
                        new ImmutablePair<>(firstStripe.getMedias().get(i).getView().getPhysicalStructure(), firstStripe.getStructure()));
            }

            for (int i = 1; i <= galleryStripes.size() - 2; i++) {
                GalleryStripe galleryStripe = galleryStripes.get(i);
                for (int j = galleryStripe.getMedias().size() - 1; j >= 0; j--) {
                    mediaWithinRange.add(new ImmutablePair<>(
                            galleryStripe.getMedias().get(j).getView().getPhysicalStructure(), galleryStripe.getStructure()));
                }
            }

            GalleryStripe lastStripe = galleryStripes.get(galleryStripes.size() - 1);
            for (int i = lastStripe.getMedias().size() - 1; i >= lastIndex; i--) {
                mediaWithinRange.add(
                        new ImmutablePair<>(lastStripe.getMedias().get(i).getView().getPhysicalStructure(), lastStripe.getStructure()));
            }
        }
        return mediaWithinRange;
    }

    private Pair<Integer, Integer> getIndices(PhysicalStructure physicalStructure, LogicalStructure structuralElement) {
        for (GalleryStripe galleryStripe : stripes) {
            if (Objects.equals(galleryStripe.getStructure(), structuralElement)) {
                for (GalleryMediaContent media : galleryStripe.getMedias()) {
                    if (Objects.equals(media.getView().getPhysicalStructure(), physicalStructure)) {
                        return new ImmutablePair<>(stripes.indexOf(galleryStripe), galleryStripe.getMedias().indexOf(media));
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check whether the passed GalleryMediaContent is selected.
     * @param galleryMediaContent the GalleryMediaContent to be checked
     * @param galleryStripe the GalleryStripe where the GalleryMediaContent is located
     * @return Boolean whether passed GalleryMediaContent is selected
     */
    public boolean isSelected(GalleryMediaContent galleryMediaContent, GalleryStripe galleryStripe) {
        if (Objects.nonNull(galleryMediaContent) && Objects.nonNull(galleryMediaContent.getView())) {
            PhysicalStructure physicalStructure = galleryMediaContent.getView().getPhysicalStructure();
            if (Objects.nonNull(physicalStructure)) {
                if (Objects.nonNull(galleryStripe)) {
                    return dataEditor.isSelected(physicalStructure, galleryStripe.getStructure());
                } else {
                    return dataEditor.isSelected(physicalStructure, getLogicalStructureOfMedia(galleryMediaContent).getStructure());
                }
            }
        }
        return false;
    }

    private void selectMedia(String orderOfPhysicalStructure, String stripeIndex, String selectionType) {
        PhysicalStructure selectedPhysicalStructure = null;
        for (PhysicalStructure physicalStructure : this.dataEditor.getWorkpiece().getAllPhysicalStructuresSorted()) {
            if (Objects.equals(physicalStructure.getOrder(), Integer.parseInt(orderOfPhysicalStructure))) {
                selectedPhysicalStructure = physicalStructure;
                break;
            }
        }

        try {
            this.dataEditor.getMetadataPanel().preserveLogical();
        } catch (InvalidMetadataValueException | NoSuchMetadataFieldException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
        if (Objects.nonNull(selectedPhysicalStructure)) {
            this.dataEditor.getMetadataPanel().showPageInLogical(selectedPhysicalStructure);
        } else {
            Helper.setErrorMessage("Selected PhysicalStructure is null!");
        }

        GalleryStripe parentStripe;
        try {
            parentStripe = stripes.get(Integer.parseInt(stripeIndex));
        } catch (NumberFormatException e) {
            parentStripe = null;
        }

        GalleryMediaContent currentSelection = getGalleryMediaContent(selectedPhysicalStructure);
        select(currentSelection, parentStripe, selectionType);

        if (GalleryViewMode.PREVIEW.equals(galleryViewMode)) {
            PrimeFaces.current().executeScript("checkScrollPosition();initializeImage();scrollToSelectedTreeNode()");
        } else {
            PrimeFaces.current().executeScript("scrollToSelectedTreeNode()");
        }
    }

    private void selectStructure(String stripeIndex) {
        LogicalStructure logicalStructure = stripes.get(Integer.parseInt(stripeIndex)).getStructure();
        dataEditor.getSelectedMedia().clear();
        dataEditor.getStructurePanel().updateLogicalNodeSelection(logicalStructure);
        PrimeFaces.current().executeScript("scrollToSelectedTreeNode()");
    }

    /**
     * Update the media selection based on the page index, stripe index and pressed modifier keys passed as request parameter.
     * This method should be called via remoteCommand.
     */
    public void select() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String orderOfPhysicalStructure = params.get("page");
        String stripeIndex = params.get("stripe");
        String selectionType = params.get("selectionType");

        if (StringUtils.isNotBlank(orderOfPhysicalStructure)) {
            selectMedia(orderOfPhysicalStructure, stripeIndex, selectionType);
        } else if (StringUtils.isNotBlank(stripeIndex)) {
            try {
                selectStructure(stripeIndex);
            } catch (NumberFormatException e) {
                Helper.setErrorMessage("Could not select stripe: Stripe index \"" + stripeIndex + "\" could not be parsed.");
            }
        }
    }

    /**
     * Update selection based on the passed GalleryMediaContent and selectionType.
     *
     * @param currentSelection the GalleryMediaContent that was clicked
     * @param parentStripe the GalleryStripe the clicked GalleryMediaContent is part of
     * @param selectionType the type of selection based on the pressed modifier key
     */
    private void select(GalleryMediaContent currentSelection, GalleryStripe parentStripe, String selectionType) {
        if (Objects.isNull(parentStripe)) {
            parentStripe = getLogicalStructureOfMedia(currentSelection);
        }

        if (Objects.isNull(currentSelection)) {
            Helper.setErrorMessage("Passed GalleryMediaContent must not be null.");
            return;
        }

        switch (selectionType) {
            case "multi":
                multiSelect(currentSelection, parentStripe);
                break;
            case "range":
                rangeSelect(currentSelection, parentStripe);
                break;
            default:
                defaultSelect(currentSelection, parentStripe);
                break;
        }

        updateStructure(currentSelection, parentStripe.getStructure());
    }

    private void defaultSelect(GalleryMediaContent currentSelection, GalleryStripe parentStripe) {
        if (Objects.isNull(currentSelection)) {
            return;
        }

        dataEditor.getSelectedMedia().clear();
        dataEditor.getSelectedMedia().add(new ImmutablePair<>(currentSelection.getView().getPhysicalStructure(), parentStripe.getStructure()));
    }

    private void rangeSelect(GalleryMediaContent currentSelection, GalleryStripe parentStripe) {
        if (Objects.isNull(currentSelection)) {
            return;
        }

        if (dataEditor.getSelectedMedia().isEmpty()) {
            dataEditor.getSelectedMedia().add(new ImmutablePair<>(currentSelection.getView().getPhysicalStructure(), parentStripe.getStructure()));
            return;
        }

        Pair<PhysicalStructure, LogicalStructure> firstSelectedMediaPair = dataEditor.getSelectedMedia().get(0);
        Pair<PhysicalStructure, LogicalStructure> lastSelectedMediaPair =
                new ImmutablePair<>(currentSelection.getView().getPhysicalStructure(), parentStripe.getStructure());

        dataEditor.getSelectedMedia().clear();
        dataEditor.getSelectedMedia().addAll(getMediaWithinRange(firstSelectedMediaPair, lastSelectedMediaPair));
    }

    private void multiSelect(GalleryMediaContent currentSelection, GalleryStripe parentStripe) {
        Pair<PhysicalStructure, LogicalStructure> selectedMediaPair = new ImmutablePair<>(
                currentSelection.getView().getPhysicalStructure(), parentStripe.getStructure());

        if (dataEditor.getSelectedMedia().contains(selectedMediaPair)) {
            if (dataEditor.getSelectedMedia().size() > 1) {
                dataEditor.getSelectedMedia().remove(selectedMediaPair);
            }
        } else {
            dataEditor.getSelectedMedia().add(selectedMediaPair);
        }
    }

    /**
     * Get the index of this GalleryMediaContent's PhysicalStructure out of all
     * PhysicalStructures which are assigned to more than one LogicalStructure.
     *
     * @param galleryMediaContent
     *            object to find the index for
     * @return index of the GalleryMediaContent's PhysicalStructure if present
     *         in the List of several assignments, or -1 if not present in the
     *         list.
     */
    public int getSeveralAssignmentsIndex(GalleryMediaContent galleryMediaContent) {
        if (Objects.nonNull(galleryMediaContent.getView()) && Objects.nonNull(galleryMediaContent.getView().getPhysicalStructure())) {
            return dataEditor.getStructurePanel().getSeveralAssignments().indexOf(galleryMediaContent.getView().getPhysicalStructure());
        }
        return -1;
    }

    /**
     * Get cachingUUID.
     *
     * @return value of cachingUUID
     */
    public String getCachingUUID() {
        return cachingUUID;
    }
}
