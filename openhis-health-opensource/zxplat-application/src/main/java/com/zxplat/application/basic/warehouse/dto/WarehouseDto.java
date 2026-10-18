/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 库房/药房/科室药房列表与详情 DTO（与 {@link com.zxplat.domain.administration.location.entity.Location} 对齐字段）。
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
    /** 物理形态枚举 code（WAREHOUSE / PHARMACY / DEPARTMENT_PHARMACY）。 */
    private String formEnum;
    /** 物理形态展示文案。 */
    private String formEnumText;
    /** 所属机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
    /** 上级科室展示名称（科室药房列表/详情回显）。 */
    private String organizationName;
    /** 显示顺序。 */
    private Integer displayOrder;
    /** 子节点（历史兼容字段，库房维护页为平铺列表，恒为空）。 */
    private List<WarehouseDto> children = new ArrayList<>();
}
