/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** A0004 撤销命令（Bridge API）。 */
@Data
@Accessors(chain = true)
public class ElepRevokeCommand {

    private String contractBusNo;
    private Long prescriptionId;
    /** 平台 ElepRevokeInput JSON 结构。 */
    private Map<String, Object> platformBody;
}
