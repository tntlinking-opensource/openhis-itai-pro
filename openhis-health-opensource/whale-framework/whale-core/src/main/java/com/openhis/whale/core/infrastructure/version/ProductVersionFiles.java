/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;

/**
 * 业务产品版本：只读仓库/产物中的 VERSION 明文文件（非 POM / app.version）。
 */
public final class ProductVersionFiles {

    public static final String FILE_NAME = "VERSION";

    private ProductVersionFiles() {
    }

    /**
     * 读取展示用版本号（x.y.z[.n]）。找不到则返回 empty。
     */
    public static Optional<String> readDisplayVersion(Class<?> anchor) {
        Optional<String> fromClasspath = readClasspath(anchor);
        if (fromClasspath.isPresent()) {
            return fromClasspath;
        }

        Optional<String> fromJarSibling = readBesideCodeSource(anchor);
        if (fromJarSibling.isPresent()) {
            return fromJarSibling;
        }

        return readNearWorkingDirectory();
    }

    static Optional<String> readClasspath(Class<?> anchor) {
        ClassLoader cl = anchor.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl != null ? cl.getResourceAsStream(FILE_NAME) : null) {
            if (in == null) {
                try (InputStream root = anchor.getResourceAsStream("/" + FILE_NAME)) {
                    return readFirstMeaningfulLine(root);
                }
            }
            return readFirstMeaningfulLine(in);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> readBesideCodeSource(Class<?> anchor) {
        try {
            CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return Optional.empty();
            }
            Path location = Path.of(codeSource.getLocation().toURI());
            Path dir = Files.isDirectory(location) ? location : location.getParent();
            if (dir == null) {
                return Optional.empty();
            }
            return readFile(dir.resolve(FILE_NAME));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> readNearWorkingDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++) {
            Optional<String> hit = readFile(current.resolve(FILE_NAME));
            if (hit.isPresent()) {
                return hit;
            }
            // 开发时常在 whale-project/project-* 下启动
            hit = readFile(current.resolve("whale-sever").resolve(FILE_NAME));
            if (hit.isPresent()) {
                return hit;
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    static Optional<String> readFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path)) {
            return readFirstMeaningfulLine(in);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    static Optional<String> readFirstMeaningfulLine(InputStream in) throws IOException {
        if (in == null) {
            return Optional.empty();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                return Optional.of(stripSnapshot(trimmed));
            }
        }
        return Optional.empty();
    }

    static String stripSnapshot(String version) {
        if (version == null) {
            return null;
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        final String suffix = "-SNAPSHOT";
        if (trimmed.regionMatches(true, trimmed.length() - suffix.length(), suffix, 0, suffix.length())) {
            return trimmed.substring(0, trimmed.length() - suffix.length());
        }
        return trimmed;
    }
}
