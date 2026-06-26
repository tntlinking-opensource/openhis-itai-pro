/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方上传 Port 命令。 */
@Data
@Accessors(chain = true)
public class ElepUploadCommand {

    private Long encounterId;
    private Long contractId;
    private String prescriptionNo;
    private String hospRxno;
}
