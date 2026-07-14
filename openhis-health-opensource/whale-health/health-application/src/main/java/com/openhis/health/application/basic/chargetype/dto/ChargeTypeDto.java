/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargetype.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ChargeTypeDto {

    /** 费用性质主键 {@code fin_charge_type.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 费用性质名称（展示用）。 */
    private String name;

    /** 业务编码，与 {@link #code} 一致，供检索与下拉 value 使用。 */
    private String busNo;

    /**
     * 为前端搜索兜底提供 code 字段（与 busNo 一致）。
     */
    private String code;

    /**
     * 费用性质分类（1/2/3/4/5）。
     */
    private String categoryEnum;

    /** 费用性质分类展示文案（由 {@link #categoryEnum} 解析）。 */
    private String categoryEnumText;

    /**
     * 前端约定：1=启用，2=停用。
     */
    private Integer activeFlag;

    /** 列表排序号，越小越靠前。 */
    private Integer displayOrder;
}
