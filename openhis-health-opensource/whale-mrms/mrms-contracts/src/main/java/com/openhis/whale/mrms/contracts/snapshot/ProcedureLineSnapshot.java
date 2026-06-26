/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 手术行读模型快照。
 */
@Getter
@Setter
public class ProcedureLineSnapshot {

    /** 手术编码。 */
    private String oprnCode;

    /** 手术名称。 */
    private String oprnName;

    /** 手术日期。 */
    private LocalDate oprnDate;

    /** 是否主手术。 */
    private Boolean main;
}
