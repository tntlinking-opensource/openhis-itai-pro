/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf.model;

import com.zxplat.plugin.emr.application.pdf.config.PrintConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenPDF 渲染入参（由 GeneratePdfRequest 转换）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class PdfRenderOptions {

    private PrintConfig printConfig;
    private String pageRange = "all";
    private List<String> regionNames;
    private boolean includeTrackChanges;
    private boolean previewMode = true;
    private int previewMaxPages = 20;
    private String documentTitle;
    private String documentTypeName;
    private Map<String, String> metadata = new HashMap<>();

    public Map<String, String> safeMetadata() {
        if (metadata != null) {
            return metadata;
        }
        return Map.of();
    }
}
