/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 患者历史处方列表查询。 */
@Data
@Accessors(chain = true)
public class ElepPatientHistoryQuery {

    private Long patientId;
    private Long currentEncounterId;
    private String contractBusNo;
    private int page;
    private int pageSize;
}
