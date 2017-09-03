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


DIR=`dirname $0`
USERDIR=`mktemp -d`;
trap "rm -rf -- '$USERDIR'" EXIT

ID="$1"; shift
NAME="$1"; shift
TARGET="$1"; shift
ROOT_DIR="$1"; shift

$DIR/indexer/bin/indexer -J-Djava.awt.headless=true --userdir $USERDIR --nosplash --nogui -J-Xmx2048m -J-Dnetbeans.indexing.recursiveListeners=false --info "$JPT30_INFO" --category-id "$ID" --category-name "$NAME" --cache-target "$TARGET" --category-root-dir "$ROOT_DIR" --category-projects "$@"

exit
