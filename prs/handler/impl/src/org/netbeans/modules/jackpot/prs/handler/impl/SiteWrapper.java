/*
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
package org.netbeans.modules.jackpot.prs.handler.impl;

import java.io.IOException;
import java.util.List;

/**
 *
 * @author lahvac
 */
public interface SiteWrapper {

    public void createCommitStatusPending(String fullRepoName, String sha, String text) throws IOException;
    public void createCommitStatusSuccess(String fullRepoName, String sha, String text) throws IOException;

    public void createReviewComment(String fullRepoName, int prId, String comment, String sha, String filename, int targetPosition) throws IOException;
    public List<ReviewComment> getReviewComments(String fullRepoName, int prId) throws IOException;

    public static final class ReviewComment {
        public final String filename;
        public final int linenumber;
        public final String comment;

        public ReviewComment(String filename, int linenumber, String comment) {
            this.filename = filename;
            this.linenumber = linenumber;
            this.comment = comment;
        }

    }

    public static interface Factory {
        public SiteWrapper create(String authToken) throws IOException;
    }
}
