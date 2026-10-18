/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientPickupGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询汇总单摆药明细响应（IF-IP-SUM-09）：单头 + 按患者分组、同 medReqId 合并数量。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "查询汇总单摆药明细响应")
public class GetMedicationSummarySlipPatientDetailResponse {

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 单头（与 IF-IP-SUM-02/03 同字段；外层列表已有时可忽略）。 */
    @Schema(description = "单头")
    private NurseStationDispenseSummarySlipHeaderDto header;

    /** 按患者分组的摆药行。 */
    @Schema(description = "患者分组摆药明细")
    private List<NurseStationDispenseSummaryPatientPickupGroupDto> patientGroups;
}
