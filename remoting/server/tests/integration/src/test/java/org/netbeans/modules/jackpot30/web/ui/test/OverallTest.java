/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2013 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2013 Sun Microsystems, Inc.
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
