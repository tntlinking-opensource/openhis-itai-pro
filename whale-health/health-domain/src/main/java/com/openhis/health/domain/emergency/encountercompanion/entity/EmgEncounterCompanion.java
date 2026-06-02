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

package com.openhis.health.domain.emergency.encountercompanion.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * 急诊就诊陪护人（一次就诊可多名陪护）。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_encounter_companion")
public class EmgEncounterCompanion extends BaseAuditedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_ID_CARD_READER = "ID_CARD_READER";
    public static final String SOURCE_SCAN = "SCAN";

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "id_card")
    private String idCard;

    @Column(name = "gender_enum")
    private String genderEnum;

    @Column(name = "phone")
    private String phone;

    @Column(name = "relationship_code")
    private String relationshipCode;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "id_card_source_enum", nullable = false)
    private String idCardSourceEnum;

    @Column(name = "wristband_printed_at")
    private LocalDateTime wristbandPrintedAt;

    @Column(name = "remark")
    private String remark;

    public void assertNamePresent() {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护人姓名不能为空");
        }
    }

    public void assertRevocable() {
        if (!STATUS_ACTIVE.equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "仅生效中的陪护可注销");
        }
    }

    public void markRevoked() {
        assertRevocable();
        this.statusEnum = STATUS_REVOKED;
    }

    public void recordWristbandPrinted(LocalDateTime printedAt) {
        this.wristbandPrintedAt = printedAt != null ? printedAt : LocalDateTime.now();
    }
}
