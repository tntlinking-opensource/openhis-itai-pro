/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** A0002 医保电子签名命令。 */
@Data
@Accessors(chain = true)
public class ElepSignCommand {

    private String contractBusNo;
    private Long prescriptionId;
    /** 可选：覆盖自动组装的平台 ElepSignatureInput。 */
    private Map<String, Object> platformBody;
}
