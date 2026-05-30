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

package com.openhis.whale.emr.application.metadata;

import com.openhis.whale.emr.domain.metadata.port.EmrMetadataContextPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 无 HIS 对接时的空实现；health 模块可提供覆盖 Bean。
 */
@Component
@ConditionalOnMissingBean(EmrMetadataContextPort.class)
public class NoOpEmrMetadataContextPort implements EmrMetadataContextPort {

    @Override
    public void contributePatient(Map<String, String> target, String patientId) {
        // no-op
    }

    @Override
    public void contributeEncounter(Map<String, String> target, String encounterId) {
        // no-op
    }

    @Override
    public void contributePractitioner(Map<String, String> target, Long practitionerId) {
        // no-op
    }
}
