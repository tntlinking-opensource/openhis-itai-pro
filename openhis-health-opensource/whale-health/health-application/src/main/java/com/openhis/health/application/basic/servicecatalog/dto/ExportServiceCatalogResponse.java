/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.dto;

import lombok.Data;

/**
 * 诊疗目录导出响应（xlsx 字节包装 + 截断标记）。
 */
@Data
public class ExportServiceCatalogResponse {

    /** 建议保存文件名（含时间戳）。 */
    private String fileName;

    /** HTTP Content-Type（xlsx MIME）。 */
    private String contentType;

    /** 文件二进制内容。 */
    private byte[] fileBytes;

    /** 实际写入 Excel 的数据行数（不含表头）。 */
    private Integer exportedRowCount;

    /** 是否因触达 {@code EXPORT_MAX_ROWS} 而截断。 */
    private Boolean truncated;
}
