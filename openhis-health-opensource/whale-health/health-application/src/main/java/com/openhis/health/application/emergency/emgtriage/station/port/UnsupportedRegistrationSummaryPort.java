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
 * 挂号摘要端口占位实现：明确失败，满足 BR-EMG-AS-05「禁止静默成功」直至院内适配落地。
 */
@Component
public class UnsupportedRegistrationSummaryPort implements RegistrationSummaryPort {

    /**
     * 按就诊主键拉取挂号摘要（占位：挂号适配尚未接入时抛出业务异常）。
     *
     * @param encounterId 就诊标识
     * @return 摘要快照（本实现不返回，直接抛异常）
     */
    @Override
    public RegistrationSummarySnapshot fetchSummary(Long encounterId) {
        throw new BusinessException(
                ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT,
                "挂号信息系统适配尚未接入，无法拉取挂号摘要 encounterId=" + encounterId);
    }
}
