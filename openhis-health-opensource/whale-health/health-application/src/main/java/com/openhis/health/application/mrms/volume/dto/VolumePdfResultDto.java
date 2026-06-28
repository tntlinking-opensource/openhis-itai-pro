/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.volume.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 卷宗 PDF 预览结果 DTO。
 */
@Getter
@Setter
public class VolumePdfResultDto {

    /** PDF 内容（Base64 编码）。 */
    private String pdfBase64;

    /** PDF 页数。 */
    private Integer pageCount;

    /** 渲染完成时间。 */
    private Instant renderedAt;
}
