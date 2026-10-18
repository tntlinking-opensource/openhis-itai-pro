/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * PEIS 体检套餐 DTO（CFG-05）。
 */
@Data
public class PeisPackageDto {

    /** 套餐主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageActivityId;

    /** 套餐编码（busNo）。 */
    private String code;

    /** 套餐名称。 */
    private String display;

    /** 业务类型（术语 activity_type_code 码值）。 */
    private String typeEnum;

    /** 状态：active / inactive。 */
    private String statusEnum;

    /** 原价/零售价 SALE（charge_item_definition）。 */
    private BigDecimal salePrice;

    /** 子项目 AD 主键列表（元素须按字符串序列化，避免雪花 Long 在前端丢精度）。 */
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> childActivityIds;
}
