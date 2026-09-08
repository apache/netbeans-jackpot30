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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author lahvac
 */
public class GHSite implements SiteWrapper {
    public static final Factory FACTORY = new FactoryImpl();

    private final GitHub github;

    public GHSite(String authToken) throws IOException {
        this.github = authToken != null ? GitHub.connectUsingOAuth(authToken) : GitHub.connect();
    }

    @Override
    public void createCommitStatusPending(String fullRepoName, String sha, String text) throws IOException {
        GHRepository statusTarget = github.getRepository(fullRepoName);
        
        statusTarget.createCommitStatus(sha, GHCommitState.PENDING, null, text);
    }

    @Override
    public void createCommitStatusSuccess(String fullRepoName, String sha, String text) throws IOException {
        GHRepository statusTarget = github.getRepository(fullRepoName);
        
        statusTarget.createCommitStatus(sha, GHCommitState.SUCCESS, null, text);
    }

    @Override
    public void createReviewComment(String fullRepoName, int prId, String comment, String sha, String filename, int targetPosition) throws IOException {
        GHRepository commentTarget = github.getRepository(fullRepoName);
        GHPullRequest pr = commentTarget.getPullRequest(prId);
        pr.createReviewComment(comment, sha, filename, targetPosition);
    }

    @Override
    public List<ReviewComment> getReviewComments(String fullRepoName, int prId) throws IOException {
        GHRepository commentTarget = github.getRepository(fullRepoName);
        GHPullRequest pr = commentTarget.getPullRequest(prId);
        return pr.listReviewComments().asList().stream().map(c -> new ReviewComment(c.getPath(), c.getPosition(), c.getBody())).collect(Collectors.toList());
    }

    public static class FactoryImpl implements Factory {

        @Override
        public SiteWrapper create(String authToken) throws IOException {
            return new GHSite(authToken);
        }
        
    }
}
