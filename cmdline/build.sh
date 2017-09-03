#!/bin/bash -x

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


ant "$@" clean && ant "$@" build && ant "$@" test && (cd compiler; ant "$@" create-standalone-compiler && build/test/scripted/run )  && (cd tool; ant "$@" create-standalone-tool && build/test/scripted/run ) && (cd lib; ant "$@" create-cmdline-lib ) && (cd ap; ant "$@" build-ap-jar ) || exit 1
mvn $MAVEN_EXTRA_ARGS install:install-file -Dfile=tool/build/jackpot/jackpot.jar -DgroupId=org.netbeans.modules.jackpot30 -DartifactId=tool -Dversion=8.1-SNAPSHOT -Dpackaging=jar -DgeneratePom=true
(cd maven; mvn $MAVEN_EXTRA_ARGS install -DskipTests;  mvn $MAVEN_EXTRA_ARGS -Dmaven.executable=`which mvn` test)
MAVEN_REPO=`pwd`/build/.m2
mkdir -p "$MAVEN_REPO"
mvn $MAVEN_EXTRA_ARGS deploy:deploy-file -Dfile=tool/build/jackpot/jackpot.jar -DgroupId=org.netbeans.modules.jackpot30 -DartifactId=tool -Dversion=8.1-SNAPSHOT -Dpackaging=jar -DgeneratePom=true -DaltDeploymentRepository=temp::default::file://"$MAVEN_REPO" -Durl=file://"$MAVEN_REPO"
(cd maven; mvn $MAVEN_EXTRA_ARGS -DskipTests -DaltDeploymentRepository=temp::default::file://"$MAVEN_REPO" deploy)
(cd build; zip -r .m2.zip .m2)
