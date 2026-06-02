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
 * 患者报道同步端口（专科排队叫号适配）。
 */
public interface EmergencyReportArrivalPort {

    /**
     * 同步报道事实（幂等键由适配层解释）。
     *
     * @param encounterId    就诊标识
     * @param idempotencyKey 幂等键
     * @param operatorId     操作护士
     */
    void report(Long encounterId, String idempotencyKey, Long operatorId);
}
