/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者血型值对象。
 */
@Getter
@Setter
@Embeddable
public class BloodType {

    /**
     * ABO 血型编码。
     */
    @Column(name = "blood_type_abo")
    private Integer abo;

    /**
     * Rh 血型编码。
     */
    @Column(name = "blood_type_rh")
    private Integer rh;
}
