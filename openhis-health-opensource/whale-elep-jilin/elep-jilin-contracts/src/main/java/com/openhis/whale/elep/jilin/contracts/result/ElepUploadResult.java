/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.elep.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方上传 Port 结果。 */
@Data
@Accessors(chain = true)
public class ElepUploadResult {

    private boolean success;
    private String hiRxno;
    private String rxTraceCode;
    private String message;
}
