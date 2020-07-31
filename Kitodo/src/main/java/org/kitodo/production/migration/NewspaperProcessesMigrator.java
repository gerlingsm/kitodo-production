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

package org.kitodo.production.migration;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.naming.ConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.dataeditor.rulesetmanagement.DatesSimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.SimpleMetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalStructure;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.data.database.beans.Batch;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.CommandException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.exceptions.RulesetNotFoundException;
import org.kitodo.production.dto.BatchDTO;
import org.kitodo.production.dto.ProcessDTO;
import org.kitodo.production.helper.tasks.NewspaperMigrationTask;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.process.NewspaperProcessesGenerator;
import org.kitodo.production.process.ProcessGenerator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.BatchService;
import org.kitodo.production.services.data.ImportService;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.dataeditor.DataEditorService;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;

/**
 * Tool for converting newspaper processes from Production v. 2 format to
 * Production v. 3 format.
 */
public class NewspaperProcessesMigrator {
    private static final Logger logger = LogManager.getLogger(NewspaperProcessesMigrator.class);

    /**
     * Metadata field in Production v. 2 where the displayed title is contained.
     */
    private static final String FIELD_TITLE = "TitleDocMain";

    /**
     * Metadata field in Production v. 2, in which the title is contained in
     * sorting form.
     */
    private static final String FIELD_TITLE_SORT = "TitleDocMainShort";

    /**
     * Regular expression to find (and remove) the individual part of the
     * process title, to get the base process title.
     */
    private static final String INDIVIDUAL_PART = "(?<=.)\\p{Punct}*(?:1[6-9]|20)\\d{2}\\p{Punct}?(?:0[1-9]|1[012]).*$";

    /**
     * A regular expression describing a four-digit year number or a double year
     * consisting of two four-digit year numbers, concatenated by a slash.
     */
    private static final String YEAR_OR_DOUBLE_YEAR = "\\d{4}(?:/\\d{4})?";

    /**
     * Acquisition stage of newspaper processes migrator.
     */
    private final String acquisitionStage = "";

    /**
     * The database index number of the newspaper batch.
     */
    private Integer batchNumber;

    /**
     * Service to read and write Batch objects in the database or search engine
     * index.
     */
    private static final BatchService batchService = ServiceManager.getBatchService();

    /**
     * Service that contains the meta-data editor.
     */
    private final DataEditorService dataEditorService = ServiceManager.getDataEditorService();

    /**
     * Ruleset setting where to store the day information.
     */
    private DatesSimpleMetadataViewInterface daySimpleMetadataView;

    /**
     * Service to access files on the storage.
     */
    private static final FileService fileService = ServiceManager.getFileService();

    /**
     * Service to read and write METS file format.
     */
    private final MetsService metsService = ServiceManager.getMetsService();

    /**
     * Ruleset setting where to store the month information.
     */
    private DatesSimpleMetadataViewInterface monthSimpleMetadataView;

    /**
     * Service to read and write Process objects in the database or search
     * engine index.
     */
    private static final ProcessService processService = ServiceManager.getProcessService();

    /**
     * List of transferred processes.
     */
    private final List<ProcessDTO> transferredProcess;

    /**
     * Record ID of the process template.
     */
    private int templateId;

    /**
     * The metadata of the newspaper as its whole.
     */
    private Collection<Metadata> overallMetadata = new ArrayList<>();

    /**
     * A process representing the newspaper as its whole.
     */
    private Process overallProcess;

    /**
     * The workpiece of the newspaper as its whole.
     */
    private Workpiece overallWorkpiece = new Workpiece();

    /**
     * Record ID of the project.
     */
    private int projectId;

    /**
     * The process title.
     */
    private String title;

    /**
     * Ruleset setting where to store the year information.
     */
    private DatesSimpleMetadataViewInterface yearSimpleMetadataView;

    /**
     * The years of the course of appearance of the newspaper with their logical
     * structure root elements.
     */
    private Map<String, LogicalStructure> years = new TreeMap<>();

    /**
     * Process IDs of children (issue processes) to be added to the years in
     * question.
     */
    private Map<String, Collection<Integer>> yearsChildren = new HashMap<>();

