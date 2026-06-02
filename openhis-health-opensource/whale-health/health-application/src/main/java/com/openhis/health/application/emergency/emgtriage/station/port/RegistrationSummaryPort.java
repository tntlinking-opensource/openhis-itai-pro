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

/**
 * 挂号摘要适配端口（院内挂号系统对接）；应用层仅依赖该契约，具体适配由基础设施实现。
 */
public interface RegistrationSummaryPort {

    /**
     * 按就诊主键拉取挂号摘要。
     *
     * @param encounterId 就诊标识
     * @return 摘要快照；外部不可用时应抛出业务异常而非静默空壳成功
     */
    RegistrationSummarySnapshot fetchSummary(Long encounterId);
}
