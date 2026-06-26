/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** A0003 上传命令。 */
@Data
@Accessors(chain = true)
public class ElepFileUploadCommand {

    private String contractBusNo;
    private Long prescriptionId;
    /** 平台 ElepUploadInput JSON 结构。 */
    private Map<String, Object> platformBody;
}
