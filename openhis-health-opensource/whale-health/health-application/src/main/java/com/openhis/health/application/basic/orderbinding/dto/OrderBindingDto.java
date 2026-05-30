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

package com.openhis.health.application.basic.orderbinding.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务设备模板绑定列表行：来源项与目标（诊疗/设备）及数量、单位、状态。
 */
@Data
public class OrderBindingDto {

    /** 绑定记录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 绑定范围业务码（如全院）。 */
    private String rangeCode;

    /**
     * 前端口径：1=号源 2=用法 3=诊疗
     */
    private String typeCode;

    /**
     * 前端字段名保持 itemNo；领域真实字段为 itemId
     */
    private String itemNo;

    /** 目标记录所在物理/逻辑表名（与 {@link com.openhis.health.domain.share.constants.TableNameConstants} 对齐）。 */
    private String devActTable;

    /** 目标主键（诊疗定义 id 或设备定义 id）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long devActId;

    /** 绑定数量，须为正数。 */
    private BigDecimal quantity;

    /** 单位码（术语 unit）。 */
    private String unitCode;

    /**
     * 前端口径：1=启用 2=停用
     */
    private Integer statusEnum;
}
