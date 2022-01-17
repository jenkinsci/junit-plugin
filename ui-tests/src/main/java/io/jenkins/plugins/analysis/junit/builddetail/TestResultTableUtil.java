package io.jenkins.plugins.analysis.junit.builddetail;

import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class TestResultTableUtil {

    public static List<WebElement> getTableItemsWithoutHeader(final WebElement mainPanelElement) {
        WebElement testResultTable = mainPanelElement.findElement(By.cssSelector("#testresult"));

        List<WebElement> testResultTableBodies = testResultTable.findElements(By.cssSelector("tbody"));
        WebElement testResultTableBodyWithoutHeader = testResultTableBodies.get(1);
        return testResultTableBodyWithoutHeader.findElements(By.cssSelector("tr"));
    }

    public static List<WebElement> getLinksOfTableItems(final WebElement mainPanelElement) {
        return getTableItemsWithoutHeader(mainPanelElement).stream()
                .map(trElement -> trElement.findElements(By.cssSelector("td")).get(0).findElement(By.cssSelector("a.model-link.inside")))
                .collect(Collectors.toList());
    }
}
