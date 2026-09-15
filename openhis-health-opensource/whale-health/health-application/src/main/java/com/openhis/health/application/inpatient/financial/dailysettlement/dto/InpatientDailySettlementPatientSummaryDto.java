/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院日结「患者分项」块（IF-IP-DCL-02 只读展示/打印；<strong>不</strong>参与交账写路径）。
 *
 * <p>金额口径与合计区 {@link InpatientDailySettlementSummaryDto} 同源；
 * 各患者块加总须勾回同次预览的合计 summary（**BR-IP-DCL-030**）。
 * 不含医保上传差额（患者分项不展示该勾稽项）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结患者分项汇总块")
public class InpatientDailySettlementPatientSummaryDto {

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

    /** 住院号（就诊 busNo）。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 病区名。 */
    @Schema(description = "病区名")
    private String wardName;

    /** 床位名。 */
    @Schema(description = "床位名")
    private String bedName;

    /**
     * 该患者在本窗内流水的下半区汇总（与合计同源算法；不含医保上传差额）。
     */
    @Schema(description = "患者下半区汇总（不含医保上传差额）")
    private InpatientDailySettlementSummaryDto summary;
}
