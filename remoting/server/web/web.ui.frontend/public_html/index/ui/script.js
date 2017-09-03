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

function IndexDeclarationSearch($scope, $location, $http, $routeParams, $route) {
    DeclarationSearch($scope, $location, $http, $routeParams, $route);
    $scope.performQuery = function() {
        $location.url("/search?prefix=" + $scope.prefix);
        //no need to do the actual query - it will be done automatically when the route changes
    };
}
function DeclarationSearch($scope, $location, $http, $routeParams, $route) {
    $scope.performQuery = function() {
        $scope.$parent.loading = true;
        $location.url("/search?prefix=" + $scope.prefix);
        $http.get('/index/ui/searchSymbol?prefix=' + $scope.prefix).success(function(data) {
            var result = [];
            var index = 0;

            for (var projectId in data) {
                var projectData = data[projectId].found;
                var project = $scope.projects.get(projectId);

                for (var relPath in projectData) {
                    var symbols = projectData[relPath];
                    for (var j = 0; j < symbols.length; j++) {
                        symbols[j].project = project;
                        symbols[j].displayName = symbolDisplayName(symbols[j]).replace("&", "&amp;").replace("<", "&lt;");
                        symbols[j].enclosingFQN = symbols[j].enclosingFQN.replace("&", "&amp;").replace("<", "&lt;");
                        result[index++] = symbols[j];
                    }
                }
            }

            $scope.searchResult = result;
            $scope.$parent.loading = false;
        });
    };
    $scope.performQueryDelayed = _.debounce($scope.performQuery, 2000);
    $scope.getElementIcon = getElementIcon;
    $scope.acceptByKind = acceptByKind;
    var prefix = $route ? $route.current.params.prefix : null;
    $scope.prefix = prefix != null ? prefix : "";
    $scope.symbolSignature = symbolSignature;

    $scope.showSearch = false;
    $scope.showNextPrev = false;
    $scope.showTypes = true;
    $scope.showFields = true;
    $scope.showMethods = true;
    $scope.showOthers = true;
    
    if (prefix != null) {
        $scope.performQuery();
    }
}

//Copied from Icons, NetBeans proper
function getElementIcon(elementKind, modifiers) {
    var GIF_EXTENSION = ".gif";
    var PNG_EXTENSION = ".png";
    if ("PACKAGE" === elementKind) {
        return "package" + GIF_EXTENSION;
    } else if ("ENUM" === elementKind) {
        return "enum" + PNG_EXTENSION;
    } else if ("ANNOTATION_TYPE" === elementKind) {
        return "annotation" + PNG_EXTENSION;
    } else if ("CLASS" === elementKind) {
        return "class" + PNG_EXTENSION;
    } else if ("INTERFACE" === elementKind) {
        return "interface" + PNG_EXTENSION;
    } else if ("FIELD" === elementKind) {
        return getIconName("field", PNG_EXTENSION, modifiers);
    } else if ("ENUM_CONSTANT" === elementKind) {
        return "constant" + PNG_EXTENSION;
    } else if ("CONSTRUCTOR" === elementKind) {
        return getIconName("constructor", PNG_EXTENSION, modifiers);
    } else if ("INSTANCE_INIT" === elementKind
            || "STATIC_INIT" === elementKind) {
        return "initializer" + (modifiers.contains("STATIC") ? "Static" : "") + PNG_EXTENSION;
    } else if ("METHOD" === elementKind) {
        return getIconName("method", PNG_EXTENSION, modifiers);
    } else {
        return "";
    }
}

function acceptByKind(elementKind, showFields, showTypes, showMethods, showOthers) {
    if (   "ENUM" === elementKind || "ANNOTATION_TYPE" === elementKind
        || "CLASS" === elementKind || "INTERFACE" === elementKind) {
        return showTypes;
    } else if ("FIELD" === elementKind || "ENUM_CONSTANT" === elementKind) {
        return showFields;
    } else if ("CONSTRUCTOR" === elementKind || "METHOD" === elementKind) {
        return showMethods;
    } else {
        return showOthers;
    }
}

