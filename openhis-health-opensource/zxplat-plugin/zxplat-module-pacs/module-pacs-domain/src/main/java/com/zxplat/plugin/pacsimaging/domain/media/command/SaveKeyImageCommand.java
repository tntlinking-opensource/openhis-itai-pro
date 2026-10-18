/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.media.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存关键图索引命令。
 */
@Getter
@Setter
public class SaveKeyImageCommand {

    /** 检查工单主键。 */
    private Long workItemId;

    /** 已写入对象存储的对象键。 */
    private String objectKey;

    /** 内容类型。 */
    private String contentType;

    /** 来源枚举 code。 */
    private String sourceEnum;

    /** 租户主键。 */
    private Long tenantId;

    /** 操作人主键。 */
    private Long operatorId;
}
