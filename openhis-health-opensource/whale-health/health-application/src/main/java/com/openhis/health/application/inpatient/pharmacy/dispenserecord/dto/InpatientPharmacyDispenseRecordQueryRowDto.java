/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto;

import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordQueryRowDto;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 住院发退药记录平铺行（querysupport 中性 DTO；扩展住院号/病区/床号）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class InpatientPharmacyDispenseRecordQueryRowDto extends PharmacyDispenseRecordQueryRowDto {

    /** 住院号。 */
    private String inpatientNo;

    /** 病区名称。 */
    private String wardName;

    /** 床号。 */
    private String bedName;
}
