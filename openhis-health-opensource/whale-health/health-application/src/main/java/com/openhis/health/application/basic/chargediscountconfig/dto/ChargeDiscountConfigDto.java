/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费折扣配置列表/详情行（IF-CDC-02；含目录展示名 enrich）。
 */
@Getter
@Setter
public class ChargeDiscountConfigDto {

    /**
     * 配置主键（JSON 字符串，避免前端 Long 精度丢失）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 产品定义表名（三大目录之一）。
     */
    private String productTable;

    /**
     * 产品表展示名（药品/诊疗/耗材）。
     */
    private String productTableName;

    /**
     * 大类编码（大类行非空）。
     */
    private String categoryCode;

    /**
     * 大类展示名（术语 enrich）。
     */
    private String categoryName;

    /**
     * 产品定义主键（单项行非空；JSON 字符串）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /**
     * 产品名称（目录 enrich）。
     */
    private String productName;

    /**
     * 折扣率；空=款员定率。
     */
    private BigDecimal discountRate;

    /**
     * 状态 code（ACTIVE/INACTIVE）。
     */
    private String statusEnum;

    /**
     * 状态展示名。
     */
    private String statusEnumText;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 院区标识（可空；JSON 字符串）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
}