// Private Methods ---------------------------------------------------------

function getIconName(typeName, extension, modifiers) {
    var fileName = typeName;

    if (modifiers.indexOf("STATIC") > -1) {
        fileName += "Static";
    }
    if (modifiers.indexOf("PUBLIC") > -1) {
        fileName += "Public";
    } else if (modifiers.indexOf("PROTECTED") > -1) {
        fileName += "Protected";
    } else if (modifiers.indexOf("PRIVATE") > -1) {
        fileName += "Private";
    } else {
        fileName += "Package";
    }
    fileName += extension;
    return fileName;
}

function symbolDisplayName(symbol) {
    if (typeof symbol.kind === undefined) {
        alert("Undefined kind: " + symbol);
    }
    switch (symbol.kind) {
        case "METHOD":
        case "CONSTRUCTOR":
            return "" + symbol.simpleName + decodeMethodSignature(symbol.signature);
        default:
            return "" + symbol.simpleName;
    }
}

function decodeSignatureType(signature, pos) {
    var c = signature.charAt(pos[0]++);
    switch (c) {
        case 'V':
            return "void";
        case 'Z':
            return "boolean";
        case 'B':
            return "byte";
        case 'S':
            return "short";
        case 'I':
            return "int";
        case 'J':
            return "long";
        case 'C':
            return "char";
        case 'F':
            return "float";
        case 'D':
            return "double";
        case '[':
            return decodeSignatureType(signature, pos) + "[]";
        case 'L':
            {
                var lastSlash = pos[0];
                var result = "";

                while (signature.charAt(pos[0]) !== ';' && signature.charAt(pos[0]) !== '<') {
                    if (signature.charAt(pos[0]) === '/') {
                        lastSlash = pos[0] + 1;
                    }
                    if (signature.charAt(pos[0]) === '$') {
                        lastSlash = pos[0] + 1;
                    }
                    pos[0]++;
                }

                result += signature.substring(lastSlash, pos[0]);

                if (signature.charAt(pos[0]++) === '<') {
                    result += '<';

                    while (signature.charAt(pos[0]) !== '>') {
                        if (result.charAt(result.length - 1) !== '<') {
                            result += ", ";
                        }
                        result += decodeSignatureType(signature, pos);
                    }

                    result += '>';
                    pos[0] += 2;
                }


                return result;
            }
        case 'T':
            {
                var result = "";

                while (signature.charAt(pos[0]) !== ';') {
                    result += signature.charAt(pos[0]);
                    pos[0]++;
                }

                pos[0]++;

                return result;
            }
        case '+':
            return "? extends " + decodeSignatureType(signature, pos);
        case '-':
            return "? super " + decodeSignatureType(signature, pos);
        case '*':
            return "?";
        default:
            return "unknown";
    }
}
function decodeMethodSignature(signature) {
    var pos = [1];

    if (signature.charAt(0) === '<') {
        var b = 1;

        while (b > 0) {
            switch (signature.charAt(pos[0]++)) {
                case '<':
                    b++;
                    break;
                case '>':
                    b--;
                    break;
            }
        }
        ;

        pos[0]++;
    }

    var result = "(";

    while (signature.charAt(pos[0]) !== ')') {
        if (result.charAt(result.length - 1) !== '(') {
            result += ", ";
        }

        result += decodeSignatureType(signature, pos);
    }

    result += ')';

    return result;
}

function symbolSignature(symbolDescription) {
    var signature;

    switch (symbolDescription.kind) {
        case "METHOD":
            signature = "" + symbolDescription.kind + ":" + symbolDescription.enclosingFQN + ":" + symbolDescription.simpleName + ":" + symbolDescription.vmsignature;
            break;
        case "CLASS": case "INTERFACE": case "ENUM": case "ANNOTATION_TYPE":
            signature = "" + symbolDescription.kind + ":" + symbolDescription.fqn;
            break;
        default:
            signature = "" + symbolDescription.kind + ":" + symbolDescription.enclosingFQN + ":" + symbolDescription.simpleName;
    }

    return signature;
}

