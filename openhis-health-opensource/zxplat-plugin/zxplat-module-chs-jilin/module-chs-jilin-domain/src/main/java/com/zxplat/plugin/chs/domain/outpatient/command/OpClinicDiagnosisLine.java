/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊预结算临床诊断行（供 2203 {@code diseinfo} 多行上传）。
 *
 * <p>由 bridge ContextLoader 从就诊诊断关联装载；挂号费兜底时通常仅 1 行。</p>
 */
@Data
@Accessors(chain = true)
public class OpClinicDiagnosisLine {

    /**
     * 诊断序号（{@code diag_srt_no}，从 1 起；主诊优先为最小序号）。
     */
    private Integer diagSrtNo;

    /**
     * 国家医保诊断编码（定义 {@code yb_no}，禁止 {@code "-"}）。
     */
    private String diagCode;

    /**
     * 诊断名称。
     */
    private String diagName;

    /**
     * 诊断类别（平台 {@code diag_type}：1 西医 / 2 中医主病 / 3 中医主证）。
     */
    private String diagType;

    /**
     * 是否主诊（平台 {@code maindiag_flag}：1 是 / 0 否）。
     */
    private String maindiagFlag;
}
