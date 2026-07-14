/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.homepage.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 首页 PDF 生成结果 DTO。
 */
@Getter
@Setter
public class PdfGenerateResultDto {

    /** Base64 编码 PDF 字节流。 */
    private String pdfBase64;

    /** PDF 页数。 */
    private Integer pageCount;

    /** 渲染完成时间。 */
    private Instant renderedAt;
}
