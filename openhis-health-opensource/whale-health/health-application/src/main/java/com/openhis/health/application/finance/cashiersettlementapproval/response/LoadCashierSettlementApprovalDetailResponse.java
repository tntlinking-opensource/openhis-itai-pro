/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.response;

import com.openhis.health.application.finance.cashiersettlementapproval.dto.CashierSettlementApprovalHeaderDto;
import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementCategoryRowDto;
import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementProductRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 交账审批详情响应（IF-CS-APR-02 详情）。
 *
 * <p>头表 + 按挂载源表现算的日结体（收款 rows/summary 或药房大类/品规）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批详情响应")
public class LoadCashierSettlementApprovalDetailResponse {

    /** 交账头信息。 */
    @Schema(description = "交账头信息")
    private CashierSettlementApprovalHeaderDto header;

    /** 挂载源行笔数（与头表 paymentCount 或现算挂载数一致）。 */
    @Schema(description = "挂载源行笔数")
    private Integer mountedSourceCount;

    /** 明细区说明。 */
    @Schema(description = "明细区现算说明")
    private String detailBodyRecomputeNote;

    /** 收款日结上半区 fin_type 行（挂载现算）。 */
    @Schema(description = "收款日结上半区行")
    private Object paymentRows;

    /** 收款日结下半区汇总（挂载现算）。 */
    @Schema(description = "收款日结下半区汇总")
    private Object paymentSummary;

    /** 药房日结大类汇总（挂载现算）。 */
    @Schema(description = "药房日结大类汇总")
    private List<PharmacyDailySettlementCategoryRowDto> pharmacyCategoryRows;

    /** 药房日结品规汇总（挂载现算）。 */
    @Schema(description = "药房日结品规汇总")
    private List<PharmacyDailySettlementProductRowDto> pharmacyProductRows;
}
