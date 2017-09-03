/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2009-2017 Oracle and/or its affiliates. All rights reserved.
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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 2009-2010 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */

HighlightingTest = TestCase("HighlightingTest");

HighlightingTest.prototype.testHighlighting = function() {
    var colored = tokenColoring("0123456789", ["a identifier", "b", "c identifier", "d"], [4, 1, 4, 1]);
    //                           aaaabccccd
    assertEquals('<table><tr><td class="unselectable">1</td><td><span id="p0" class="a identifier" jpt30pos="0">0123</span><span id="p4" class="b" jpt30pos="4">4</span><span id="p5" class="c identifier" jpt30pos="5">5678</span><span id="p9" class="d" jpt30pos="9">9</span></td></tr></table>', colored);

    $(document).find('body').append("<div id='code'></div>");
    var scratch = $("#code");
    scratch.empty();
    scratch.append(colored);
    addHighlights([[0, 4], [9, 10]]); //exclusive ends, must be synchronized with the server
    assertEquals('<table><tbody><tr><td class="unselectable">1</td><td><span id="p0" class="a identifier highlight" jpt30pos="0">0123</span><span id="p4" class="b" jpt30pos="4">4</span><span id="p5" class="c identifier" jpt30pos="5">5678</span><span id="p9" class="d highlight" jpt30pos="9">9</span></td></tr></tbody></table>', scratch.html());
    scratch.empty();
    scratch.append(colored);
    addHighlights([[1, 5], [6, 8]]); //exclusive ends, must be synchronized with the server
    assertEquals('<table><tbody><tr><td class="unselectable">1</td><td><span id="p0" class="a identifier" jpt30pos="0">0</span><span id="p1" class="a identifier highlight" jpt30pos="1">123</span><span id="p4" class="b highlight" jpt30pos="4">4</span><span id="p5" class="c identifier" jpt30pos="5">5</span><span id="p6" class="c identifier highlight" jpt30pos="6">67</span><span id="p8" class="c identifier" jpt30pos="8">8</span><span id="p9" class="d" jpt30pos="9">9</span></td></tr></tbody></table>', scratch.html());
    scratch.empty();
    scratch.append(colored);
    addHighlights([[0, 2], [9, 10]]); //exclusive ends, must be synchronized with the server
    assertEquals('<table><tbody><tr><td class="unselectable">1</td><td><span id="p0" class="a identifier highlight" jpt30pos="0">01</span><span id="p2" class="a identifier" jpt30pos="2">23</span><span id="p4" class="b" jpt30pos="4">4</span><span id="p5" class="c identifier" jpt30pos="5">5678</span><span id="p9" class="d highlight" jpt30pos="9">9</span></td></tr></tbody></table>', scratch.html());
};
