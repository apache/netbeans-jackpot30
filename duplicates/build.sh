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


(cd ide; ant "$@" clean && ant "$@" nbms && ant "$@" test) || exit 1
(cd server/indexer; ant "$@" clean && ant "$@" build && cp -r build/cluster build/indexer; cp -r ../../ide/build/cluster/* build/indexer/; cp -r ../../../remoting/ide/build/cluster/* build/indexer/; cd build; zip -r ../../../../remoting/build/duplicates-indexer.zip indexer/) || exit 1
(cd server/web/duplicates.web.api; ant "$@" clean && ant "$@") || exit 1

