/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手术行读模型快照（自动取值中间结构）。
 *
 * <p>由 {@link com.zxplat.domain.mrms.caserecord.support.CaseRecordAutoFillSupport#listProcedures}
 * 从净态 {@code cln_procedure} + SR + 国临 PD + performer 组装，落库为 {@code mrms_homepage_procedure}。
 * 对齐 DM-MRMS-001-SDD §4.1；禁止 JSON。</p>
 */
@Getter
@Setter
public class ProcedureLineSnapshot {

    /** 手术国临编码（落库 oprn_oprt_code）。 */
    private String oprnCode;

    /** 手术/操作名称。 */
    private String oprnName;

    /** 手术日期。 */
    private LocalDate oprnDate;

    /** 手术时间（业务；非 occurrence_time）。 */
    private LocalDateTime oprnTime;

    /** 是否主手术。 */
    private Boolean main;

    /** 手术级别。 */
    private String oprnLevelCode;

    /** 切口类别。 */
    private String oprnIncisionCode;

    /** 麻醉方式。 */
    private String anstWayCode;

    /** 术者姓名。 */
    private String operDrName;

    /** 一助姓名。 */
    private String asit1Name;

    /** 二助姓名。 */
    private String asit2Name;

    /** 配合护士姓名。 */
    private String nurseName;

    /** 麻醉医师姓名。 */
    private String anstDrName;

    /** 麻醉开始。 */
    private LocalDateTime anstStartTime;

    /** 麻醉结束。 */
    private LocalDateTime anstEndTime;

    /** 样本。 */
    private String specimen;

    /** 备注。 */
    private String note;

    /** 送检。 */
    private Boolean sendLabFlag;
}
