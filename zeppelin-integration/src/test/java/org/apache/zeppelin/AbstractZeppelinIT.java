/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Function;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractZeppelinIT {
  protected static WebDriver driver;

  protected final static Logger LOG = LoggerFactory.getLogger(AbstractZeppelinIT.class);
  protected static final long MIN_IMPLICIT_WAIT = 5;
  protected static final long MAX_IMPLICIT_WAIT = 30;
  protected static final long MAX_BROWSER_TIMEOUT_SEC = 30;
  protected static final long MAX_PARAGRAPH_TIMEOUT_SEC = 120;

  protected void setTextOfParagraph(int paragraphNo, String text) {
    String editorId = driver.findElement(By.xpath(getParagraphXPath(paragraphNo) + "//div[contains(@class, 'editor')]")).getAttribute("id");
    if (driver instanceof JavascriptExecutor) {
      ((JavascriptExecutor) driver).executeScript("ace.edit('" + editorId + "'). setValue('" + text + "')");
    } else {
      throw new IllegalStateException("This driver does not support JavaScript!");
    }
  }

  protected void runParagraph(int paragraphNo) {
    driver.findElement(By.xpath(getParagraphXPath(paragraphNo) + "//span[@class='icon-control-play']")).click();
  }


  protected String getParagraphXPath(int paragraphNo) {
    return "(//div[@ng-controller=\"ParagraphCtrl\"])[" + paragraphNo + "]";
  }

  protected String getNoteFormsXPath() {
    return "(//div[@id='noteForms'])";
  }

  protected boolean waitForParagraph(final int paragraphNo, final String state) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]//span[2][contains(.,'" + state + "')]");
    WebElement element = pollingWait(locator, MAX_PARAGRAPH_TIMEOUT_SEC);
    return element.isDisplayed();
  }

  protected String getParagraphStatus(final int paragraphNo) {
    By locator = By.xpath(getParagraphXPath(paragraphNo)
        + "//div[contains(@class, 'control')]/span[2]");

    return driver.findElement(locator).getText();
  }

  protected boolean waitForText(final String txt, final By locator) {
    try {
      WebElement element = pollingWait(locator, MAX_BROWSER_TIMEOUT_SEC);
      return txt.equals(element.getText());
    } catch (TimeoutException e) {
      return false;
    }
  }

  protected WebElement pollingWait(final By locator, final long timeWait) {
    Wait<WebDriver> wait = new FluentWait<>(driver)
        .withTimeout(timeWait, TimeUnit.SECONDS)
        .pollingEvery(1, TimeUnit.SECONDS)
        .ignoring(NoSuchElementException.class);

    return wait.until(new Function<WebDriver, WebElement>() {
      public WebElement apply(WebDriver driver) {
        return driver.findElement(locator);
      }
    });
  }

  protected void waitForBootstrapModalFade(final WebDriverWait wait) {
    ZeppelinITUtils.turnOffImplicitWaits(driver);
    try {
      wait.until(ExpectedConditions.numberOfElementsToBe(
          By.xpath("//div[contains(@class, 'modal-backdrop')]"), 0));
    } finally {
      ZeppelinITUtils.turnOnImplicitWaits(driver);
    }
  }

  protected void waitForLoginWindowFade(final WebDriverWait wait) {
    wait.until(ExpectedConditions.invisibilityOfElementLocated(
        By.xpath("//*[@id='loginModal']")));
    waitForBootstrapModalFade(wait);
  }

  public void authenticationUser(final String userName, final String password) {
    final WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    LOG.info("Clicking login button...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li//button[contains(.,'Login')]")))
        .click();
    // This might fail intermittently on Mac+Chrome.
    // See https://stackoverflow.com/questions/39765008/selenium-test-hangs-on-css-transition-when-chrome-window-is-sent-to-background-o
    LOG.info("Waiting for login dialog...");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
        "//*[@id='loginModal']")));
    LOG.info("Typing user name...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//*[@id='userName']")))
        .sendKeys(userName);
    LOG.info("Typing password...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//*[@id='password']")))
        .sendKeys(password);
    LOG.info("Logging in...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//*[@id='loginModalContent']//button[contains(.,'Login')]")))
        .click();
    waitForLoginWindowFade(wait);
    LOG.info("Login successful!");
  }

  public void logoutUser(final String userName) throws URISyntaxException {
    final WebDriverWait wait = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    LOG.info("Opening dropdown menu...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
            userName + "')]")))
        .click();
    LOG.info("Logging out...");
    wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
        "//div[contains(@class, 'navbar-collapse')]//li[contains(.,'" +
            userName + "')]//a[@ng-click='navbar.logout()']")))
        .click();
    final String mainPage = new URI(driver.getCurrentUrl()).resolve("/#/").toString();
    LOG.info("Waiting for the main page (" + mainPage + ")...");
    wait.until(ExpectedConditions.urlToBe(mainPage));
    LOG.info("Logout successful!");
  }

  protected void createNewNote() {
    clickAndWait(By.xpath("//div[contains(@class, \"col-md-4\")]/div/h5/a[contains(.,'Create new" +
        " note')]"));

    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.id("noteCreateModal")));
    clickAndWait(By.id("createNoteButton"));
    block.until(ExpectedConditions.invisibilityOfElementLocated(By.id("createNoteButton")));
  }

  protected void deleteTestNotebook(final WebDriver driver) {
    WebDriverWait block = new WebDriverWait(driver, MAX_BROWSER_TIMEOUT_SEC);
    driver.findElement(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']"))
        .sendKeys(Keys.ENTER);
    block.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(".//*[@id='main']//button[@ng-click='moveNoteToTrash(note.id)']")));
    driver.findElement(By.xpath("//div[@class='modal-dialog'][contains(.,'This note will be moved to trash')]" +
        "//div[@class='modal-footer']//button[contains(.,'OK')]")).click();
    ZeppelinITUtils.sleep(100, true);
  }

  protected void clickAndWait(final By locator) {
    pollingWait(locator, MAX_IMPLICIT_WAIT).click();
    ZeppelinITUtils.sleep(1000, true);
  }

  protected void handleException(String message, Exception e) throws Exception {
    LOG.error(message, e);
    File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    LOG.error("ScreenShot::\ndata:image/png;base64," + new String(Base64.encodeBase64(FileUtils.readFileToByteArray(scrFile))));
    throw e;
  }

  protected static void handleBrowserLogs() {
    try {
      for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
        LOG.info(new Date(entry.getTimestamp()) + " " + entry.getLevel() + " " + entry.getMessage());
      }
    } catch (WebDriverException e) {
      // Log retrieval is not available for our browser, ignore.
    }
  }
}
