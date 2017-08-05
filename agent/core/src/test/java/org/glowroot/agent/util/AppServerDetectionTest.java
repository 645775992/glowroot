/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AppServerDetectionTest {

    @Test
    public void test() {
        checkJavaSunCommand(null, false);
        checkJavaSunCommand("", false);
        checkJavaSunCommand("org.example.Main", false);
        checkJavaSunCommand("org.jboss.modules.Main", true);
        checkJavaSunCommand("org.jboss.modules.Main 1 2", true);
        checkJavaSunCommand("path/to/jboss-modules.jar", true);
        checkJavaSunCommand("path/to/jboss-modules.jar 1 2", true);
        checkJavaSunCommand("org.tanukisoftware.wrapper.WrapperSimpleApp org.example.Main", false);
        checkJavaSunCommand("org.tanukisoftware.wrapper.WrapperSimpleApp org.jboss.modules.Main",
                true);
        checkJavaSunCommand(
                "org.tanukisoftware.wrapper.WrapperSimpleApp org.jboss.modules.Main 1 2", true);
        checkJavaSunCommand("org.tanukisoftware.wrapper.WrapperSimpleApp path/to/jboss-modules.jar",
                true);
        checkJavaSunCommand(
                "org.tanukisoftware.wrapper.WrapperSimpleApp path/to/jboss-modules.jar 1 2", true);
    }

    private static void checkJavaSunCommand(String javaSunCommand, boolean jbossModules) {
        String previous = System.getProperty("sun.java.command");
        setJavaSunCommand(javaSunCommand);
        try {
            assertThat(AppServerDetection.isJBossModules(AppServerDetection.makeCommand()))
                    .isEqualTo(jbossModules);
        } finally {
            setJavaSunCommand(previous);
        }
    }

    private static void setJavaSunCommand(String value) {
        if (value == null) {
            System.clearProperty("sun.java.command");
        } else {
            System.setProperty("sun.java.command", value);
        }
    }
}
