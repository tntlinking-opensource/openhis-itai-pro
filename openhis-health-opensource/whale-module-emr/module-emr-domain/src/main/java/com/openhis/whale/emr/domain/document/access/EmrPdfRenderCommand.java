/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.access;

import lombok.Getter;
import lombok.Setter;

/**
 * PDF 渲染入参（bridge / application 共享契约，不含 OpenPDF 实现细节）。
 */
@Getter
@Setter
public class EmrPdfRenderCommand {

    private String documentId;
    private String content;
    private Boolean previewMode = Boolean.TRUE;
    private Object patientId;
    private Object encounterId;
    private String documentTitle;
}
