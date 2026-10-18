/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 医嘱签退校验查询模型。
 */
@Getter
@Setter
public class MedicationRequestSignOffCheckQueryModel {
    /** allowed。 */
    private Boolean allowed;
    /** 原因编码。 */
    private String reasonCode;
    /** charged。 */
    private Boolean charged;
}
