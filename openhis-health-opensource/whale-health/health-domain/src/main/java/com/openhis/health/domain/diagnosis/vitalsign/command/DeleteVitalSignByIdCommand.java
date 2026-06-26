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

package com.openhis.health.domain.diagnosis.vitalsign.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按主表 id 删除生命体征单据命令（DM-DIA-002 v2.0 §6.1.2）。
 */
@Getter
@Setter
public class DeleteVitalSignByIdCommand {

    /** 主表 id。 */
    private Long id;

    /** 操作人 id。 */
    private Long operatorId;
}
