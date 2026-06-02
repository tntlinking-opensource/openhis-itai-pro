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

package com.openhis.health.domain.emergency.encountercompanion.service;

import com.openhis.health.domain.emergency.encountercompanion.command.RecordCompanionWristbandPrintCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.RegisterEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.RevokeEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.command.UpdateEmgEncounterCompanionCommand;
import com.openhis.health.domain.emergency.encountercompanion.entity.EmgEncounterCompanion;
import com.openhis.health.domain.emergency.encountercompanion.repository.EmgEncounterCompanionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmgEncounterCompanionDomainService {

    private final EmgEncounterCompanionRepository repository;

    public EmgEncounterCompanion registerCompanion(RegisterEmgEncounterCompanionCommand cmd) {
        if (cmd == null || cmd.getEncounterId() == null || cmd.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊与患者标识不能为空");
        }
        EmgEncounterCompanion row = new EmgEncounterCompanion();
        row.setIsDeleted(Boolean.FALSE);
        row.setEncounterId(cmd.getEncounterId());
        row.setPatientId(cmd.getPatientId());
        applyProfile(row, cmd.getName(), cmd.getIdCard(), cmd.getGenderEnum(), cmd.getPhone(),
            cmd.getRelationshipCode(), cmd.getIdCardSourceEnum(), cmd.getRemark());
        row.setStatusEnum(EmgEncounterCompanion.STATUS_ACTIVE);
        row.assertNamePresent();
        return repository.save(row);
    }

    public EmgEncounterCompanion updateCompanion(UpdateEmgEncounterCompanionCommand cmd) {
        if (cmd == null || cmd.getCompanionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护标识不能为空");
        }
        EmgEncounterCompanion row = loadActive(cmd.getCompanionId());
        applyProfile(row, cmd.getName(), cmd.getIdCard(), cmd.getGenderEnum(), cmd.getPhone(),
            cmd.getRelationshipCode(), cmd.getIdCardSourceEnum(), cmd.getRemark());
        row.assertNamePresent();
        return repository.save(row);
    }

    public EmgEncounterCompanion revokeCompanion(RevokeEmgEncounterCompanionCommand cmd) {
        if (cmd == null || cmd.getCompanionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护标识不能为空");
        }
        EmgEncounterCompanion row = loadActive(cmd.getCompanionId());
        row.markRevoked();
        return repository.save(row);
    }

    public EmgEncounterCompanion recordCompanionWristbandPrint(RecordCompanionWristbandPrintCommand cmd) {
        if (cmd == null || cmd.getCompanionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护标识不能为空");
        }
        EmgEncounterCompanion row = loadActive(cmd.getCompanionId());
        row.recordWristbandPrinted(cmd.getWristbandPrintedAt());
        return repository.save(row);
    }

    private EmgEncounterCompanion loadActive(Long companionId) {
        EmgEncounterCompanion row = repository.findByIdAndIsDeletedFalse(companionId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护记录不存在"));
        if (!EmgEncounterCompanion.STATUS_ACTIVE.equals(row.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "陪护记录已注销");
        }
        return row;
    }

    private static void applyProfile(
        EmgEncounterCompanion row,
        String name,
        String idCard,
        String genderEnum,
        String phone,
        String relationshipCode,
        String idCardSourceEnum,
        String remark) {
        if (name != null) {
            row.setName(StringUtils.trimToNull(name));
        }
        if (idCard != null) {
            row.setIdCard(StringUtils.trimToNull(idCard));
        }
        if (genderEnum != null) {
            row.setGenderEnum(StringUtils.trimToNull(genderEnum));
        }
        if (phone != null) {
            row.setPhone(StringUtils.trimToNull(phone));
        }
        if (relationshipCode != null) {
            row.setRelationshipCode(StringUtils.trimToNull(relationshipCode));
        }
        if (idCardSourceEnum != null) {
            row.setIdCardSourceEnum(StringUtils.trimToNull(idCardSourceEnum));
        }
        if (remark != null) {
            row.setRemark(StringUtils.trimToNull(remark));
        }
        if (StringUtils.isBlank(row.getIdCardSourceEnum())) {
            row.setIdCardSourceEnum(EmgEncounterCompanion.SOURCE_MANUAL);
        }
    }
}
