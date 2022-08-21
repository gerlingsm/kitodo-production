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

package org.kitodo.selenium;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.selenium.testframework.BaseTestSelenium;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.kitodo.selenium.testframework.pages.ProjectsPage;

public class ConfigConversionST extends BaseTestSelenium {

    private static final String MODS_2_KITODO = "mods2kitodo.xsl";
    private static final String KALLIOPE = "Kalliope";
    private static final String GBV = "GBV";
    private static final String K10PLUS = "K10Plus";

    @Before
    public void login() throws Exception {
        Pages.getLoginPage().goTo().performLoginAsAdmin();
    }

    @After
    public void logout() throws Exception {
        Pages.getTopNavigation().logout();
    }

    /**
     * Test import of catalog configurations.
     * @throws Exception when thread is interrupted.
     */
    @Test
    public void shouldImportCatalogConfigurations() throws Exception {
        ProjectsPage importConfigurationsTab = Pages.getProjectsPage().goToImportConfigurationsTab();

        importConfigurationsTab.openOpacConfigurationImportDialog();
        importConfigurationsTab.startOpacConfigurationImport();

        assertEquals(MODS_2_KITODO, importConfigurationsTab.getMappingFileTitle());
        importConfigurationsTab.selectInputFormatMods();
        importConfigurationsTab.selectOutputFormatKitodo();
        importConfigurationsTab.clickMappingFileOkButton();

        await("Wait for 'Results' dialog to be displayed")
                .atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(Browser.getDriver()
                        .findElementById("importResultsForm:successfulImports").isDisplayed() && Browser.getDriver()
                        .findElementById("importResultsForm:failedImports").isDisplayed()));

        // assert successful and failed opac config imports
        assertTrue(importConfigurationsTab.allCatalogsImportedSuccessfully(Arrays.asList(GBV, KALLIOPE)));
        assertTrue(importConfigurationsTab.allCatalogsFailedToImport(Collections.singletonList(K10PLUS)));
        // assert error message of K10+ import!
        // TODO: load message by key instead of using a static String here!
        String expectedErrorMessage = "XML-Pflichtelement \"mappingFiles\" konnte nicht gefunden werden";
        assertTrue("List of error messages should contain '" + expectedErrorMessage + "'",
                importConfigurationsTab.getCatalogConfigurationImportErrorsMessages().contains(expectedErrorMessage));

        importConfigurationsTab.closeResultsDialog();

        // assert number of ImportConfigurations and MappingFiles in database
        assertEquals(Long.valueOf(2), ServiceManager.getImportConfigurationService().countDatabaseRows());
        assertEquals(Long.valueOf(1), ServiceManager.getMappingFileService().countDatabaseRows());

        // assert that lists have been updated properly (counts include table header row, therefore each +1!)
        assertEquals(Long.valueOf(3), importConfigurationsTab.getNumberOfImportConfigurations());
        ProjectsPage mappingFilesTab = importConfigurationsTab.goToMappingFilesTab();
        assertEquals(Long.valueOf(2), mappingFilesTab.getNumberOfMappingFiles());

        checkGbvConfiguration();
        checkKalliopeConfiguration();
    }

    private void checkGbvConfiguration() throws DAOException {
        ImportConfiguration gbvConfiguration = ServiceManager.getImportConfigurationService().getById(1);
        assertEquals("GBV", gbvConfiguration.getTitle());
        assertEquals("SRU", gbvConfiguration.getInterfaceType());
        assertEquals("MODS", gbvConfiguration.getMetadataFormat());

        // check GBV url
        assertEquals("http", gbvConfiguration.getScheme());
        assertEquals("sru.gbv.de", gbvConfiguration.getHost());
        assertEquals("/gvk", gbvConfiguration.getPath());

        // search fields
        assertEquals(8, gbvConfiguration.getSearchFields().size());
        assertEquals("PPN", gbvConfiguration.getDefaultSearchField().getLabel());

        // mapping files
        assertEquals(1, gbvConfiguration.getMappingFiles().size());
        assertEquals(MODS_2_KITODO, gbvConfiguration.getMappingFiles().get(0).getFile());
    }

    private void checkKalliopeConfiguration() throws DAOException {
        ImportConfiguration kalliopeConfiguration = ServiceManager.getImportConfigurationService().getById(2);
        assertEquals("Kalliope", kalliopeConfiguration.getTitle());
        assertEquals("SRU", kalliopeConfiguration.getInterfaceType());
        assertEquals("MODS", kalliopeConfiguration.getMetadataFormat());

        // check Kalliope url
        assertEquals("http", kalliopeConfiguration.getScheme());
        assertEquals("localhost", kalliopeConfiguration.getHost());
        assertEquals("8888", String.valueOf(kalliopeConfiguration.getPort()));
        assertEquals("/sru", kalliopeConfiguration.getPath());
        // sru parameter
        assertEquals("1.2", kalliopeConfiguration.getSruVersion());
        assertEquals("mods", kalliopeConfiguration.getSruRecordSchema());

        // search fields
        assertEquals(6, kalliopeConfiguration.getSearchFields().size());
        assertNull(kalliopeConfiguration.getDefaultSearchField());

        // mapping files
        assertEquals(1, kalliopeConfiguration.getMappingFiles().size());
        assertEquals(MODS_2_KITODO, kalliopeConfiguration.getMappingFiles().get(0).getFile());
    }
}
