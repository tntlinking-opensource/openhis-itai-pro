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

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 患者报道端口占位实现。
 */
@Component
public class UnsupportedEmergencyReportArrivalPort implements EmergencyReportArrivalPort {

    /**
     * 同步报道事实（占位：适配尚未接入时抛出业务异常）。
     *
     * @param encounterId    就诊标识
     * @param idempotencyKey 幂等键
     * @param operatorId     操作护士
     */
    @Override
    public void report(Long encounterId, String idempotencyKey, Long operatorId) {
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "报道叫号适配尚未接入");
    }
}
