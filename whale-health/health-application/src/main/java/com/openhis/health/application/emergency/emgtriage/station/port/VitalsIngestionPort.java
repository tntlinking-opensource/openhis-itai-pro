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

/**
 * 体征落地端口（Observation 权威写入侧）；默认实现可阻断直至院内体征管线接通。
 */
public interface VitalsIngestionPort {

    /**
     * 将体征写入 Observation 事实（或等价持久化通路）。
     *
     * @param encounterId 就诊标识
     * @param patientId     患者标识
     * @param vitalsInput   体征文本入参
     */
    void persist(Long encounterId, Long patientId, VitalsInput vitalsInput);
}
