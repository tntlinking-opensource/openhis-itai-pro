/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更正入院科室时的可选床。
 */
@Data
@Accessors(chain = true)
@Schema(description = "入院科室变更可选床")
public class AdmissionDepartmentBedOptionDto {

    /** 床位 Location 主键。 */
    @Schema(description = "床位 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bedId;

    /** 床位所属病房 Location 主键。 */
    @Schema(description = "病房 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long houseId;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    private String bedName;

    /** 病房名称。 */
    @Schema(description = "病房名称")
    private String houseName;

    /** 为 true 表示该床是本就诊当前占用床，可选中以保持不换床。 */
    @Schema(description = "是否当前占用床")
    private Boolean currentBed;
}
