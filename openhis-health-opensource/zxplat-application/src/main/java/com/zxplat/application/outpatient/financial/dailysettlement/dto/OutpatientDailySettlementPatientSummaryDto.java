/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊日结「患者分项」块（IF-OP-DCL-02 只读展示/打印；<strong>不</strong>参与交账写路径）。
 *
 * <p>金额口径与合计区 {@link DailySettlementSummaryDto} 同源；各患者块加总须勾回同次预览的合计
 * summary（**BR-OP-DCL-032**）。不含预交金相关列与医保上传差额。门诊特有：是否住院。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结患者分项汇总块")
public class OutpatientDailySettlementPatientSummaryDto {

    /** 分块序号（从 1 起，按日结窗内首次出现顺序）。 */
    @Schema(description = "分块序号（从 1 起）")
    private Integer seqNo;

    /** 就诊主键；无就诊上下文时可能为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者主键；无读模型时可能为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 门诊号（就诊 busNo）。 */
    @Schema(description = "门诊号")
    private String encounterBusNo;

    /**
     * 收费日期（年月日，{@code yyyy-MM-dd}）：取该患者分项桶内首笔有 {@code billDate} 的流水。
     */
    @Schema(description = "收费日期（yyyy-MM-dd）")
    private String chargeDate;

    /**
     * 是否住院：该患者存在未终态住院就诊（IMP，排除取消/错误录入/完诊/已完成）。
     */
    @Schema(description = "是否住院")
    private Boolean hospitalized;

    /** 是否住院展示文案（是/否）。 */
    @Schema(description = "是否住院展示文案")
    private String hospitalizedText;

    /**
     * 该患者在本窗内流水的下半区汇总（与合计同源算法；不含医保上传差额、不含预交金列）。
     */
    @Schema(description = "患者下半区汇总（不含医保上传差额）")
    private DailySettlementSummaryDto summary;
}
