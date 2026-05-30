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

package com.openhis.health.domain.administration.chargeitemdefinition.query;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 收费项目定义查询命令：按实例维度、批次或实例集合拉取价表候选。
 */
@Getter
@Setter
public class ChargeItemDefinitionQueryCommand {
    /** 实例表标识。 */
    private String instanceTable;
    /** 单个业务实例主键。 */
    private Long instanceId;
    /** 批次号过滤（可空表示不限批次）。 */
    private String lotNo;
    /** 批量实例主键（与 instanceTable 联用）。 */
    private Collection<Long> instanceIds;
}
