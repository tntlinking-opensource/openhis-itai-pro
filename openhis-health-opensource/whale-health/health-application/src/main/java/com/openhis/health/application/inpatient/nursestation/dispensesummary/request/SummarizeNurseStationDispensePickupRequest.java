/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
/** 提交汇总领药。 */
@Schema(description = "提交汇总领药")
public class SummarizeNurseStationDispensePickupRequest {

    /** 待汇总发放单主键。 */
    @Schema(description = "待汇总发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> dispenseIds;

    /** 汇总目标药房库位。 */
    @Schema(description = "汇总目标药房库位（可选）")
    private Long locationId;
}
