/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 患者历史处方摘要。 */
@Data
@Accessors(chain = true)
public class ElepPatientHistoryItem {

    private Long prescriptionId;
    private String hiRxno;
    private String hospRxno;
    private String localStatus;
    private String prscDrName;
    private String prscDeptName;
    private String mainDiagName;
    private String drugSummary;
    private Instant prscTime;
    private Boolean canContinuationRx;
}