    /**
     * Years iterator during creation of year processes.
     */
    private PeekingIterator<Entry<String, LogicalStructure>> yearsIterator;

    /**
     * Creates a new process migrator.
     *
     * @param batchTransfer
     *            transfers Production v. 2 newspaper batch
     */
    public NewspaperProcessesMigrator(BatchDTO batchTransfer) {
        this.batchNumber = batchTransfer.getId();
        this.transferredProcess = batchTransfer.getProcesses();
    }

    /**
     * Returns all newspaper batches.
     *
     * @return all newspaper batches
     * @throws DataException
     *             if a search engine error occurs
     * @throws DAOException
     *             if a process cannot be load from the database
     * @throws IOException
     *             if an I/O error occurs when accessing the file system
     */
    public static List<BatchDTO> getNewspaperBatches() throws DataException, DAOException, IOException {
        List<BatchDTO> newspaperBatches = new ArrayList<>();
        for (BatchDTO batchTransfer : batchService.findAll()) {
            if (isNewspaperBatch(batchTransfer)) {
                newspaperBatches.add(batchTransfer);
            }
        }
        return newspaperBatches;
    }

    /**
     * Returns whether the batch in the transfer object is a newspaper batch. A
     * batch is a newspaper batch, if all of its processes are newspaper
     * processes. A process is a newspaper process if it has a
     * {@code meta_year.xml} file.
     *
     * @param batchTransfer
     *            object transferring a batch
     * @return whether the batch is a newspaper batch
     * @throws DAOException
     *             if a process cannot be load from the database
     * @throws IOException
     *             if an I/O error occurs when accessing the file system
     */
    private static boolean isNewspaperBatch(BatchDTO batchTransfer) throws DAOException, IOException {

        logger.trace("Examining batch {}...", batchTransfer.getTitle());
        boolean newspaperBatch = true;
        for (ProcessDTO processTransfer : batchTransfer.getProcesses()) {
            Process process = processService.getById(processTransfer.getId());
            if (!fileService.processOwnsYearXML(process)) {
                newspaperBatch = false;
                break;
            }
        }
        logger.trace("{} {} newspaper batch.", batchTransfer.getTitle(), newspaperBatch ? "is a" : "is not a");
        return newspaperBatch;
    }

    /**
     * Creates a newspaper migration task for the given batch ID in the task
     * manager.
     *
     * @param batchId
     *            number of batch to migrate
     * @throws DataException
     *             if a search engine error occurs
     */
    public static void initializeMigration(Integer batchId) throws DataException {
        BatchDTO batchTransfer = ServiceManager.getBatchService().findById(batchId);
        TaskManager.addTask(new NewspaperMigrationTask(batchTransfer));
    }

    /**
     * Initializes the newspaper processes migrator.
     *
     * @param process
     *            a process, to get basic information from
     * @param newspaperIncludedStructalElementDivision
     *            the ID of the newspaper division in the ruleset
     */
    private void initializeMigrator(Process process, String newspaperIncludedStructalElementDivision)
            throws IOException, ConfigurationException, RulesetNotFoundException {

        title = process.getTitle().replaceFirst(INDIVIDUAL_PART, "");
        logger.trace("Newspaper is: {}", title);
        projectId = process.getProject().getId();
        logger.trace("Project is: {} (ID {})", process.getProject().getTitle(), projectId);
        templateId = process.getTemplate().getId();
        logger.trace("Template is: {} (ID {})", process.getTemplate().getTitle(), templateId);

        RulesetManagementInterface rulesetManagement = ServiceManager.getRulesetService()
                .openRuleset(process.getRuleset());
        StructuralElementViewInterface newspaperView = rulesetManagement.getStructuralElementView(
            newspaperIncludedStructalElementDivision, "", NewspaperProcessesGenerator.ENGLISH);
        StructuralElementViewInterface yearDivisionView = NewspaperProcessesGenerator.nextSubView(rulesetManagement,
            newspaperView, acquisitionStage);
        yearSimpleMetadataView = yearDivisionView.getDatesSimpleMetadata().orElseThrow(
            () -> new ConfigurationException(yearDivisionView.getId() + " has no dates metadata configuration!"));
        StructuralElementViewInterface monthDivisionView = NewspaperProcessesGenerator.nextSubView(rulesetManagement,
            yearDivisionView, acquisitionStage);
        monthSimpleMetadataView = monthDivisionView.getDatesSimpleMetadata().orElseThrow(
            () -> new ConfigurationException(monthDivisionView.getId() + " has no dates metadata configuration!"));
        StructuralElementViewInterface dayDivisionView = NewspaperProcessesGenerator.nextSubView(rulesetManagement,
            monthDivisionView, acquisitionStage);
        daySimpleMetadataView = dayDivisionView.getDatesSimpleMetadata().orElseThrow(
            () -> new ConfigurationException(dayDivisionView.getId() + " has no dates metadata configuration!"));
    }

