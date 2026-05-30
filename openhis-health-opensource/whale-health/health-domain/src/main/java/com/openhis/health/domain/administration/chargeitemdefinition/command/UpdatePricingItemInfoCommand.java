/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.chargeitemdefinition.command;

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
