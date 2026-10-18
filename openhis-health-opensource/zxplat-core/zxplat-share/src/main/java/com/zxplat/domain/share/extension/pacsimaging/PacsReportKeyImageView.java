/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsimaging;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 报告侧关键图预览行（跨模块 SPI）。
 */
@Getter
@Setter
public class PacsReportKeyImageView {

    /** Media 主键。 */
    private Long mediaId;

    /** 内容类型。 */
    private String contentType;

    /** 来源枚举 code。 */
    private String sourceEnum;

    /** 预签名预览 URL。 */
    private String previewUrl;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
