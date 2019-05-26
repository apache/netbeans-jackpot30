/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.jackpot.prs.handler.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/**
 *
 * @author lahvac
 */
public class HandlePullRequestTest {
    
    public HandlePullRequestTest() {
    }
    
    @Test
    public void testMain() throws Exception {
        HandlePullRequest.processPullRequest(Files.readString(Paths.get("/tmp/pr"), StandardCharsets.UTF_8), null, null);
    }

}