function ShowSourceCode($scope, $http, $routeParams, $location) {
    $scope.$parent.loading = true;

    $('#popup').dialog('close');
    $scope.$parent.showSearch = true;
    $scope.$parent.showNextPrev = true;
    $scope.$parent.prefix = "";

    var path = $routeParams.path;
    var relative = $routeParams.relative;

    $scope.gotoAction = function ($event) {
        $scope.$parent.loading = true;
        var pos = $($event.target).attr("jpt30pos");
        var declaration = $($event.target).attr("class").indexOf("declaration") !== (-1);
        $http.get('/index/ui/target?path=' + path + '&relative=' + relative + '&position=' + pos).success(function(parsedData) {
            $scope.$parent.loading = false;
            if (declaration && "signature" in parsedData) {
                $location.url("/usages?signature=" + escape(parsedData.signature));
            } else if ("position" in parsedData) {
                setHash($location, "p" + parsedData.position);
            } else if ("source" in parsedData) {
                $location.hash("p" + pos);
                $location.replace();
                $location.url("/showCode?path=" + parsedData.path + "&relative=" + parsedData.source + "&goto=" + parsedData.signature);
            } else if ("targets" in parsedData) {
                var popupContent = "The target element is defined in the following files:<br>";
                popupContent += "<ul>";

                for (var i = 0; i < parsedData.targets.length; i++) {
                    var categoryData = parsedData.targets[i];
                    popupContent += "<li>" + categoryData.rootDisplayName/*XXX: escape*/ + "<br>";

                    for (var f = 0; f < categoryData.files.length; f++) {
                        popupContent += "<img src='/index/icons/javaFile.png' alt='Java File'/>";
                        popupContent += "<a href='#/showCode?path=" + categoryData.rootPath + "&relative=" + categoryData.files[i] + "&goto=" + parsedData.signature + "'>" + categoryData.files[i] + "</a><br>";
                    }

                    popupContent += "</li><br>";
                }

                popupContent += "</ul><br>";
                $('#popup').html(popupContent)
                                .dialog({
                                    title: 'Show',
                                    width: 800 //XXX: hardcoded size
                                });
            } else if ("menu" in parsedData) {
                var menuDef = parsedData.menu;
                var popupContent = "";
                for (var i = 0; i < menuDef.length; i++) {
                    var menuItem = menuDef[i];
                    popupContent += '<a href="' + menuItem.url + '">' + menuItem.displayName + '</a><br>';
                    $('#popup').html(popupContent)
                                    .dialog({
                                        title: 'Show',
                                        width: 800 //XXX: hardcoded size
                                    });
                }
            } else if ("signature" in parsedData) {
                alert("Cannot find source file for class: " + parsedData.signature.split(":")[1]);
            } else {
                alert("Cannot resolve target on this place");
            }
        });
    };
    $scope.markOccurrencesAction = function ($event) {
        $scope.$parent.loading = true;
        var pos = $($event.target).attr("jpt30pos");
        $http.get('/index/ui/localUsages?path=' + path + '&relative=' + relative + '&position=' + pos).success(function(parsedData) {
            $scope.$parent.loading = false;
            addHighlights(parsedData);

            if (parsedData.length > 0) {
                $scope.$parent.currentHighlight = -1;

                for (var i = 0; i < parsedData.length; i++) {
                    if (parsedData[i][0] <= pos && pos <= parsedData[i][1]) {
                        break;
                    }
                    $scope.$parent.currentHighlight++;
                }

                $scope.$parent.highlights = parsedData;
                $scope.$parent.nextOccurrence();
            } else {
                $scope.$parent.currentHighlight = 0;
                $scope.$parent.highlights = [];
            }
        });
    };

    $http.get("/index/source/cat?path=" + escape(path) + "&relative=" + escape(relative)).success(function(data) {
        var sourceCode = data.replace(/\r\n/g, '\n');
        $scope.sourceCode = escapeHTML(sourceCode);
        $http.get("/index/ui/highlightData?path=" + escape(path) + "&relative=" + escape(relative)).success(function(parsedData) {
            doColoring(path, relative, parsedData.categories, parsedData.spans, $scope, $location, $routeParams, $http, sourceCode);
        });
    });
    
    $scope.browsedFile = relative;
    $scope.$parent.currentHighlight = 0;
    $scope.$parent.highlights = [];

    $scope.$parent.nextOccurrence = function() {
        setHash($location, "p" + $scope.$parent.highlights[++$scope.currentHighlight][0]);
    };

    $scope.$parent.prevOccurrence = function() {
        setHash($location, "p" + $scope.$parent.highlights[--$scope.currentHighlight][0]);
    };
}

