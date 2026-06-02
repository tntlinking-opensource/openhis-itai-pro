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

package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建退药请求命令。
 */
@Getter
@Setter
/**
 * CreateRefundMedicationOrderCommand。
 */
public class CreateRefundMedicationOrderCommand {
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
    /** 原始药品申请标识。 */
    /**
     * originRequestId。
     */
    private Long originRequestId;
}
