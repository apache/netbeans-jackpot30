@echo off
REM DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
REM
REM Copyright 2009-2017 Oracle and/or its affiliates. All rights reserved.
REM
REM Oracle and Java are registered trademarks of Oracle and/or its affiliates.
REM Other names may be trademarks of their respective owners.
REM
REM The contents of this file are subject to the terms of either the GNU
REM General Public License Version 2 only ("GPL") or the Common
REM Development and Distribution License("CDDL") (collectively, the
REM "License"). You may not use this file except in compliance with the
REM License. You can obtain a copy of the License at
REM http://www.netbeans.org/cddl-gplv2.html
REM or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
REM specific language governing permissions and limitations under the
REM License.  When distributing the software, include this License Header
REM Notice in each file and include the License file at
REM nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
REM particular file as subject to the "Classpath" exception as provided
REM by Oracle in the GPL Version 2 section of the License file that
REM accompanied this code. If applicable, add the following below the
REM License Header, with the fields enclosed by brackets [] replaced by
REM your own identifying information:
REM "Portions Copyrighted [year] [name of copyright owner]"
REM
REM Contributor(s):
REM
REM The Original Software is NetBeans. The Initial Developer of the Original
REM Software is Sun Microsystems, Inc. Portions Copyright 2009-2010 Sun
REM Microsystems, Inc. All Rights Reserved.
REM
REM If you wish your version of this file to be governed by only the CDDL
REM or only the GPL Version 2, indicate your decision by adding
REM "[Contributor] elects to include this software in this distribution
REM under the [CDDL or GPL Version 2] license." If you do not indicate a
REM single choice of license, a recipient has the option to distribute
REM your version of this file under either the CDDL, the GPL Version 2 or
REM to extend the choice of license to its licensees as provided above.
REM However, if you add GPL Version 2 code and therefore, elected the GPL
REM Version 2 license, then the option applies only if the new code is
REM made subject to such option by the copyright holder.

@echo on
set dirname=%0%\..
java -classpath "%dirname:"=%\jackpot.jar" org.netbeans.modules.jackpot30.cmdline.Main %*
