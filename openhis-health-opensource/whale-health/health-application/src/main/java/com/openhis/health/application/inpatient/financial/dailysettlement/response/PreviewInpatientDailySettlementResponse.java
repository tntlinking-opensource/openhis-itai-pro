/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.response;

import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementChannelRowDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementPaymentItemDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院日结查询响应（IF-IP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结查询响应")
public class PreviewInpatientDailySettlementResponse {

    /** 未交账候选 payment 流水条数（BR-CS-011：含 0 元与收支抵净，与 items.size 一致）。 */
    @Schema(description = "待归档 payment 流水条数")
    private Integer paymentCount;

    /** 候选明细。 */
    @Schema(description = "候选明细")
    private List<InpatientDailySettlementPaymentItemDto> items;

    /** 渠道/分类汇总行。 */
    @Schema(description = "渠道/分类汇总行")
    private List<InpatientDailySettlementChannelRowDto> rows;

    /** 收入汇总。 */
    @Schema(description = "收入汇总")
    private InpatientDailySettlementSummaryDto summary;

    /** 本次待交账合计上半区（仅未交账；BR-IP-DCL-017）。 */
    @Schema(description = "本次待交账合计上半区")
    private List<InpatientDailySettlementChannelRowDto> handoverRows;

    /** 本次待交账合计下半区（仅未交账；BR-IP-DCL-017）。 */
    @Schema(description = "本次待交账合计下半区")
    private InpatientDailySettlementSummaryDto handoverSummary;
}
