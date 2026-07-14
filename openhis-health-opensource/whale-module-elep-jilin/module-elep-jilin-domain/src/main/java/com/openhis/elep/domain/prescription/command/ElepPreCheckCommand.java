/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import com.openhis.elep.domain.prescription.result.ElepPdfInput;
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
    private ElepPdfInput pdfInput;
}
