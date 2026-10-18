/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitemdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新定价项展示信息命令：按实例主键触发同步名称等展示字段（具体字段由领域服务解析实例）。
 */
@Getter
@Setter
public class UpdatePricingItemInfoCommand {
    /** 业务实例主键。 */
    private Long instanceId;
    /**
     * 实例来源表名（与 {@code adm_charge_item_definition.instance_table} 一致）。
     * <p>有值时按「表名 + 实例 id」精确加载价项；无值时保留按实例 id 跨表汇总的历史行为。</p>
     */
    private String instanceTable;
    /** 操作人主键。 */
    private Long operatorId;
}
