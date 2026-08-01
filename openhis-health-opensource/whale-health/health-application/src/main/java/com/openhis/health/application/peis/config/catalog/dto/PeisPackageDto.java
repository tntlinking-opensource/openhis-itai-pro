/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

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

    /** 状态：active / inactive。 */
    private String statusEnum;

    /** 子项目 AD 主键列表。 */
    private List<Long> childActivityIds;
}
