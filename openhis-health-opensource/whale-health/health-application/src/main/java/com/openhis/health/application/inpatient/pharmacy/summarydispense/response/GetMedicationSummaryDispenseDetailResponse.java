/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.response;

import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询汇总单详情响应（IF-IPS-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "查询汇总单详情响应")
public class GetMedicationSummaryDispenseDetailResponse {

    /** 汇总单业务号，与 {@code bus_no}、列表 {@code busNo} 一致。 */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 单头展示（申请/核发时间、药房、申请人、发放门控态等）。 */
    @Schema(description = "单头")
    private InpatientPharmacySummarySlipHeaderDto header;

    /** 合并 Supply 行（无批号）。 */
    @Schema(description = "合并明细行")
    private List<InpatientPharmacySummarySlipLineDto> mergedLines;

    /** 底层发放行（含批号/扣库字段）。 */
    @Schema(description = "发放行明细")
    private List<InpatientPharmacySummaryDispenseItemDto> dispenseItems;

    /** 是否已发药：仅以发放表同号行全部为 COMPLETED 为准。 */
    @Schema(description = "是否已发药（发放表终态）")
    private Boolean summaryDispenseCompleted;
}
