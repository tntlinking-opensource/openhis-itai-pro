/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 医嘱删除校验查询模型。
 */
@Getter
@Setter
public class MedicationRequestDeleteCheckQueryModel {
    /** deletable。 */
    private Boolean deletable;
    /** 原因编码。 */
    private String reasonCode;
    /** charged。 */
    private Boolean charged;
}
