/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 完成执行命令。
 *
 * <p>{@code recordedTime} 可选：住院护士站医嘱执行写入护士认定的实际执行时刻；
 * 为空时领域层仅在实体尚未有记录时间时回落当前时刻（兼容门诊/划价等调用方）。</p>
 */
@Getter
@Setter
public class CompleteProcedureRecordCommand {
    /** 操作主键。 */
    private Long procedureId;
    /** 结果文本。 */
    private String resultText;
    /** 报告JSON。 */
    private String reportJson;
    /** 操作人主键。 */
    private Long operatorId;
    /**
     * 实际执行/登记时刻（写入 {@code cln_procedure.recorded_time}）。
     *
     * <p>住院医嘱执行：等于写槽位 {@code actualTime}（默认同计划时点，仅允许同日改时分）；
     * 汇总/发药/计费逻辑锚点仍用 {@code occurrence_time}（planTime），本字段仅事实与展示。</p>
     */
    private LocalDateTime recordedTime;

    /**
     * 手术时间；非空时覆盖。
     */
    private LocalDateTime procedureTime;

    /**
     * 麻醉开始时间；非空时覆盖。
     */
    private LocalDateTime anesthesiaStartTime;

    /**
     * 麻醉结束时间；非空时覆盖。
     */
    private LocalDateTime anesthesiaEndTime;

    /**
     * 切口类别（术中可填）；非空时覆盖。
     */
    private String incisionTypeCode;

    /**
     * 是否送检；非空时覆盖。
     */
    private Boolean sendLabFlag;

    /**
     * 样本；非空时覆盖。
     */
    private String specimen;

    /**
     * 备注；非空时覆盖。
     */
    private String note;
}
