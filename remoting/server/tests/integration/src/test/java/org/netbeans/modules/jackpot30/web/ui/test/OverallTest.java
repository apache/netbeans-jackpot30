/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.jackpot30.web.ui.test;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 *
 * @author lahvac
 */
public class OverallTest {

    @Test
    public void overallTest() throws Exception {
//        WebDriver driver = new FirefoxDriver();
        WebDriver driver = new HtmlUnitDriver(BrowserVersion.FIREFOX_10);

        ((HtmlUnitDriver) driver).setJavascriptEnabled(true);

        try {
            driver.get("http://localhost:" + System.getProperty("PORT", "9998") + "/index/ui/index.html");

            //wait for the page to be rendered:
            new WebDriverWait(driver, 20).until(new Predicate<WebDriver>() {
                public boolean apply(WebDriver t) {
                    List<WebElement> cb = t.findElements(By.id("projectCheckBox-data"));
                    return !cb.isEmpty() && cb.get(0).isDisplayed();
                }
            });

            WebElement searchInput = driver.findElements(By.id("search-input")).get(0);

            searchInput.sendKeys("ClassA");
            searchInput.submit();

            WebElement link = new WebDriverWait(driver, 20).until(new Function<WebDriver, WebElement>() {
                public WebElement apply(WebDriver t) {
                    List<WebElement> cb = t.findElements(By.tagName("a"));

                    for (WebElement we : cb) {
                        String href = we.getAttribute("href");

                        if (href != null && href.contains("goto=CLASS:org.netbeans.modules.jackpot30.example.ClassA")) return we;
                    }

                    return null;
                }
            });


            link.click();

            WebElement methodIdentifierSpan = new WebDriverWait(driver, 20).until(new Function<WebDriver, WebElement>() {
                public WebElement apply(WebDriver t) {
                    List<WebElement> cb = t.findElements(By.tagName("span"));

                    for (WebElement we : cb) {
                        String href = we.getAttribute("jpt30pos");

                        if (href != null && href.equals("88")) return we;
                    }

                    return null;
                }
            });

            String classes = methodIdentifierSpan.getAttribute("class");

            Assert.assertTrue(classes.contains("identifier") && classes.contains("method") && classes.contains("declaration") && classes.contains("public"));

            methodIdentifierSpan.click();

            Assert.assertEquals(Arrays.asList("example/src/org/netbeans/modules/jackpot30/example/SubClassA.java",
                                              "example/src/org/netbeans/modules/jackpot30/example/UseClassA.java"),
                                usagesList(driver));

            findCheckbox(driver, "showUsages").click(); //uncheck

            Assert.assertEquals(Arrays.asList("example/src/org/netbeans/modules/jackpot30/example/SubClassA.java"),
                                usagesList(driver));

            findCheckbox(driver, "showUsages").click(); //check
            
            Assert.assertEquals(Arrays.asList("example/src/org/netbeans/modules/jackpot30/example/SubClassA.java",
                                              "example/src/org/netbeans/modules/jackpot30/example/UseClassA.java"),
                                usagesList(driver));

            findCheckbox(driver, "showSubtypes").click(); //uncheck

            Assert.assertEquals(Arrays.asList("example/src/org/netbeans/modules/jackpot30/example/UseClassA.java"),
                                usagesList(driver));

            findCheckbox(driver, "showSubtypes").click(); //uncheck
            
            Assert.assertEquals(Arrays.asList("example/src/org/netbeans/modules/jackpot30/example/SubClassA.java",
                                              "example/src/org/netbeans/modules/jackpot30/example/UseClassA.java"),
                                usagesList(driver));
        } finally {
            driver.quit();
        }
    }

    private List<String> usagesList(WebDriver driver) {
        Iterable<WebElement> usages = new WebDriverWait(driver, 20).until(new Function<WebDriver, Iterable<WebElement>>() {
            public Iterable<WebElement> apply(WebDriver t) {
                List<WebElement> cb = t.findElements(By.className("usages"));

                if (cb.isEmpty()) return null;

                return cb;
            }
        });

        List<String> usageStrings = new ArrayList<String>();

        for (WebElement we : usages) {
            if (!we.isDisplayed()) continue;
            usageStrings.add(we.getText());
        }

        return usageStrings;
    }

    private WebElement findCheckbox(WebDriver driver, final String id) {
        return new WebDriverWait(driver, 20).until(new Function<WebDriver, WebElement>() {
            public WebElement apply(WebDriver t) {
                List<WebElement> cb = t.findElements(By.id(id));

                if (cb.isEmpty()) return null;

                return cb.get(0);
            }
        });
    }
}
