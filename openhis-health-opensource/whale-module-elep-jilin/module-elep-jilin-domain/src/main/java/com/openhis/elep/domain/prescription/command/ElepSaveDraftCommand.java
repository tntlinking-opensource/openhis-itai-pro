/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 保存/更新电子处方草稿（DRAFT/FAILED）。 */
@Data
@Accessors(chain = true)
public class ElepSaveDraftCommand {

    private String contractBusNo;
    private Long contractId;
    private Long encounterId;
    private Long patientId;
    /** 空则新建；非空则更新已有草稿。 */
    private Long prescriptionId;
    /** A0001 PreCheckPrescription JSON 结构。 */
    private Map<String, Object> platformBody;
}
