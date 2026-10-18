/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 为 AppService 动态文档追加模块级 tag：health / emr / mrms / chs。
 */
@Component
@Order(10)
public class ExtensionModuleOpenApiTagCustomizer implements OpenApiCustomizer {

    private static final String TAG_HEALTH = "health";
    private static final String TAG_EMR = "emr";
    private static final String TAG_MRMS = "mrms";
    private static final String TAG_CHS = "chs";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        Set<String> moduleTags = new LinkedHashSet<>();
        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
            String moduleTag = resolveModuleTag(operation);
            moduleTags.add(moduleTag);
            prependModuleTag(operation, moduleTag);
        }));
        ensureModuleTagDefinitions(openApi, moduleTags);
    }

    private static String resolveModuleTag(Operation operation) {
        String haystack = buildHaystack(operation);
        if (haystack.contains("com.zxplat.framework.emr") || haystack.contains(".emr.")) {
            return TAG_EMR;
        }
        if (haystack.contains("com.zxplat.framework.mrms") || haystack.contains(".mrms.")) {
            return TAG_MRMS;
        }
        if (haystack.contains("integration.insurance")
                || haystack.contains("Insurance")
                || haystack.contains("com.zxplat.chs")) {
            return TAG_CHS;
        }
        return TAG_HEALTH;
    }

    private static String buildHaystack(Operation operation) {
        StringBuilder builder = new StringBuilder();
        if (operation.getOperationId() != null) {
            builder.append(operation.getOperationId()).append(' ');
        }
        if (operation.getDescription() != null) {
            builder.append(operation.getDescription()).append(' ');
        }
        if (operation.getTags() != null) {
            builder.append(String.join(" ", operation.getTags()));
        }
        return builder.toString();
    }

    private static void prependModuleTag(Operation operation, String moduleTag) {
        List<String> tags = operation.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
        } else {
            tags = new ArrayList<>(tags);
            tags.remove(moduleTag);
        }
        tags.add(0, moduleTag);
        operation.setTags(tags);
    }

    private static void ensureModuleTagDefinitions(OpenAPI openApi, Set<String> moduleTags) {
        if (openApi.getTags() == null) {
            openApi.setTags(new ArrayList<>());
        }
        for (String moduleTag : moduleTags) {
            boolean exists = openApi.getTags().stream().anyMatch(tag -> moduleTag.equals(tag.getName()));
            if (!exists) {
                openApi.addTagsItem(new Tag().name(moduleTag).description(moduleTag + " module APIs"));
            }
        }
    }
}
