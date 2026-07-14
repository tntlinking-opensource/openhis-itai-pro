/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import com.openhis.elep.domain.prescription.result.ElepPdfInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 本地 PDF 生成命令（A0001 成功后、A0002 之前）。 */
@Data
@Accessors(chain = true)
public class ElepPdfGenerateCommand {

    private String contractBusNo;
    private Long prescriptionId;
    private ElepPdfInput pdfInput;
}
