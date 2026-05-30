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

package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 释放耗材发放预减占用命令。
 */
@Getter
@Setter
/**
 * ReleaseDeviceDispenseReserveCommand。
 */
public class ReleaseDeviceDispenseReserveCommand {

    /** 发放标识列表（逗号分隔）或请求维度键。 */
    /**
     * idListOrRequestKey。
     */
    private String idListOrRequestKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
