/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊日结工伤独立统计（不计入总收入 / 上半区 / 费用性质公共块）。
 *
 * <p>分类金额按日结 fin_type 口径归并：西药+中成药(+外购)、诊疗合计、耗材、中草药、诊察费；
 * 另附与费用性质明细同构的统筹 / 个人自付行。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结工伤独立统计")
public class DailySettlementWorkInjurySummaryDto {

    /** 西药 + 中成药（含外购药品）金额。 */
    @Schema(description = "西药中成药金额")
    private String westernPatentMedicineAmount;

    /** 诊疗合计（上半区检查/检验/治疗等归并，不含药/草/耗/诊察费）。 */
    @Schema(description = "诊疗合计金额")
    private String treatmentAmount;

    /** 医用耗材金额。 */
    @Schema(description = "耗材金额")
    private String consumableAmount;

    /** 中草药金额。 */
    @Schema(description = "中草药金额")
    private String herbalMedicineAmount;

    /** 诊察费金额（含号源归并 1012）。 */
    @Schema(description = "诊察费金额")
    private String consultationFeeAmount;

    /** 人次（patientId 去重）。 */
    @Schema(description = "人次")
    private Integer personCount;

    /** 费用性质展示名（固定「工伤保险」）。 */
    @Schema(description = "费用性质名称")
    private String contractName;

    /** 个人自付（现金/微信/支付宝/银行卡等自付渠道代数和；工伤主渠道区已剔除故在此回收）。 */
    @Schema(description = "个人自付")
    private String cashAmount;

    /** 工伤统筹（基金）。 */
    @Schema(description = "统筹")
    private String fundAmount;

    /** 个人账户。 */
    @Schema(description = "个人账户")
    private String accountAmount;

    /** 总额（统筹+个人账户，与医保费用性质行同口径）。 */
    @Schema(description = "总额")
    private String totalAmount;
}
