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

import com.openhis.health.domain.emergency.emgtriage.entity.EmgTriageAssessment;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 按就诊加载分诊详情（SDD §4.6.2），子表为实体快照列表。
 */
@Getter
@Setter
public class EmgTriageByEncounterQueryModel {

    private Long id;
    private Long encounterId;
    private Long patientId;
    private LocalDateTime triageTime;
    private String triageStatusEnum;
    private String chiefComplaintJson;
    private String symptomJson;
    private Integer triageLevelEnum;
    private Integer suggestedLevelEnum;
    private String levelAdjustReason;
    private String changeReasonCode;
    private String changeRemark;
    private Long lastAmendedBy;
    private LocalDateTime lastAmendedAt;
    private Long priorEmgTriageId;
    private Long supersededById;
    private Long organizationId;
    private Long locationId;
    private String assignedDoctorSnapshot;
    private String diseaseTypesJson;
    private String patientSource;
    private Boolean isGreenChannel;
    private Boolean isFreeRegister;
    private Integer queuePriority;
    private String note;
    private String triageRegistrationModeEnum;
    private Boolean isStaged;
    private Integer stageQueueSeq;
    private String provisionalTriageNo;
    private String specialPopulationTagsJson;
    private Long mciEventId;
    private Boolean blacklistAlertAck;
    private String arrivalModeEnum;
    private Long transferInOrgId;
    private String destinationModeEnum;
    private Long secondaryLocationId;
    private LocalDateTime greenChannelOnsetTime;
    private String epidemicSurveyJson;
    private String specialtyGreenChannelCode;
    private String ambulance120Json;
    private String police110Json;
    private String monitorDeviceBindingJson;
    private LocalDateTime wristbandPrintedAt;
    private String vitalsAutoTierPolicyId;

    private List<EmgTriageAssessment> assessments = new ArrayList<>();
}
