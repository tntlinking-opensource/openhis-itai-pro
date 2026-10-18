/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.domain.media.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除关键图索引命令。
 */
@Getter
@Setter
public class DeleteKeyImageCommand {

    /** 关键图索引主键。 */
    private Long mediaId;

    /** 检查工单主键。 */
    private Long workItemId;

    /** 租户主键。 */
    private Long tenantId;

    /** 操作人主键。 */
    private Long operatorId;
}
