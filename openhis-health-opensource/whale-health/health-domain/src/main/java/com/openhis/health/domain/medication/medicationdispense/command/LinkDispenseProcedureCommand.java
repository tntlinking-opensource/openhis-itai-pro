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

package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 临时医嘱执行后挂接已有发放单命令（IF-IP-EXE-02 临时药品路径）。
 */
@Getter
@Setter
public class LinkDispenseProcedureCommand {
    /** 发放单主键。 */
    private Long dispenseId;
    /** 执行记录主键。 */
    private Long procedureId;
    /** 计划发药时刻。 */
    private LocalDateTime plannedDispenseTime;
    /** 操作人主键。 */
    private Long operatorId;
}
