/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务实例移除定价命令：根据实例表名与实例主键批量清理关联的收费项目定义/价表行。
 */
@Getter
@Setter
public class RemovePricingByInstanceCommand {
    /** 实例所在逻辑表名或类型标识（与持久化分层约定一致）。 */
    private String instanceTable;
    /** 业务实例主键。 */
    private Long instanceId;
    /** 操作人主键。 */
    private Long operatorId;
}
