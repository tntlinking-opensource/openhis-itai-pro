/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;
@Data
@Accessors(chain = true)
public class ElepPreCheckCommand {

    private String contractBusNo;
    private Long contractId;
    private Long encounterId;
    private Long patientId;
    /** 平台 PreCheckPrescription JSON 结构。 */
    private Map<String, Object> platformBody;
    /** 可选：A0001 成功后自动生成 PDF。 */
    private ElepPdfInputDto pdfInput;
}
