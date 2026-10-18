/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.prescription.pdf;

import com.zxplat.plugin.elep.domain.config.ElepContractConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** PDF 输出路径规则（对齐 ybapp：{@code {output_path}{yyyyMMdd}/{hospRxno}.pdf}）。 */
public final class ElepPdfPathSupport {

    private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ElepPdfPathSupport() {
    }

    public static String buildOutputFilePath(ElepContractConfig config, String hospRxno) {
        String dateFolder = LocalDate.now().format(DATE_FOLDER);
        Path root = Paths.get(normalizeRoot(config.getOutputPath()));
        return root.resolve(dateFolder).resolve(hospRxno + ".pdf").toString();
    }

    private static String normalizeRoot(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("output_path 未配置");
        }
        String trimmed = outputPath.trim();
        if (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            return trimmed;
        }
        return trimmed + java.io.File.separator;
    }
}
