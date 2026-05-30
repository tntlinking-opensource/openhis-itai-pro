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

package com.openhis.health.application.basic.warehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 库房/药房列表与树节点 DTO（与 {@link com.openhis.health.domain.administration.location.entity.Location} 对齐字段）。
 */
@Data
public class WarehouseDto {
    /** 位置主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 业务编号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 状态枚举 code。 */
    private String statusEnum;
    /** 状态展示文案。 */
    private String statusEnumText;
    /** 运营状态枚举 code。 */
    private String operationalEnum;
    /** 运营状态展示文案。 */
    private String operationalEnumText;
    /** 模式枚举 code。 */
    private String modeEnum;
    /** 模式展示文案。 */
    private String modeEnumText;
    /** 类型扩展属性（由库表 type_json 解析，禁止对外透出原始 JSON 串）。 */
    private Map<String, Object> typeExtensions;
    /** 拼音码。 */
    private String pyStr;
    /** 五笔码。 */
    private String wbStr;
    /** 物理形态枚举 code（仓库/药房）。 */
    private String formEnum;
    /** 物理形态展示文案。 */
    private String formEnumText;
    /** 所属机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 子节点（预留树结构，当前列表多为空子列表）。 */
    private List<WarehouseDto> children = new ArrayList<>();
}