    /**
     * Converts one newspaper process.
     *
     * @param index
     *            index of process to convert in the processes transfer object
     *            list passed to the constructor—<b>not</b> the process ID
     */
    public void convertProcess(int index) throws DAOException, IOException, ConfigurationException, RulesetNotFoundException {
        final long begin = System.nanoTime();
        Integer processId = transferredProcess.get(index).getId();
        Process process = processService.getById(processId);
        String processTitle = process.getTitle();
        logger.info("Starting to convert process {} (ID {})...", processTitle, processId);
        URI metadataFilePath = fileService.getMetadataFilePath(process);
        URI anchorFilePath = fileService.createAnchorFile(metadataFilePath);
        URI yearFilePath = fileService.createYearFile(metadataFilePath);

        dataEditorService.readData(anchorFilePath);
        dataEditorService.readData(yearFilePath);
        dataEditorService.readData(metadataFilePath);

        Workpiece workpiece = metsService.loadWorkpiece(metadataFilePath);
        workpiece.setId(process.getId().toString());
        LogicalStructure newspaperLogicalStructureRoot = workpiece.getLogicalStructureRoot();

        if (Objects.isNull(title)) {
            initializeMigrator(process, newspaperLogicalStructureRoot.getType());
        }

        LogicalStructure yearLogicalStructure = cutOffTopLevel(newspaperLogicalStructureRoot);
        final String year = createLinkStructureAndCopyDates(process, yearFilePath, yearLogicalStructure);

        workpiece.setLogicalStructureRoot(cutOffTopLevel(yearLogicalStructure));
        metsService.saveWorkpiece(workpiece, metadataFilePath);

        for (Metadata metadata : metsService.loadWorkpiece(anchorFilePath).getLogicalStructureRoot().getMetadata()) {
            if (!overallMetadata.contains(metadata)) {
                logger.debug("Adding metadata to newspaper {}: {}", title, metadata);
                overallMetadata.add(metadata);
            }
        }
        yearsChildren.computeIfAbsent(year, each -> new ArrayList<>()).add(processId);

        ServiceManager.getFileService().renameFile(anchorFilePath, "meta_anchor.migrated");
        ServiceManager.getFileService().renameFile(yearFilePath, "meta_year.migrated");

        logger.info("Process {} (ID {}) successfully converted.", processTitle, processId);
        if (logger.isTraceEnabled()) {
            logger.trace("Converting {} took {} ms.", processTitle,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Cuts the top level of a tree of logical structure.
     *
     * @param logicalStructure
     *            tree of logical structure to be cut
     * @return the new top level
     */
    private static LogicalStructure cutOffTopLevel(LogicalStructure logicalStructure) {
        List<LogicalStructure> children = logicalStructure.getChildren();
        int numberOfChildren = children.size();
        if (numberOfChildren == 0) {
            return null;
        }
        LogicalStructure firstChild = children.get(0);
        if (numberOfChildren > 1) {
            children.subList(1, numberOfChildren).stream()
                    .flatMap(theLogicalStructure -> theLogicalStructure.getChildren().stream())
                    .forEachOrdered(firstChild.getChildren()::add);
            String firstOrderlabel = firstChild.getOrderlabel();
            String lastOrderlabel = children.get(children.size() - 1).getOrderlabel();
            if (Objects.nonNull(firstOrderlabel) && !firstOrderlabel.equals(lastOrderlabel)) {
                firstChild.setOrderlabel(firstOrderlabel + '/' + lastOrderlabel);
            }
        }
        return firstChild;
    }

    /**
     * Creates or complements the logical root levels of the annual level.
     *
     * @param process
     *            process ID of the current process (on issue level)
     * @param yearMetadata
     *            Production v. 2 year metadata file
     * @param metaFileYearLogicalStructure
     *            year logical structure of the processes’ metadata file
     * @throws IOException
     *             if an error occurs in the disk drive
     */
    private String createLinkStructureAndCopyDates(Process process, URI yearMetadata,
            LogicalStructure metaFileYearLogicalStructure)
            throws IOException {

        LogicalStructure yearFileYearLogicalStructure = metsService.loadWorkpiece(yearMetadata)
                .getLogicalStructureRoot().getChildren().get(0);
        String year = MetadataEditor.getMetadataValue(yearFileYearLogicalStructure, FIELD_TITLE_SORT);
        if (Objects.isNull(year) || !year.matches(YEAR_OR_DOUBLE_YEAR)) {
            logger.debug("\"{}\" is not a year number. Falling back to {}.", year, FIELD_TITLE);
            year = MetadataEditor.getMetadataValue(yearFileYearLogicalStructure, FIELD_TITLE);
        }
        LogicalStructure processYearLogicalStructure = years.computeIfAbsent(year, theYear -> {
            LogicalStructure yearLogicalStructure = new LogicalStructure();
            MetadataEditor.writeMetadataEntry(yearLogicalStructure, yearSimpleMetadataView, theYear);
            return yearLogicalStructure;
        });

        createLinkStructureAndCopyMonths(process, metaFileYearLogicalStructure, yearFileYearLogicalStructure, year,
            processYearLogicalStructure);
        return year;
    }

    private void createLinkStructureAndCopyMonths(Process process, LogicalStructure metaFileYearLogicalStructure,
            LogicalStructure yearFileYearLogicalStructure, String year, LogicalStructure processYearLogicalStructure) {

        for (Iterator<LogicalStructure> yearFileMonthLogicalStructuresIterator = yearFileYearLogicalStructure
                .getChildren().iterator(), metaFileMonthLogicalStructuresIterator = metaFileYearLogicalStructure
                        .getChildren().iterator(); yearFileMonthLogicalStructuresIterator.hasNext()
                                && metaFileMonthLogicalStructuresIterator.hasNext();) {
            LogicalStructure yearFileMonthLogicalStructure = yearFileMonthLogicalStructuresIterator.next();
            LogicalStructure metaFileMonthLogicalStructure = metaFileMonthLogicalStructuresIterator.next();
            String month = getCompletedDate(yearFileMonthLogicalStructure, year);
            LogicalStructure processMonthLogicalStructure = computeIfAbsent(processYearLogicalStructure,
                monthSimpleMetadataView, month);
            MetadataEditor.writeMetadataEntry(metaFileMonthLogicalStructure, monthSimpleMetadataView, month);

            createLinkStructureAndCopyDays(process, yearFileMonthLogicalStructure, metaFileMonthLogicalStructure, month,
                processMonthLogicalStructure);
        }
    }

    private void createLinkStructureAndCopyDays(Process process,
            LogicalStructure yearFileMonthLogicalStructure, LogicalStructure metaFileMonthLogicalStructure,
            String month, LogicalStructure processMonthLogicalStructure) {

        for (Iterator<LogicalStructure> yearFileDayLogicalStructuresIterator = yearFileMonthLogicalStructure
                .getChildren()
                .iterator(), metaFileDayLogicalStructuresIterator = metaFileMonthLogicalStructure.getChildren()
                        .iterator(); yearFileDayLogicalStructuresIterator.hasNext()
                                && metaFileDayLogicalStructuresIterator.hasNext();) {
            LogicalStructure yearFileDayLogicalStructure = yearFileDayLogicalStructuresIterator
                    .next();
            LogicalStructure metaFileDayLogicalStructure = metaFileDayLogicalStructuresIterator
                    .next();
            String day = getCompletedDate(yearFileDayLogicalStructure, month);
            LogicalStructure processDayLogicalStructure = computeIfAbsent(processMonthLogicalStructure,
                daySimpleMetadataView, day);
            MetadataEditor.writeMetadataEntry(metaFileDayLogicalStructure, daySimpleMetadataView, day);

            createLinkStructureOfIssues(process, yearFileDayLogicalStructure, processDayLogicalStructure);
        }
    }

    private void createLinkStructureOfIssues(Process process,
            LogicalStructure yearFileDayLogicalStructure, LogicalStructure processDayLogicalStructure) {

        int numberOfIssues = yearFileDayLogicalStructure.getChildren().size();
        for (int index = 0; index < numberOfIssues; index++) {
            MetadataEditor.addLink(processDayLogicalStructure, process.getId());
        }
    }

    /**
     * Finds the logical structure with the specified label, if it exists,
     * otherwise it creates.
     *
     * @param logicalStructure
     *            parent logical structure
     * @param simpleMetadataView
     *            indication which metadata value is used to store the value
     * @param value
     *            the value
     * @return child with value
     */
    private static LogicalStructure computeIfAbsent(LogicalStructure logicalStructure,
            SimpleMetadataViewInterface simpleMetadataView, String value) {

        int index = 0;
        for (LogicalStructure child : logicalStructure.getChildren()) {
            String firstSimpleMetadataValue = MetadataEditor.readSimpleMetadataValues(child, simpleMetadataView).get(0);
            int comparison = firstSimpleMetadataValue.compareTo(value);
            if (comparison <= -1) {
                index++;
            } else if (comparison == 0) {
                return child;
            } else {
                break;
            }
        }
        LogicalStructure computed = new LogicalStructure();
        MetadataEditor.writeMetadataEntry(computed, simpleMetadataView, value);
        logicalStructure.getChildren().add(index, computed);
        return computed;
    }

    /**
     * Adds a date to get a complete date. In Production versions before 2.2,
     * the date is stored incompletely (as an integer). This is supplemented to
     * ISO if found. Otherwise just returns the date.
     *
     * @param logicalStructure
     *            the logical structure that contains the date
     * @param previousLevel
     *            previous part of date
     * @return ISO date
     */
    private static String getCompletedDate(LogicalStructure logicalStructure, String previousLevel) {
        String date = MetadataEditor.getMetadataValue(logicalStructure, FIELD_TITLE_SORT);
        if (!date.matches("\\d{1,2}")) {
            return date;
        }
        logger.debug("Found integer date value ({}), supplementing to ISO date", date);
        StringBuilder composedDate = new StringBuilder();
        composedDate.append(previousLevel);
        composedDate.append('-');
        if (date.length() < 2) {
            composedDate.append('0');
        }
        composedDate.append(date);
        return composedDate.toString();
    }

    /**
     * Creates an overall process as a representation of the newspaper as a
     * whole.
     *
     * @throws ProcessGenerationException
     *             An error occurred while creating the process.
     * @throws IOException
     *             An error has occurred in the disk drive.
     */
    public void createOverallProcess() throws ProcessGenerationException, IOException, DataException, DAOException,
            CommandException, RulesetNotFoundException {
        final long begin = System.nanoTime();
        logger.info("Creating overall process {}...", title);

        ProcessGenerator processGenerator = new ProcessGenerator();
        processGenerator.generateProcess(templateId, projectId);
        overallProcess = processGenerator.getGeneratedProcess();
        overallProcess.setTitle(getTitle());
        ImportService.checkTasks(overallProcess, overallWorkpiece.getLogicalStructureRoot().getType());
        processService.save(overallProcess);
        ServiceManager.getFileService().createProcessLocation(overallProcess);
        overallWorkpiece.setId(overallProcess.getId().toString());
        overallWorkpiece.getLogicalStructureRoot().getMetadata().addAll(overallMetadata);
        addToBatch(overallProcess);

        logger.info("Process {} (ID {}) successfully created.", overallProcess.getTitle(), overallProcess.getId());
        if (logger.isTraceEnabled()) {
            logger.trace("Creating {} took {} ms.", overallProcess.getTitle(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Creates the next year process.
     *
     * @throws ProcessGenerationException
     *             if the process cannot be generated
     * @throws IOException
     *             if an I/O error occurs when accessing the file system
     * @throws DataException
     *             if there is an error saving the process
     * @throws DAOException
     *             if a process cannot be load from the database
     */
    public void createNextYearProcess() throws ProcessGenerationException, IOException, DataException, DAOException,
            CommandException, RulesetNotFoundException {
        final long begin = System.nanoTime();
        Entry<String, LogicalStructure> yearToCreate = yearsIterator.next();
        String yearTitle = getYearTitle(yearToCreate.getKey());
        logger.info("Creating process for year {}, {}...", yearToCreate.getKey(), yearTitle);
        ProcessGenerator processGenerator = new ProcessGenerator();
        processGenerator.generateProcess(templateId, projectId);
        Process yearProcess = processGenerator.getGeneratedProcess();
        yearProcess.setTitle(yearTitle);
        ImportService.checkTasks(yearProcess, yearToCreate.getValue().getType());
        processService.save(yearProcess);

        MetadataEditor.addLink(overallWorkpiece.getLogicalStructureRoot(), yearProcess.getId());
        if (!yearsIterator.hasNext()) {
            metsService.saveWorkpiece(overallWorkpiece, fileService.getMetadataFilePath(overallProcess, false, false));
        }

        yearProcess.setParent(overallProcess);
        overallProcess.getChildren().add(yearProcess);
        processService.save(yearProcess);

        ServiceManager.getFileService().createProcessLocation(yearProcess);

        Workpiece yearWorkpiece = new Workpiece();
        yearWorkpiece.setId(yearProcess.getId().toString());
        yearWorkpiece.setLogicalStructureRoot(yearToCreate.getValue());
        metsService.saveWorkpiece(yearWorkpiece, fileService.getMetadataFilePath(yearProcess, false, false));

        Collection<Integer> childIds = yearsChildren.get(yearToCreate.getKey());
        for (Integer childId : childIds) {
            Process child = processService.getById(childId);
            child.setParent(yearProcess);
            yearProcess.getChildren().add(child);
            processService.save(child);
        }
        processService.save(yearProcess);
        addToBatch(yearProcess);

        logger.info("Process {} (ID {}) successfully created.", yearProcess.getTitle(), yearProcess.getId());
        if (logger.isTraceEnabled()) {
            logger.trace("Creating {} took {} ms.", yearProcess.getTitle(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
        }
    }

    /**
     * Add the process to the newspaper batch.
     *
     * @param process
     *            process to be added
     */
    private void addToBatch(Process process) throws DAOException, DataException {
        Batch batch = batchService.getById(batchNumber);
        process.getBatches().add(batch);
        batch.getProcesses().add(process);
        processService.save(process);
        batchService.save(batch);
    }

    /**
     * Returns the number of years that have issues referenced.
     *
     * @return the number of years
     */
    public int getNumberOfYears() {
        return years.size();
    }

    /**
     * Returns the title of the year process to create next.
     *
     * @return the title of the year process to create next
     */
    public String getPendingYearTitle() {
        return getYearTitle(yearsIterator.peek().getKey());
    }

    /**
     * Returns the process title of the process with the given transfer index.
     *
     * @param transferIndex
     *            index of process in transfer object list
     * @return the process title
     */
    public String getProcessTitle(int transferIndex) {
        return transferredProcess.get(transferIndex).getTitle();
    }

    /**
     * Returns the title of the overall process.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the title of a year process.
     *
     * @param year
     *            year to return title
     * @return the title
     */
    private String getYearTitle(String year) {
        return title + '_' + year.replace("/", "--");
    }

    /**
     * Returns whether there are more years to create.
     *
     * @return whether there are more years
     */
    public boolean hasNextYear() {
        if (Objects.isNull(yearsIterator)) {
            yearsIterator = Iterators.peekingIterator(years.entrySet().iterator());
        }
        return yearsIterator.hasNext();
    }
}
