/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断行读模型快照（自动取值中间结构）。
 *
 * <p>由 {@link com.zxplat.domain.mrms.caserecord.support.CaseRecordAutoFillSupport} 诊断 scope 方法组装，
 * 落库时按 {@link com.zxplat.domain.share.enums.HomepageDiagType} 拆分主/次行。</p>
 */
@Getter
@Setter
public class DiagnosisLineSnapshot {

    /** ICD-10 诊断编码（4401 diag_code，来自 condition_definition）。 */
    private String diagCode;

    /** ICD-10 诊断名称（4401 diag_name，definition 名或 condition 描述回落）。 */
    private String diagName;

    /** 是否主诊断（就诊诊断 primary_flag，映射 maindiag_flag）。 */
    private Boolean main;

    /** 入院病情字典码（4401 adm_cond，EMR 扩展 {@code AdmissionConditionResolver} 回填）。 */
    private String admCondCode;
}
