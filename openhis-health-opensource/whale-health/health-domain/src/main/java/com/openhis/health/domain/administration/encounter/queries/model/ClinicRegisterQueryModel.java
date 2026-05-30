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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊挂号列表行（替代 {@code ClinicRegisterReadModel}）。
 */
@Getter
@Setter
public class ClinicRegisterQueryModel {

    private Long encounterId;
    private String encounterBusNo;
    private String patientName;
    private String patientGenderCode;
    private String patientGenderText;
    private String contactPhone;
    private String organizationName;
    private String doctorName;
    private String statusEnum;
    private String statusEnumText;
    private LocalDateTime startTime;
    private Long serviceTypeId;
    private String serviceTypeName;
    /** 打印次数；主表未持久化时为 null（降级）。 */
    private Integer printCount;
}
