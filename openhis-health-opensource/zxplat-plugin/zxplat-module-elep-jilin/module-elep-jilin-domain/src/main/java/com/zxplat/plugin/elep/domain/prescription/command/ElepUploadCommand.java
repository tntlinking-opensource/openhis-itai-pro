/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方上传 Client 命令。 */
@Data
@Accessors(chain = true)
public class ElepUploadCommand {

    private Long encounterId;
    private Long contractId;
    private String prescriptionNo;
    private String hospRxno;
}
