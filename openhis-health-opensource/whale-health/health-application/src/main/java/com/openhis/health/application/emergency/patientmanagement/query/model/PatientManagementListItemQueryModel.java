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

package com.openhis.health.application.emergency.patientmanagement.query.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 患者管理工作站列表行（应用层读侧投影，非领域实体）。
 */
@Getter
@Setter
public class PatientManagementListItemQueryModel {

    private Long encounterId;

    private Long patientId;

    private String patientName;

    private String genderEnum;

    private String genderDisplay;

    private Integer ageYears;

    private Long emgTriageId;

    private Integer triageLevelEnum;

    private Long locationId;

    private String areaName;

    private String bedLabel;

    private LocalDateTime registrationTime;

    private long waitMinutes;

    private boolean focused;

    private String focusNote;

    private boolean greenChannel;

    private String encounterBusNo;

    private String nursingLevelEnum;

    private String nursingLevelDisplay;

    private String specialPopulationTagsJson;

    private boolean blacklisted;
}
