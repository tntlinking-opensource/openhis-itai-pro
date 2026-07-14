/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 重试待上报/失败进销存流水 */
@Data
@Accessors(chain = true)
public class RetryPendingInvUploadsCommand {

    /** 单次处理上限；空则用配置默认值 */
    private Integer batchSize;
}
