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

package com.openhis.health.domain.custom.servicelocationtemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 取药科室模板查询模型。
 */
@Getter
@Setter
public class ServiceLocationTemplateQueryModel {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 开立科室/机构标识。
     */
    private Long organizationId;
    /**
     * 物品来源表名（药品/耗材等）。
     */
    private String itemTable;
    /**
     * 发放类别编码。
     */
    private String distributionCategoryCode;
    /**
     * 默认发药位置标识。
     */
    private Long defLocationId;
    /**
     * 物品定义主键（通配为空）。
     */
    private Long itemDefinitionId;
    /**
     * 给药途径编码（通配为空）。
     */
    private String methodCode;
    /**
     * 生效开始时刻（空表示全天起点）。
     */
    private LocalTime startTime;
    /**
     * 生效结束时刻（空表示全天终点）。
     */
    private LocalTime endTime;
    /**
     * 匹配优先级（数值越小越优先）。
     */
    private Integer priority;
    /**
     * 展示顺序（可空）。
     */
    private Integer displayOrder;
    /**
     * 备注（可空）。
     */
    private String remark;
}
