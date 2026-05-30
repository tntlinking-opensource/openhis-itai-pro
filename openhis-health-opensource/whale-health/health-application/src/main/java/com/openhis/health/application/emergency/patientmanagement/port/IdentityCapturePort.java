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

/**
 * 多源身份采集适配端口（读卡器/扫码/手工）。
 */
public interface IdentityCapturePort {

    /**
     * 将设备回传原文标准化为患者/陪护可录入字段。
     *
     * @param channelCode 渠道编码
     * @param rawPayload  原文或客户端已解析 JSON
     * @return 标准化结果
     */
    IdentityCaptureResult normalize(String channelCode, String rawPayload);
}
