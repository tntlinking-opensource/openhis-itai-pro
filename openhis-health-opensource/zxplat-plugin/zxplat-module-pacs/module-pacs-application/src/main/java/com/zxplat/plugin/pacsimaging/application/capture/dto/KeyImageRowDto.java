/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 关键图列表行（含预签名 URL）。
 */
@Getter
@Setter
public class KeyImageRowDto {

    /** 关键图索引主键。 */
    private Long mediaId;

    /** 对象键。 */
    private String objectKey;

    /** 内容类型。 */
    private String contentType;

    /** 来源枚举 code。 */
    private String sourceEnum;

    /** 预签名 GET URL。 */
    private String url;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
