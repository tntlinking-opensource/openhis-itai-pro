/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.register.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.wi.domain.register.access.BindQualificationAccess;
import com.openhis.wi.domain.register.access.WorkInjuryEncounterContextAccess;
import com.openhis.wi.domain.register.command.BindQualificationCommand;
import com.openhis.wi.domain.register.result.WorkInjuryEncounterContextResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 工伤挂号/9102（POST /api/app/workInjuryRegister/*）。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryRegisterAppService {

    private final BindQualificationAccess bindQualificationAccess;
    private final WorkInjuryEncounterContextAccess workInjuryEncounterContextAccess;

    public WorkInjuryEncounterContextResult loadEncounterContext(Long encounterId) {
        return workInjuryEncounterContextAccess.resolveContext(encounterId);
    }

    public void bindQualification(BindQualificationCommand command) {
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException("REG-VAL-001", "encounterId 不能为空");
        }
        if (StringUtils.isBlank(command.getQualificationId())) {
            throw new BusinessException("REG-VAL-001", "qualificationId 不能为空");
        }
        bindQualificationAccess.bind(
                command.setQualificationId(command.getQualificationId().trim()));
    }
}
