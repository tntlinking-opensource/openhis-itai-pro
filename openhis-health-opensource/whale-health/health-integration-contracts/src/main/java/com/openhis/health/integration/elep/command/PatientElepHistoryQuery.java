/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.elep.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 患者历史处方列表查询。 */
@Data
@Accessors(chain = true)
public class PatientElepHistoryQuery {

    private Long patientId;
    private Long currentEncounterId;
    private String contractBusNo;
    private int page;
    private int pageSize;
}
