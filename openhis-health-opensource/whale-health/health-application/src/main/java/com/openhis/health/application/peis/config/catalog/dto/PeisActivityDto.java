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
 * PEIS 体检项目 DTO（CFG-04）。
 */
@Data
public class PeisActivityDto {

    /** 项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 项目编码（busNo）。 */
    private String code;

    /** 项目名称。 */
    private String display;

    /** 项目类型编码。 */
    private String typeEnum;

    /** 状态：active / inactive（映射 PublicationStatus）。 */
    private String statusEnum;

    /** 绑定小项主键列表。 */
    private List<Long> observationDefinitionIds;

    /** 执行科室机构主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
}
