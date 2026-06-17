/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 皮试批号候选。
 */
@Getter
@Setter
public class OutpatientSkinTestLotOptionDto {

    /** 药品定义主键。 */
    private Long medicationId;

    /** 库房库位主键。 */
    private Long locationId;

    /** 批号。 */
    private String lotNumber;
}
