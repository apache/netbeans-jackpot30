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
package org.netbeans.modules.java.debugjavac;

import java.util.List;

/**
 *
 * @author lahvac
 */
public interface Decompiler {
    public Result decompile(Input input);

    public final class Input {
        public String source;
        public List<String> params;

        public Input() {
        }

        public Input(String source, List<String> params) {
            this.source = source;
            this.params = params;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public List<String> getParams() {
            return params;
        }

        public void setParams(List<String> params) {
            this.params = params;
        }

    }
//    public final class Input {
//        public final String source;
//        public final List<String> params;
//        public Input(String source, List<String> params) {
//            this.source = source;
//            this.params = params;
//        }
//    }
    public final class Result {
        public String compileErrors;
        public String decompiledOutput;
        public String decompiledMimeType;
        public String exception;

        public Result() {
        }

        public Result(String compileErrors, String decompiledOutput, String decompiledMimeType) {
            this.compileErrors = compileErrors.trim().isEmpty() ? null : compileErrors;
            this.decompiledOutput = decompiledOutput.trim().isEmpty() ? null : decompiledOutput;
            this.decompiledMimeType = decompiledMimeType;
        }

        public Result(String exception) {
            this.exception = exception;
        }

        public String getCompileErrors() {
            return compileErrors;
        }

        public void setCompileErrors(String compileErrors) {
            this.compileErrors = compileErrors;
        }

        public String getDecompiledOutput() {
            return decompiledOutput;
        }

        public void setDecompiledOutput(String decompiledOutput) {
            this.decompiledOutput = decompiledOutput;
        }

        public String getDecompiledMimeType() {
            return decompiledMimeType;
        }

        public void setDecompiledMimeType(String decompiledMimeType) {
            this.decompiledMimeType = decompiledMimeType;
        }

        public String getException() {
            return exception;
        }

        public void setException(String exception) {
            this.exception = exception;
        }
    }
//    public final class Result {
//        public final String compileErrors;
//        public final String decompiledOutput;
//        public final String decompiledMimeType;
//        public Result(String compileErrors, String decompiledOutput, String decompiledMimeType) {
//            this.compileErrors = compileErrors.trim().isEmpty() ? null : compileErrors;
//            this.decompiledOutput = decompiledOutput.trim().isEmpty() ? null : decompiledOutput;
//            this.decompiledMimeType = decompiledMimeType;
//        }
//    }
}
