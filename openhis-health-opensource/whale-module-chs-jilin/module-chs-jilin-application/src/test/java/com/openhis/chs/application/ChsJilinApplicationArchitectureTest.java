/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * application 层架构约束：禁止编译期依赖 bridge。
 * <p>CHS 全 cap Invoker 已迁至 bridge；application main 源码禁止任何 {@code com.openhis.chs.gateway.*} import。
 * medicalinfo、reconciliation、catalog、inventory、foundation cap 分别有 cap 级 gateway 禁入测试。</p>
 */
class ChsJilinApplicationArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import com.openhis.chs.bridge.",
            "import com.openhis.health.integration.insurance.");

    private static final String GATEWAY_IMPORT_PREFIX = "import com.openhis.chs.gateway.";

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

    @Test
    void applicationMustNotImportGatewayLayer() throws IOException {
        Path mainRoot = Path.of("src/main/java");
        if (!Files.isDirectory(mainRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(mainRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImportsGlobally);
        }
    }

    @Test
    void medicalinfoCapMustNotImportGatewayLayer() throws IOException {
        Path medicalinfoRoot = Path.of("src/main/java/com/openhis/chs/application/medicalinfo");
        if (!Files.isDirectory(medicalinfoRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(medicalinfoRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImports);
        }
    }

    @Test
    void reconciliationCapMustNotImportGatewayLayer() throws IOException {
        Path reconciliationRoot = Path.of("src/main/java/com/openhis/chs/application/reconciliation");
        if (!Files.isDirectory(reconciliationRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(reconciliationRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImportsInReconciliation);
        }
    }

    @Test
    void catalogCapMustNotImportGatewayLayer() throws IOException {
        Path catalogRoot = Path.of("src/main/java/com/openhis/chs/application/catalog");
        if (!Files.isDirectory(catalogRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(catalogRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImportsInCatalog);
        }
    }

    @Test
    void inventoryCapMustNotImportGatewayLayer() throws IOException {
        Path inventoryRoot = Path.of("src/main/java/com/openhis/chs/application/inventory");
        if (!Files.isDirectory(inventoryRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(inventoryRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImportsInInventory);
        }
    }

    @Test
    void foundationCapMustNotImportGatewayLayer() throws IOException {
        Path foundationRoot = Path.of("src/main/java/com/openhis/chs/application/foundation");
        if (!Files.isDirectory(foundationRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(foundationRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(this::assertNoGatewayImportsInFoundation);
        }
    }

    private void assertNoForbiddenImports(Path path) {
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                    if (line.startsWith(prefix)) {
                        fail("Forbidden bridge import at " + path + ":" + (i + 1) + " -> " + line);
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImportsGlobally(Path path) {
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in application at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImports(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.contains("/com/openhis/chs/application/medicalinfo/")) {
            return;
        }
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in medicalinfo cap at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImportsInReconciliation(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.contains("/com/openhis/chs/application/reconciliation/")) {
            return;
        }
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in reconciliation cap at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImportsInCatalog(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.contains("/com/openhis/chs/application/catalog/")) {
            return;
        }
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in catalog cap at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImportsInInventory(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.contains("/com/openhis/chs/application/inventory/")) {
            return;
        }
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in inventory cap at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertNoGatewayImportsInFoundation(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (!normalized.contains("/com/openhis/chs/application/foundation/")) {
            return;
        }
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith(GATEWAY_IMPORT_PREFIX)) {
                    fail("Forbidden gateway import in foundation cap at " + path + ":" + (i + 1) + " -> " + line);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
