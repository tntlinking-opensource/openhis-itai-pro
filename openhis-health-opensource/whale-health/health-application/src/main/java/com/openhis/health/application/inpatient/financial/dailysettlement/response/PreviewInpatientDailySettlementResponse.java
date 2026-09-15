/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.response;

import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementChannelRowDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementPatientSummaryDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementPaymentItemDto;
import com.openhis.health.application.inpatient.financial.dailysettlement.dto.InpatientDailySettlementSummaryDto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
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

    /**
     * 患者分项块列表（只读统计；与 {@link #summary} 同源口径按就诊/患者拆分；
     * 各块加总勾回合计；<strong>不</strong>影响交账提交，**BR-IP-DCL-030**）。
     */
    @Schema(description = "患者分项块列表（只读；不影响交账）")
    private List<InpatientDailySettlementPatientSummaryDto> patientSummaries;

    /** 本次待交账合计上半区（仅未交账；BR-IP-DCL-017）。 */
    @Schema(description = "本次待交账合计上半区")
    private List<InpatientDailySettlementChannelRowDto> handoverRows;

    /** 本次待交账合计下半区（仅未交账；BR-IP-DCL-017）。 */
    @Schema(description = "本次待交账合计下半区")
    private InpatientDailySettlementSummaryDto handoverSummary;

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
