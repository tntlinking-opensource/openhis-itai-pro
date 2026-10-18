/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * application 层架构约束：禁止直触 gateway 传输层与 scheme（出站经 bridge）。
 */
class ElepJilinApplicationArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import com.zxplat.plugin.elep.gateway.client.",
            "import com.zxplat.plugin.elep.gateway.local.",
            "import com.zxplat.plugin.elep.gateway.remote.",
            "import com.zxplat.plugin.elep.gateway.scheme.",
            "import com.zxplat.plugin.elep.bridge.");

    @Test
    void mainSourcesMustNotImportGatewayTransportLayer() throws IOException {
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
            List<String> lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                    if (line.startsWith(prefix)) {
                        fail("Forbidden gateway transport import at " + path + ":" + (i + 1) + " -> " + line);
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
