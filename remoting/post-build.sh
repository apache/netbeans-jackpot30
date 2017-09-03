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


cp -r ../duplicates/server/indexer/build/cluster/* build/indexing-backend/indexer/indexer
cp -r ../duplicates/ide/build/cluster/* build/indexing-backend/indexer/indexer
cp -r ../remoting/ide/build/cluster/* build/indexing-backend/indexer/indexer
cp -r ../language/ide/build/cluster/* build/indexing-backend/indexer/indexer
cp -r ../duplicates/server/web/*/dist/*.jar build/indexing-backend/web/lib
cp -r ../language/server/web/*/dist/*.jar build/indexing-backend/web/lib
cp -r ../language/server/web/*/dist/lib/*.jar build/indexing-backend/web/lib

(cd build; zip -r indexing-backend-feature-packed.zip indexing-backend) || exit 1
(cd build; zip -r indexing-backend-feature-packed-shortened.zip `find indexing-backend -type f | grep -v indexing-backend/indexer/enterprise/ | grep -v indexing-backend/indexer/apisupport/  | grep -v indexing-backend/indexer/cnd/   | grep -v indexing-backend/indexer/dlight/   | grep -v indexing-backend/indexer/harness/   | grep -v indexing-backend/indexer/ide/   | grep -v indexing-backend/indexer/java   | grep -v indexing-backend/indexer/nb/   | grep -v indexing-backend/indexer/platform/   | grep -v indexing-backend/indexer/profiler/   | grep -v indexing-backend/indexer/websvccommon/`) || exit 1

(cd server/tests; ./run-integration-tests) || exit 1
