/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量汇总驳回响应（IF-IPS-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量汇总驳回响应")
public class RejectSummaryDispenseResponse {

    /** 本轮已成功驳回的汇总单号（Supply REJECT + 发放行释放 summary_no）。 */
    @Schema(description = "已驳回汇总单号")
    private List<String> summaryNos;

    /** 成功提示（批量时含处理张数）。 */
    @Schema(description = "成功提示")
    private String message;
}