function tokenColoring(code, tokenColoring, tokenSpans) {
    var current = 0;
    var coloredCode = "";
    var line = 1;
    coloredCode += '<table><tr><td class="unselectable">' + (line++) + "</td><td>";
    
    for (var i = 0; i < tokenColoring.length; i++ ) {
        var currentCode = code.slice(current, current+tokenSpans[i]);
        var byLines = currentCode.split("\n");
        var index = current;
        for (var j = 0; j < byLines.length; j++) {
            if (j > 0) {
                coloredCode += '</td></tr><tr><td class="unselectable">' + (line++) + "</td><td>";
                index++;
            }
            coloredCode += '<span id="p' + index + '" class="' + tokenColoring[i] + '" jpt30pos="' + current + '">' + byLines[j].replace(/&/g, '&amp;').replace(/</g, '&lt;') + "</span>";
            index += byLines[j].length;
        }
        current += tokenSpans[i];
    }

    coloredCode += "</td></tr></table>";

    return coloredCode;
}

function escapeHTML(source) {
    return source.replace(/&/g, '&amp;').replace(/</g, '&lt;');
}

function addHighlights(highlights) {
    $(".highlight").removeClass("highlight");

    for (var i = 0; i < highlights.length; i++) {
        var highlightStart = highlights[i][0];
        var highlightEnd = highlights[i][1];
        var highlightLen = highlightEnd - highlights[i][0];
        var startingHere = $("#p" + highlightStart);

        if (startingHere.length === 1 && $(startingHere[0]).text().length === highlightLen) {
            startingHere.addClass("highlight");
            continue;
        }

        //should use binary search or something
        $(document).find('span').each(function () {
            var id=$(this).attr('id');
            if (!id || id.indexOf("p") !== 0) return ;
            var start = parseInt($(this).attr('id').substring(1));
            var text = $(this).text();
            var end = start + text.length;

            if (highlightStart <= end && highlightEnd > start) {
                var clazz = $(this).attr('class');
                var jpt30pos = $(this).attr('jpt30pos');
                var result = "";

                if (start < highlightStart) {
                    result += '<span id="p' + start + '" class="' + clazz + '" jpt30pos="' + jpt30pos + '">' + escapeHTML(text.substring(0, Math.min(text.length, highlightStart - start))) + "</span>";
                }
                result += '<span id="p' + Math.max(start, highlightStart) + '" class="' + clazz + ' highlight" jpt30pos="' + Math.max(start, highlightStart) + '">' + escapeHTML(text.substring(highlightStart - start, Math.min(text.length, highlightEnd - start))) + "</span>";
                if (highlightEnd < end) {
                    result += '<span id="p' + (highlightStart + highlightLen) + '" class="' + clazz + '" jpt30pos="' + (highlightStart + highlightLen) + '">' + escapeHTML(text.substring(highlightEnd - start, end - start)) + "</span>";
                }

                $(this).replaceWith(result);
            }
        });
    }
}

