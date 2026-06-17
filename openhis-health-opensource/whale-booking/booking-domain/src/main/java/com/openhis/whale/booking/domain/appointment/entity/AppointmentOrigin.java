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

package com.openhis.whale.booking.domain.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * 预约/候诊来源信息值对象。
 *
 * <p>用途：记录渠道、自助机/小程序会话等审计信息，便于回放与风控。</p>
 */
@Getter
@Setter
@Embeddable
public class AppointmentOrigin {

    /** 渠道编码（如 KIOSK / MINIAPP / COUNTER / DOCTOR_STATION / NURSE_STATION / OTHER）。 */
    @Column(name = "origin_channel")
    private String originChannel;

    /** 渠道会话标识。 */
    @Column(name = "origin_channel_session_id")
    private String originChannelSessionId;

    /** 来源设备标识（自助机 Device 等）。 */
    @Column(name = "origin_source_device_id")
    private Long originSourceDeviceId;

    /** 代操作操作者标识（护士站等）。 */
    @Column(name = "origin_operator_id")
    private Long originOperatorId;

    /** 客户端版本（小程序/App 版本等）。 */
    @Column(name = "origin_client_app_version")
    private String originClientAppVersion;
}

