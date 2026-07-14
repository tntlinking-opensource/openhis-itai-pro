/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * application 层架构约束：禁止编译期依赖 bridge / gateway 出站实现。
 */
class CrbApplicationArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import com.openhis.whale.crb.bridge.",
            "import com.openhis.whale.crb.gateway.");

    @Test
    void mainSourcesMustNotImportBridgeOrGatewayLayer() throws IOException {
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
                for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                    if (line.startsWith(prefix)) {
                        fail("Forbidden import at " + path + ":" + (i + 1) + " -> " + line);
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
