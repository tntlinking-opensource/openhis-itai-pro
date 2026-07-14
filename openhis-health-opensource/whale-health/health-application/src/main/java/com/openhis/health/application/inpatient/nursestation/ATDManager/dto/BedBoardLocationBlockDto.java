/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 床位列表 — 位置块（BR-IP-ATD-001）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位位置块")
public class BedBoardLocationBlockDto {

    @Schema(description = "床位位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 床位位置主键。 */
    private Long bedId;

    /** 床号/名称。 */
    @Schema(description = "床号/名称")
    private String bedName;

    /** 所属病房位置主键。 */
    @Schema(description = "所属病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String houseName;

    /** 床运营态 code：IN_USE/NOT_IN_USE。 */
    @Schema(description = "床运营态 code：IN_USE/NOT_IN_USE")
    private String operationalEnum;

    /** 排序序号。 */
    @Schema(description = "排序序号")
    private Integer displayOrder;
}
