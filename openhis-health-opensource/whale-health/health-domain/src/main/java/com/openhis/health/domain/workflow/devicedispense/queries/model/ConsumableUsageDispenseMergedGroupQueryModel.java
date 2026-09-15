/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材使用报表患者侧「按就诊+品规[+单位]」合并分组读模型。
 *
 * <p>由 Criteria {@code GROUP BY encounter_id, device_def_id, unit_code} 产出；
 * 净数量已按退药取负累加。金额由应用层对本页键集合回载明细后再 enrich 汇总。</p>
 */
@Getter
@Setter
public class ConsumableUsageDispenseMergedGroupQueryModel {

    /** 就诊主键（合并键组成部分；非空）。 */
    private Long encounterId;

    /** 耗材定义主键（合并键组成部分；非空）。 */
    private Long deviceDefId;

    /** 患者主键（组内取样；同就诊通常一致）。 */
    private Long patientId;

    /** 发放库位主键（组内取样；组内多库位时仅为代表值）。 */
    private Long locationId;

    /**
     * 单位码（合并键组成部分；可空——空与有值分键，避免异单位数量相加）。
     */
    private String unitCode;

    /** 净数量：发放为正、退药为负。 */
    private BigDecimal netQuantity;

    /** 组内命中明细行数。 */
    private long lineCount;

    /** 组内最大发放业务时间（分页排序键）。 */
    private LocalDateTime maxDispenseTime;

    /**
     * 组内任一条发放主键（如 {@code MAX(id)}），仅作可选 enrich 锚点；合并行展示须清空。
     */
    private Long sampleDispenseId;
}
