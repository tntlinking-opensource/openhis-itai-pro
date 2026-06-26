/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 【A0006】取药结果查询命令（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformMedResultQueryCommand {

    private String contractBusNo;
    private Long contractId;
    private String hiRxno;
    private Map<String, Object> platformBody;
}
