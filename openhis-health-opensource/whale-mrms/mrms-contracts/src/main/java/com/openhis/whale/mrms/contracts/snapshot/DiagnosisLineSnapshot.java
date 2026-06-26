/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断行读模型快照。
 */
@Getter
@Setter
public class DiagnosisLineSnapshot {

    /** ICD-10 编码。 */
    private String diagCode;

    /** ICD-10 名称。 */
    private String diagName;

    /** 是否主诊断。 */
    private Boolean main;

    /** 入院病情码。 */
    private String admCondCode;
}
