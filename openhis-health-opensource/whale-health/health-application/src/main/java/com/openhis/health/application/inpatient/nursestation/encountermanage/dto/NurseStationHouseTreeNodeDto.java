/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 护士站左侧树 — 病房子节点。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病房树节点")
public class NurseStationHouseTreeNodeDto {

    @Schema(description = "病房位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 病房位置主键。 */
    private Long locationId;

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String name;

    /** 位置编码。 */
    @Schema(description = "位置编码")
    private String busNo;

    /** 展示排序。 */
    @Schema(description = "展示排序")
    private Integer displayOrder;
}
