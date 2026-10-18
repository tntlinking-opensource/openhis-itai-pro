/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量汇总核发响应（IF-IPS-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量汇总核发响应")
public class DispenseSummaryMedicationsResponse {

    /** 本轮已成功核发的汇总单号（扣库、发放 COMPLETED、Supply AGREE 均已提交）。 */
    @Schema(description = "已成功核发的汇总单号")
    private List<String> summaryNos;

    /** 成功提示；医保 3505 部分失败时追加警告文案。 */
    @Schema(description = "成功提示")
    private String message;
}
