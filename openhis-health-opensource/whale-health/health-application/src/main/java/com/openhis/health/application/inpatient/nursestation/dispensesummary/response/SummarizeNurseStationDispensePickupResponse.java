/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.dispensesummary.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
/** 提交汇总领药响应。 */
@Schema(description = "提交汇总领药响应")
public class SummarizeNurseStationDispensePickupResponse {

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;
}
