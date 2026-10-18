/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 护士站左侧树 — 病区根节点。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区树节点")
public class NurseStationWardTreeNodeDto {

    @Schema(description = "病区位置主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 病区位置主键。 */
    private Long locationId;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String name;

    /** 位置编码。 */
    @Schema(description = "位置编码")
    private String busNo;

    /** 展示排序。 */
    @Schema(description = "展示排序")
    private Integer displayOrder;

    /** 子病房列表。 */
    @Schema(description = "子病房列表")
    private List<NurseStationHouseTreeNodeDto> houseNodes;
}
