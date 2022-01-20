package io.jenkins.plugins.analysis.junit.testresults;

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Util class for repeating parsing tasks.
 *
 * @author Michael Müller
 * @author Nikolas Paripovic
 */
public class TestResultsTableUtil {

    /**
     * Gets the test result table items, omitting the header table item.
     * @param mainPanelElement the main panel element
     * @return the test result table items
     */
    public static List<WebElement> getTableItemsWithoutHeader(final WebElement mainPanelElement) {
        WebElement testResultTable = mainPanelElement.findElement(By.cssSelector("#testresult"));

        List<WebElement> testResultTableBodies = testResultTable.findElements(By.cssSelector("tbody"));
        WebElement testResultTableBodyWithoutHeader = testResultTableBodies.get(1);
        return testResultTableBodyWithoutHeader.findElements(By.cssSelector("tr"));
    }

    /**
     * Gets the links of the test result table items.
     * @param mainPanelElement the main panel element
     * @return the links of the test result table items
     */
    public static List<WebElement> getLinksOfTableItems(final WebElement mainPanelElement) {
        return getTableItemsWithoutHeader(mainPanelElement).stream()
                .map(trElement -> trElement.findElements(By.cssSelector("td")).get(0).findElement(By.cssSelector("a.model-link.inside")))
                .collect(Collectors.toList());
    }
}
