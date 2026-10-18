/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 发放校验查询模型。
 */
@Getter
@Setter
public class MedicationDispenseValidationQueryModel {
    /** allowed。 */
    private Boolean allowed;
    /** 原因编码。 */
    private String reasonCode;
    /** snapshot。 */
    private MedicationDispenseDetailQueryModel snapshot;
}
