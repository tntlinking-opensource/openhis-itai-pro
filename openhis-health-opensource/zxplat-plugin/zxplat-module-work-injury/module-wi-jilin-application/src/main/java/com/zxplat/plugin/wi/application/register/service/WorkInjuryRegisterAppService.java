/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.register.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.plugin.wi.domain.register.access.BindQualificationAccess;
import com.zxplat.plugin.wi.domain.register.access.WorkInjuryEncounterContextAccess;
import com.zxplat.plugin.wi.domain.register.command.BindQualificationCommand;
import com.zxplat.plugin.wi.domain.register.result.WorkInjuryEncounterContextResult;
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
