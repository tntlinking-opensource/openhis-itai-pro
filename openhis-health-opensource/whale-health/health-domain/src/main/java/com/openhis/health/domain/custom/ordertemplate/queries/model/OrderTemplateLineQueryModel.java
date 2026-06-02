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

package com.openhis.health.domain.custom.ordertemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 组套行查询模型。
 */
@Getter
@Setter
public class OrderTemplateLineQueryModel {
    /**
     * 模板行标识。
     */
    private Long id;
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 行类型枚举值（药品/诊疗/耗材）。
     */
    private String lineKindEnum;
    /**
     * 药品定义标识（lineKindEnum=药品时有值）。
     */
    private Long medicationDefinitionId;
    /**
     * 诊疗项目定义标识（lineKindEnum=诊疗时有值）。
     */
    private Long activityDefinitionId;
    /**
     * 耗材定义标识（lineKindEnum=耗材时有值）。
     */
    private Long deviceDefinitionId;
    /**
     * 分组键（可空）。
     */
    private String groupKey;
    /**
     * 行内排序（越小越靠前）。
     */
    private Integer sortOrder;
    /**
     * 默认数量（可空）。
     */
    private BigDecimal defaultQuantity;
    /**
     * 默认数量单位编码（可空）。
     */
    private String defaultUnitCode;
    /**
     * 默认天数（可空）。
     */
    private Integer defaultDays;
    /**
     * 频次标识（可空）。
     */
    private Long frequencyId;
    /**
     * 用法/途径编码（可空）。
     */
    private String routeCode;
    /**
     * 剂量文本（可空）。
     */
    private String dosageText;
    /**
     * 默认执行科室标识（可空）。
     */
    private Long defaultPerformOrgId;
    /**
     * 行备注（可空）。
     */
    private String lineRemark;
}
