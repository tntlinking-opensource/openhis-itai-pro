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

package com.openhis.health.domain.share.enums;
/**
 * 号源日程参与者类型（DM-WKF-009 §4.1）。
 */
public enum ScheduleActorKind implements EnumInterface {
    /**
     * PRACTITIONER。
     */
    PRACTITIONER(0L, "PRACTITIONER", "Practitioner", "", 10L),
    /**
     * PRACTITIONER_ROLE。
     */
    PRACTITIONER_ROLE(0L, "PRACTITIONER_ROLE", "PractitionerRole", "", 11L),
    /**
     * LOCATION。
     */
    LOCATION(0L, "LOCATION", "Location", "", 20L),
    /**
     * HEALTHCARE_SERVICE。
     */
    HEALTHCARE_SERVICE(0L, "HEALTHCARE_SERVICE", "HealthcareService", "", 30L),
    /**
     * DEVICE。
     */
    DEVICE(0L, "DEVICE", "Device", "", 40L),
    /**
     * PATIENT。
     */
    PATIENT(0L, "PATIENT", "Patient", "", 50L),
    /**
     * RELATED_PERSON。
     */
    RELATED_PERSON(0L, "RELATED_PERSON", "RelatedPerson", "", 60L),
    /**
     * CARE_TEAM。
     */
    CARE_TEAM(0L, "CARE_TEAM", "CareTeam", "", 70L),
    /**
     * ORGANIZATION。
     */
    ORGANIZATION(0L, "ORGANIZATION", "Organization", "", 80L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * ScheduleActorKind。
     */
    ScheduleActorKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

}