function doColoring(path, relative, $highlights, $spans, $scope, $location, $routeParams, $http, sourceCode) {
    $scope.sourceCode = tokenColoring(sourceCode, $highlights, $spans);

    if (!$location.hash()) {
        var goto = $routeParams.goto;

        if (goto) {
            $http.get('/index/ui/declarationSpan?path=' + path + '&relative=' + relative + '&signature=' + unescape(goto)).success(function(parsedData) {
                if (parsedData[2] !== (-1)) {
                    setHash($location, "p" + parsedData[2]);
//                    $location.hash("p" + parsedData[2]);
                    $location.replace();
//                    alert(parsedData[2]);
                }
            });
        }
    } else {
        fixScrollingCrap($location.hash());
    }

    if (!$routeParams.goto && $routeParams.highlights) {
        $http.get($routeParams.highlights).success(function(parsedData) {
            addHighlights(parsedData);

            if (parsedData.length > 0) {
                $scope.$parent.currentHighlight = -1;
                $scope.$parent.highlights = parsedData;
                $scope.$parent.nextOccurrence();
            } else {
                $scope.$parent.currentHighlight = 0;
                $scope.$parent.highlights = [];
            }
        });
    }

    $scope.$parent.loading = false;
}

function setHash($location, newHash) {
//TODO: set a bookmarkable location without refreshing the route?
//    $location.hash(newHash);
    fixScrollingCrap(newHash);
}

function fixScrollingCrap(scrollToHash) {
    var whereToScroll = $("#" + scrollToHash);
    $('html, body').scrollTop(whereToScroll.offset().top - 50);
}

function topLevel($scope, $route, $routeParams, $location, $http) {
    $scope.$route = $route;
    $scope.$location = $location;
    $scope.$routeParams = $routeParams;

    $scope.globalPerformSearch = function() {
        $location.url("/search?prefix=" + $scope.prefix);
    };

    $http.get("/index/list").success(function(data) {
        var result = [];
        var lines = data.split('\n');
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].length === 0) continue;
            var colon = lines[i].indexOf(':');
            var c = new Object();
            c.id = lines[i].substring(0, colon);
            c.displayName = lines[i].substring(colon + 1);
            c.selected = true;
            result[i] = c;
        }
        result.sort(function(l, r) { return l.displayName < r.displayName ? -1 : l.displayName > r.displayName ? 1 : 0;});
        result.get = function(id) {
            for (var i = 0; i < result.length; i++) {
                if (result[i].id === id) return result[i];
            }
        };
        $scope.projects = result;
        $scope.projectCheckBoxChanged();
    });
    $scope.projects = [];
    $scope.allProjectsCheckBoxChanged = function() {
        for (var i = 0; i < $scope.projects.length; i++) {
            $scope.projects[i].selected = $scope.allProjectsCheckBox;
        }
    };
    $scope.projectCheckBoxChanged = function() {
        var checked = true;
        for (var i = 0; i < $scope.projects.length; i++) {
            checked = checked && $scope.projects[i].selected;
        }
        $scope.allProjectsCheckBox = checked;
    };
}

function UsagesList($scope, $route, $routeParams, $location, $http) {
    var signature = $routeParams.signature;
    $http.get("/index/ui/searchUsages?signature=" + escape($routeParams.signature)).success(function(data) {
        var result = [];
        var index = 0;

        for (var projectId in data) {
            var projectData = data[projectId];
            var project = $scope.projects.get(projectId);

            for (var file in projectData) {
                var usageDescription = projectData[file];
                usageDescription.file = file;
                usageDescription.project = project;
                result[index++] = usageDescription;
            }
        }
        
        result.sort(function(l, r) { return l.file.localeCompare(r.file); });
        result.sort(function(l, r) { return l.project.displayName.localeCompare(r.project.displayName); });

        $scope.usages = result;
    });

    var signatureParts = signature.split(":");

    switch (signatureParts[0]) {
        case "METHOD":
        case "CONSTRUCTOR":
            $scope.symbolDisplayName = "" + signatureParts[2] + decodeMethodSignature(signatureParts[3]) + " in " + signatureParts[1];
            break;
        case "FIELD":
        case "ENUM_CONSTANT":
            $scope.symbolDisplayName = "" + signatureParts[2] + " in " + signatureParts[1];
            break;
        default:
            $scope.symbolDisplayName = "" + signatureParts[1];
            break;
    }

    $scope.signatureKind = signatureParts[0];
    $scope.signature = signature;
    $scope.escape = escape;
    $scope.showUsages = true;
    $scope.showSubtypes = true;
    
    $scope.showSearch = true;
    $scope.showNextPrev = false;
}
