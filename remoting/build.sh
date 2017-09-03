#!/bin/bash

# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright 2009-2017 Oracle and/or its affiliates. All rights reserved.
#
# Oracle and Java are registered trademarks of Oracle and/or its affiliates.
# Other names may be trademarks of their respective owners.
#
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common
# Development and Distribution License("CDDL") (collectively, the
# "License"). You may not use this file except in compliance with the
# License. You can obtain a copy of the License at
# http://www.netbeans.org/cddl-gplv2.html
# or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
# specific language governing permissions and limitations under the
# License.  When distributing the software, include this License Header
# Notice in each file and include the License file at
# nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the GPL Version 2 section of the License file that
# accompanied this code. If applicable, add the following below the
# License Header, with the fields enclosed by brackets [] replaced by
# your own identifying information:
# "Portions Copyrighted [year] [name of copyright owner]"
#
# Contributor(s):
#
# The Original Software is NetBeans. The Initial Developer of the Original
# Software is Sun Microsystems, Inc. Portions Copyright 2009-2010 Sun
# Microsystems, Inc. All Rights Reserved.
#
# If you wish your version of this file to be governed by only the CDDL
# or only the GPL Version 2, indicate your decision by adding
# "[Contributor] elects to include this software in this distribution
# under the [CDDL or GPL Version 2] license." If you do not indicate a
# single choice of license, a recipient has the option to distribute
# your version of this file under either the CDDL, the GPL Version 2 or
# to extend the choice of license to its licensees as provided above.
# However, if you add GPL Version 2 code and therefore, elected the GPL
# Version 2 license, then the option applies only if the new code is
# made subject to such option by the copyright holder.

(cd common; ant "$@" clean && ant "$@" build) || exit 1
(cd ide; ant "$@" clean && (cd ../common; ant "$@" -Dbuild.updates.dir=../ide/build/updates nbms) && ant "$@" nbms) || exit 1
rm -rf build
mkdir -p build/indexing-backend
(cd server/indexer; ant "$@" clean && ant "$@" build-zip && unzip -d ../../build/indexing-backend dist/indexer.zip) || exit 1
mkdir -p build/indexing-backend/web
(cd server/web/web.main; ant clean && ant jar && cp -r dist/* ../../../build/indexing-backend/web) || exit 1
(cd server/web/web.ui.frontend/; ant -f download.xml;) || exit 1
cp -r server/web/web.ui.frontend/public_html build/indexing-backend/web || exit 1

cp server/scripts/* build/indexing-backend

chmod u+x build/temp-indexing-backend/index
chmod u+x build/temp-indexing-backend/web

(cd build; zip -r indexing-backend.zip indexing-backend) || exit 1
(cd build; zip -r indexing-backend-shortened.zip `find indexing-backend -type f | grep -v indexing-backend/indexer/enterprise/ | grep -v indexing-backend/indexer/apisupport/  | grep -v indexing-backend/indexer/cnd/   | grep -v indexing-backend/indexer/dlight/   | grep -v indexing-backend/indexer/harness/   | grep -v indexing-backend/indexer/ide/   | grep -v indexing-backend/indexer/java   | grep -v indexing-backend/indexer/nb/   | grep -v indexing-backend/indexer/platform/   | grep -v indexing-backend/indexer/profiler/   | grep -v indexing-backend/indexer/websvccommon/`) || exit 1

if [ "$JAVA6_HOME" != "" ] ; then
    (cd server/hudson; export JAVA_HOME=$JAVA6_HOME; export PATH=$JAVA_HOME/bin:$PATH; mvn $MAVEN_EXTRA_ARGS -DskipTests=true -Dmaven.test.skip=true clean package && (cp target/*.hpi ../../build || true)) || exit
fi;

mkdir -p ide/local/release/index-server
(cd server/web/web.main; cp -r dist/* ../../../ide/local/release/index-server) || exit 1

(cd server/tests; ./run-integration-tests) || exit 1
