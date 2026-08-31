/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementCategoryRowDto;
import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementDispenseItemDto;
import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementProductRowDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院药房日结查询响应（IF-IP-PDC-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药房日结查询响应")
public class PreviewInpatientPharmacyDailySettlementResponse {

    /** 大类固定四行汇总（含已交；v1 金额为零占位）。 */
    @Schema(description = "大类汇总行")
    private List<PharmacyDailySettlementCategoryRowDto> categoryRows;

    /** 品规净额汇总（净数量非零才展示）。 */
    @Schema(description = "品规汇总行")
    private List<PharmacyDailySettlementProductRowDto> productRows;

    /** 待交账发药笔数（仅未挂载；一发一退各计 1 笔，净额为 0 仍计笔数）。 */
    @Schema(description = "待交账发药笔数")
    private Integer pendingCount;

    /** 售价合计：主区各大类售价总额之和（与头表 total_income / 交账审批金额同口径；退药轧差后可为负）。 */
    @Schema(description = "售价合计")
    private BigDecimal totalIncome;

    /** 待交账发药明细（弹窗候选）。 */
    @Schema(description = "待交账发药明细")
    private List<PharmacyDailySettlementDispenseItemDto> items;

    /** 业务窗内出现过的发药人 Practitioner 主键。 */
    @Schema(description = "窗内发药人 Practitioner 主键列表")
    private List<Long> practitionerIdsInWindow;

    /** 窗内发药人下拉。 */
    @Schema(description = "窗内发药人选项")
    private List<OptionDto> practitionerOptions;

    /** 漏交硬栏：窗起点前最早未交账发药时刻。 */
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "漏交硬栏最早时刻；非空时禁止提交")
    private LocalDateTime gapHardBlockEarliestAt;

    /** 漏交硬栏提示文案。 */
    @Schema(description = "漏交硬栏提示文案")
    private String gapHardBlockMessage;

    /** 漏交软提醒：业务时间晚于 dateTo 的未交账条数。 */
    @Schema(description = "截止后未交账条数（仅提醒）")
    private Long gapSoftWarnCount;

    /** 待交明细净数量合计（可选）。 */
    @Schema(description = "待交明细净数量合计")
    private BigDecimal handoverTotalQuantity;
}
