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

package com.openhis.health.domain.emergency.emgtriage.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 分诊列表行（SDD §4.6.1）；患者/科室/地点名称由查询装配。
 */
@Getter
@Setter
public class EmgTriageListItemQueryModel {

    private Long emgTriageId;
    private Long encounterId;
    private Long patientId;
    private String patientName;
    private String patientNamePy;
    private String idCardNoMask;
    private String mobileMask;
    private LocalDateTime triageTime;
    private String triageStatusEnum;
    private Integer triageLevelEnum;
    private Integer suggestedLevelEnum;
    private Long organizationId;
    private String organizationName;
    private Long locationId;
    private String locationName;
    private Long secondaryLocationId;
    private String destinationModeEnum;
    private String chiefComplaintJson;
    private String symptomJson;
    private String diseaseTypesJson;
    private String patientSource;
    private Integer queuePriority;
    private Boolean isGreenChannel;
    private Boolean isStaged;
    private String triageRegistrationModeEnum;
    private Long mciEventId;
}
