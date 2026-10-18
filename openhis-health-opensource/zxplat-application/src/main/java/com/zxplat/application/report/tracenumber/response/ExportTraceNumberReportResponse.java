/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.tracenumber.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-TRC-RPT-05：追溯码查询报表导出出参。
 */
@Getter
@Setter
public class ExportTraceNumberReportResponse {

    /** 导出文件名。 */
    private String fileName;

    /** MIME 类型。 */
    private String contentType;

    /** Excel 字节流。 */
    private byte[] body;

    /** 导出行数。 */
    private int exportedRowCount;

    /** 是否触达导出行数上限。 */
    private boolean truncated;
}
