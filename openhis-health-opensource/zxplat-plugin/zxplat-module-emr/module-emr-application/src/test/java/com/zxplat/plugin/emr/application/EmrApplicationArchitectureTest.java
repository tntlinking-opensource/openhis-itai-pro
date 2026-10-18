/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/** application 层架构约束：禁止依赖 bridge 源码包。 */
class EmrApplicationArchitectureTest {

    private static final String FORBIDDEN_IMPORT_PREFIX =
            "import com.zxplat.plugin.emr.bridge.";

    @Test
    void mainSourcesMustNotImportBridgeLayer() throws IOException {
        Path mainRoot = Path.of("src/main/java");
        if (!Files.isDirectory(mainRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(mainRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoForbiddenImports);
        }
    }

    private void assertNoForbiddenImports(Path path) {
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(FORBIDDEN_IMPORT_PREFIX)) {
                    fail("Forbidden bridge import at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
