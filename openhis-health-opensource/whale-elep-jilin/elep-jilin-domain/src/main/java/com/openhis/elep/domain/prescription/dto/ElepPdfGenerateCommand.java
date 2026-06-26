/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地 PDF 生成命令（A0001 成功后、A0002 之前）。 */
@Data
@Accessors(chain = true)
public class ElepPdfGenerateCommand {

    private String contractBusNo;
    private Long prescriptionId;
    private ElepPdfInputDto pdfInput;
}
