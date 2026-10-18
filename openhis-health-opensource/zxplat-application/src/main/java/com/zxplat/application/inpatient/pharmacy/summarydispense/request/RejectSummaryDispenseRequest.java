/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量汇总驳回请求（IF-IPS-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量汇总驳回 IF-IPS-04", requiredProperties = {"summaryNos"})
public class RejectSummaryDispenseRequest {

    /** 待驳回汇总单号列表（须全 APPROVAL 且发放行全 SUMMARIZED）。 */
    @Schema(description = "待驳回汇总单号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> summaryNos;

    /** 驳回原因 code（落 SupplyRequest.reason/changeReason）。 */
    @Schema(description = "驳回原因 code")
    private String rejectReasonCode;
}
