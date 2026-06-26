/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

/** 当前就诊电子处方摘要（含草稿/在途/已上传）。 */
@Data
@Accessors(chain = true)
public class ElepEncounterPrescriptionItemDto {

    private Long prescriptionId;
    private String hospRxno;
    private String hiRxno;
    private String prescriptionNo;
    private String localStatus;
    private String mainDiagName;
    private String drugSummary;
    private Instant prscTime;
    private String prscDrName;
}
