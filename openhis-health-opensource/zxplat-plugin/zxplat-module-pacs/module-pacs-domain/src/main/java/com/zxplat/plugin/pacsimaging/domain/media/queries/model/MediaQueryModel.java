/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.media.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Media 读模型。
 */
@Getter
@Setter
public class MediaQueryModel {

    /** 主键。 */
    private Long id;

    /** 检查工单主键。 */
    private Long workItemId;

    /** 对象键（不含预签名 URL）。 */
    private String objectKey;

    /** 内容类型。 */
    private String contentType;

    /** 来源枚举 code。 */
    private String sourceEnum;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
