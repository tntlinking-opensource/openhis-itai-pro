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

package com.openhis.health.application.emergency.emgtriage.station.port;

import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 体征落地端口占位：阻断直至 Observation 写入链路接入。
 */
@Component
public class UnsupportedVitalsIngestionPort implements VitalsIngestionPort {

    /**
     * 将体征写入 Observation 事实（占位：管线尚未接入时抛出业务异常）。
     *
     * @param encounterId 就诊标识
     * @param patientId   患者标识
     * @param vitalsInput 体征文本入参
     */
    @Override
    public void persist(Long encounterId, Long patientId, VitalsInput vitalsInput) {
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "体征 Observation 写入链路尚未接入");
    }
}
