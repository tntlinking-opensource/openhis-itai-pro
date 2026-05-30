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

package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材发放列表读模型。
 */
@Getter
@Setter
public class DeviceDispenseListModel {

    /** 主键。 */
    private Long id;

    /** 就诊主键。 */
    private Long encounterId;

    /** 状态枚举编码。 */
    private String statusEnum;

    /** 业务号。 */
    private String busNo;

    /** 设备申请主键。 */
    private Long deviceReqId;
}
