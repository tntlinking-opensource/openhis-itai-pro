/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.dto;

import com.openhis.health.application.shared.financial.dto.DailySettlementExpenseNatureSummaryDto;
import com.openhis.health.application.shared.financial.dto.DailySettlementSummaryRowDto;
import com.openhis.health.application.shared.financial.dto.DailySettlementWorkInjurySummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 日结收入汇总（IF-OP-DCL-02）；公共头 + 费用性质动态块，无省/市硬字段。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结收入汇总")
public class DailySettlementSummaryDto {

    /** 展示用日期范围。 */
    @Schema(description = "日期范围")
    private String dateRange;

    /** 总收入净额。 */
    @Schema(description = "总收入净额")
    private String totalIncome;

    /** 应收（本期同 totalIncome）。 */
    @Schema(description = "应收")
    private String receivableAmount;

    /** 优惠金额。 */
    @Schema(description = "优惠")
    private String discountAmount;

    /** 实收。 */
    @Schema(description = "实收")
    private String actualAmount;

    /** 微信小计。 */
    @Schema(description = "微信小计")
    private String wechatAmount;

    /** 支付宝小计。 */
    @Schema(description = "支付宝小计")
    private String alipayAmount;

    /** 银行卡小计。 */
    @Schema(description = "银行卡小计")
    private String onlineBankAmount;

    /** 现金小计。 */
    @Schema(description = "现金小计")
    private String cashAmount;

    /**
     * 应交现金（应上交现金；与现金渠道小计同口径）。
     */
    @Schema(description = "应交现金（与现金渠道同口径）")
    private String cashDueAmount;

    /**
     * 医保统筹合计（各医保费用性质统筹/基金支出代数和）。
     */
    @Schema(description = "医保统筹合计")
    private String insuranceCompensationAmount;

    /**
     * 个人账户合计（各医保费用性质个人账户代数和）。
     */
    @Schema(description = "个人账户合计")
    private String insuranceAccountAmount;

    /**
     * 医保上传差额合计（院内零售−2204/2301 上传；带符号代数和；勾稽项，不入总收入）。
     */
    @Schema(description = "医保上传差额合计（零售−上传）")
    private String chsRetailUploadDiffAmount;

    /** 体检人次。 */
    @Schema(description = "体检人次")
    private Integer physicalExamCount;

    /** 退还合计。 */
    @Schema(description = "退还合计")
    private String refundTotal;

    /** 按费用性质（fin_contract）动态汇总块。 */
    @Schema(description = "费用性质汇总")
    private List<DailySettlementExpenseNatureSummaryDto> expenseNatureSummaries = new ArrayList<>();

    /**
     * 工伤独立统计（窗内有工伤合同 payment 时非空；不计入 totalIncome / 上半区 / 费用性质块）。
     */
    @Schema(description = "工伤独立统计")
    private DailySettlementWorkInjurySummaryDto workInjurySummary;

    /** 下半区扁平展示行（前端主渲染源）。 */
    @Schema(description = "下半区展示行")
    private List<DailySettlementSummaryRowDto> summaryRows = new ArrayList<>();
}
