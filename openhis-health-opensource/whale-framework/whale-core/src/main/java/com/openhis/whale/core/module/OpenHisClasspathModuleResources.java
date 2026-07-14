/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 从 classpath 加载 {@code META-INF/openhis/*.json} 模块描述文件。
 */
final class OpenHisClasspathModuleResources {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenHisClasspathModuleResources() {
    }

    static <T> List<T> loadAll(String resourcePath, Class<T> type) {
        List<T> result = new ArrayList<>();
        ClassLoader loader = OpenHisClasspathModuleResources.class.getClassLoader();
        try {
            Enumeration<URL> urls = loader.getResources(resourcePath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream()) {
                    result.add(MAPPER.readValue(in, type));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + resourcePath, ex);
        }
        return result;
    }
}
