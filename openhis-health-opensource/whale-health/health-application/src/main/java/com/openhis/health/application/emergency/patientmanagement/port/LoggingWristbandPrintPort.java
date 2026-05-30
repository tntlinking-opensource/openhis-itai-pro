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

package com.openhis.health.application.emergency.patientmanagement.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 腕带打印占位：记录日志并返回成功，待对接全院打印中间件后替换。
 */
@Slf4j
@Component
public class LoggingWristbandPrintPort implements WristbandPrintPort {

    @Override
    public boolean print(WristbandPrintCommand command) {
        String subjectType = null;
        Long encounterId = null;
        Long companionId = null;
        if (command != null) {
            subjectType = command.getSubjectType();
            encounterId = command.getEncounterId();
            companionId = command.getCompanionId();
        }
        log.info("Wristband print placeholder: subject={}, encounterId={}, companionId={}",
            subjectType, encounterId, companionId);
        return true;
    }
}
