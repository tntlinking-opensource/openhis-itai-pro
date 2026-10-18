/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PdfGenerateResultDto {

    /** 资源访问 URL（相对路径） */
    private String url;
    /** token（业务字段） */
    private String token;
    /** Base64 编码的 PDF 字节流，供前端预览/下载 */
    private String pdfBase64;
    /** PDF 页数 */
    private Integer pageCount;
    /** 内容与打印配置 SHA-256，用于缓存校验 */
    private String contentHash;
    /** renderedAt（业务字段） */
    private Instant renderedAt;
    /** previewTruncated（业务字段） */
    private Boolean previewTruncated;
}
