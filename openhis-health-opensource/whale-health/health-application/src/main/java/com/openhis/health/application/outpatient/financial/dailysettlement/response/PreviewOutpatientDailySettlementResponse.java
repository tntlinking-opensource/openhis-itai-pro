/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.response;

import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementChannelRowDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementPaymentItemDto;
import com.openhis.health.application.outpatient.financial.dailysettlement.dto.DailySettlementSummaryDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊日结查询响应（IF-OP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结查询响应")
public class PreviewOutpatientDailySettlementResponse {

    /** 未交账候选 payment 流水条数（BR-CS-011：含 0 元与收支抵净，不因净额排除；与 items.size 一致）。 */
    @Schema(description = "待归档 payment 流水条数")
    private Integer paymentCount;

    /** 候选 payment 明细。 */
    @Schema(description = "候选 payment 明细")
    private List<DailySettlementPaymentItemDto> items;

    /** 渠道汇总行（可经 expenseProperty 过滤展示）。 */
    @Schema(description = "渠道汇总行")
    private List<DailySettlementChannelRowDto> rows;

    /** 收入汇总（可经 expenseProperty 过滤展示）。 */
    @Schema(description = "收入汇总")
    private DailySettlementSummaryDto summary;

    /** 本次待交账合计上半区（仅未交账；BR-OP-DCL-017）。 */
    @Schema(description = "本次待交账合计上半区")
    private List<DailySettlementChannelRowDto> handoverRows;

    /** 本次待交账合计下半区（仅未交账；BR-OP-DCL-017）。 */
    @Schema(description = "本次待交账合计下半区")
    private DailySettlementSummaryDto handoverSummary;

    /** 漏交硬栏：截止前窗外最早未交账业务时刻（DD §2.6.5）。 */
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "漏交硬栏最早时刻；非空时禁止提交")
    private LocalDateTime gapHardBlockEarliestAt;

    /** 漏交硬栏提示文案。 */
    @Schema(description = "漏交硬栏提示文案")
    private String gapHardBlockMessage;

    /** 漏交软提醒：业务时间晚于 dateTo 的未交账条数。 */
    @Schema(description = "截止后未交账条数（仅提醒）")
    private Long gapSoftWarnCount;

    /** 业务窗内出现过的 enterer Practitioner 主键（自动带出）。 */
    @Schema(description = "窗内收费人 Practitioner 主键列表")
    private List<Long> practitionerIdsInWindow;

    /** 窗内收费人下拉（由 practitionerIdsInWindow 解析）。 */
    @Schema(description = "窗内收费人选项")
    private List<OptionDto> practitionerOptions;
}
