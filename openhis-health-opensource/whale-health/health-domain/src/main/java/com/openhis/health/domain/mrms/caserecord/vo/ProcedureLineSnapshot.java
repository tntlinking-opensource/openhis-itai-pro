/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 手术行读模型快照（自动取值中间结构）。
 *
 * <p>由 {@link com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport#listProcedures}
 * 从已完成 {@code cln_procedure} + 医嘱活动定义组装，落库为 {@code mrms_homepage_procedure}。</p>
 */
@Getter
@Setter
public class ProcedureLineSnapshot {

    /** 手术/操作编码（4401 oprn_oprt_code，优先医保码/ProcedureCode/活动 bus_no）。 */
    private String oprnCode;

    /** 手术/操作名称（4401 oprn_oprt_name，来自活动定义名称）。 */
    private String oprnName;

    /** 手术日期（4401 oprn_date，来自 procedure 发生时间日期部分）。 */
    private LocalDate oprnDate;

    /** 是否主手术（按发生时间排序后首条为 true，对应 main_oprn_flag=1）。 */
    private Boolean main;
}
