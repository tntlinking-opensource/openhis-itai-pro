/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-STK-02 皮试批号候选。
 */
@Getter
@Setter
public class NurseStationSkinTestLotOptionDto {

    /** 药品定义主键。 */
    private Long medicationId;

    /** 库房点位主键。 */
    private Long locationId;

    /** 批号。 */
    private String lotNumber;

    /** 供应商展示名。 */
    private String supplierName;
}
