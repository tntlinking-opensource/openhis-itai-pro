/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.pdf.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@Getter
@RequiredArgsConstructor
public class PdfRenderResult {

    private final byte[] pdfBytes;
    private final int pageCount;
    private final boolean previewTruncated;
    private final Instant renderedAt = Instant.now();
}
