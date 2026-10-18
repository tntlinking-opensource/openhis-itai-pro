/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.dailysettlement.dto;

import com.zxplat.application.shared.financial.dto.DailySettlementExpenseNatureSummaryDto;
import com.zxplat.application.shared.financial.dto.DailySettlementSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院日结收入汇总（IF-IP-DCL-02）；公共头 + 费用性质动态块；
 * 含住院特有预交金及多退少补拆列（{@code depositRefundAmount}/{@code settleSupplementAmount}）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结收入汇总")
public class InpatientDailySettlementSummaryDto {

    /** 日期范围。 */
    @Schema(description = "日期范围")
    private String dateRange;

    /** 总收入（fin_type 上半区折后合计；不含预交金）。 */
    @Schema(description = "总收入（折后；不含预交金）")
    private String totalIncome;

    /**
     * 药品合计金额（折后；西药+外购+中草药+中成药 fin_type 代数和；不含预交金；不改变 totalIncome 口径）。
     */
    @Schema(description = "药品合计金额（折后；西药/外购/中草药/中成药）")
    private String medicationTotalAmount;

    /** 应收（折后总收入 + 折扣合计）。 */
    @Schema(description = "应收（折后总收入+折扣）")
    private String receivableAmount;

    /** 折扣合计（仅命中折扣配置的行）。 */
    @Schema(description = "折扣合计")
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
     * 医保上传差额合计（院内零售−2301 上传；带符号代数和；勾稽项，不入总收入）。
     */
    @Schema(description = "医保上传差额合计（零售−上传）")
    private String chsRetailUploadDiffAmount;

    /** 预交金小计（住院特有；独立汇总，不参与 totalIncome 及其他下半区加和）。 */
    @Schema(description = "预交金小计（独立展示，不参与总收入加和）")
    private String depositAmount;

    /**
     * 退预交金合计（多退）：窗内 {@code HOSPITAL_DEPOSIT} 逆向流水绝对值代数和；
     * 含出院结算多退现金与预交金页手工退；独立展示，不参与 totalIncome。
     */
    @Schema(description = "退预交金合计（多退；不参与总收入）")
    private String depositRefundAmount;

    /**
     * 结算补收合计（少补）：住院结算单上自付渠道实付代数和（DD 的 C；现金/微信/银联等）；
     * 不是补预交金；排除统筹/个账/冲抵；独立展示，不参与 totalIncome。
     */
    @Schema(description = "结算补收合计（结算渠道实付；不参与总收入）")
    private String settleSupplementAmount;

    /** 退还合计。 */
    @Schema(description = "退还合计")
    private String refundTotal;

    /** 按费用性质（fin_contract）动态汇总块。 */
    @Schema(description = "费用性质汇总")
    private List<DailySettlementExpenseNatureSummaryDto> expenseNatureSummaries = new ArrayList<>();

    /** 下半区扁平展示行（前端主渲染源）。 */
    @Schema(description = "下半区展示行")
    private List<DailySettlementSummaryRowDto> summaryRows = new ArrayList<>();
}
