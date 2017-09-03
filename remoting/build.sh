#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


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
