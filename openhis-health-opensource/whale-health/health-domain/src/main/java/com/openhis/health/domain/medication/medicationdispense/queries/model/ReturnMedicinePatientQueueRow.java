/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊药房退药患者队列行（查主表装配，供 AS-OP-PHA-002 列表映射）。
 */
@Getter
@Setter
public class ReturnMedicinePatientQueueRow {

    private Long encounterId;
    private String patientName;
    private String genderEnum;
    private String genderEnumText;
    private String age;
    private String idCardRaw;
    private String departmentName;
    private String refundEnum;
    private String refundEnumText;
    private String encounterNo;
    private String receptionTime;
}
