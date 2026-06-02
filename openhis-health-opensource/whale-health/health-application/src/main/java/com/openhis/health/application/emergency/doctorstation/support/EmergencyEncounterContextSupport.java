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

package com.openhis.health.application.emergency.doctorstation.support;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 急诊医生站/医嘱集成：就诊门控（仅 {@link Encounter} 主数据，不读门诊信息读模型）。
 */
@Component
@RequiredArgsConstructor
public class EmergencyEncounterContextSupport {

    private final EncounterQueryService encounterQueryService;

    /**
     * 加载急诊就诊并校验患者一致。
     *
     * @param encounterId 就诊主键
     * @param patientId   请求患者主键
     * @return 就诊实体
     */
    public Encounter requireEmergencyEncounter(Long encounterId, Long patientId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先选择患者");
        }
        Encounter encounter = this.encounterQueryService.requireEncounterEntityOfClass(
            encounterId,
            EncounterClass.EMER.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "非急诊就诊，无法在此操作");
        if (!patientId.equals(encounter.getPatientId())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者与就诊不一致");
        }
        if (EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "就诊已结束，无法操作");
        }
        return encounter;
    }

    public void validatePatientEncounter(Long encounterId, Long patientId) {
        requireEmergencyEncounter(encounterId, patientId);
    }
}
