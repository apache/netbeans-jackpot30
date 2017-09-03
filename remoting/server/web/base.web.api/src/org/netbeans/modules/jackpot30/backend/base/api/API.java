/**
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

package org.netbeans.modules.jackpot30.backend.base.api;

import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.codeviation.pojson.Pojson;
import org.netbeans.modules.jackpot30.backend.base.AccessStatistics;
import org.netbeans.modules.jackpot30.backend.base.CategoryStorage;

/**
 *
 * @author lahvac
 */
@Path("/index")
public class API {

    @GET
    @Path("/list")
    @Produces("text/plain")
    public String list() throws IOException {
        StringBuilder sb = new StringBuilder();

        for (CategoryStorage c : CategoryStorage.listCategories()) {
            sb.append(c.getId());
            sb.append(":");
            sb.append(c.getDisplayName());
            sb.append("\n");
        }

        return sb.toString();
    }

    @GET
    @Path("/internal/indexUpdated")
    @Produces("text/plain")
    public String indexUpdated() throws IOException {
        //XXX: should allow individual providers to do their own cleanup:
        
        CategoryStorage.internalReset();
        
        return "Done";
    }

    @GET
    @Path("/info")
    @Produces("text/plain")
    public String info(@QueryParam("path") String segment) throws IOException {
        CategoryStorage cat = CategoryStorage.forId(segment);

        return cat.getInfo();
    }

    @GET
    @Path("/accessStatistics")
    @Produces("text/plain")
    public String accessStatistics() throws IOException {
        return Pojson.save(AccessStatistics.getStatistics());
    }

}
