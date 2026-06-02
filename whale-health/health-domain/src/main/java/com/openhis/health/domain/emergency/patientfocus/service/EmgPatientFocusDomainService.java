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

package com.openhis.health.domain.emergency.patientfocus.service;

import com.openhis.health.domain.emergency.patientfocus.command.SaveEmgPatientFocusCommand;
import com.openhis.health.domain.emergency.patientfocus.entity.EmgPatientFocus;
import com.openhis.health.domain.emergency.patientfocus.repository.EmgPatientFocusRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 患者重点关注写侧。
 */
@Service
@RequiredArgsConstructor
public class EmgPatientFocusDomainService {

    private final EmgPatientFocusRepository emgPatientFocusRepository;

    /**
     * 设为关注或更新说明；{@code revoke=true} 时撤销生效记录。
     *
     * @param command 命令
     * @return 保存后的实体；撤销成功时返回已撤销行
     */
    public EmgPatientFocus savePatientFocus(SaveEmgPatientFocusCommand command) {
        if (command == null || command.getEncounterId() == null || command.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊与患者标识不能为空");
        }
        var existingOpt = emgPatientFocusRepository.findByEncounterIdAndStatusEnumAndIsDeletedFalse(
            command.getEncounterId(), EmgPatientFocus.STATUS_ACTIVE);
        if (command.isRevoke()) {
            if (existingOpt.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "当前就诊未标记重点关注");
            }
            EmgPatientFocus row = existingOpt.get();
            row.setStatusEnum(EmgPatientFocus.STATUS_REVOKED);
            return emgPatientFocusRepository.save(row);
        }
        EmgPatientFocus row = existingOpt.orElseGet(() -> {
            EmgPatientFocus created = new EmgPatientFocus();
            created.setIsDeleted(Boolean.FALSE);
            created.setEncounterId(command.getEncounterId());
            created.setPatientId(command.getPatientId());
            created.setStatusEnum(EmgPatientFocus.STATUS_ACTIVE);
            return created;
        });
        row.setFocusNote(StringUtils.trimToNull(command.getFocusNote()));
        row.setStatusEnum(EmgPatientFocus.STATUS_ACTIVE);
        return emgPatientFocusRepository.save(row);
    }
}
