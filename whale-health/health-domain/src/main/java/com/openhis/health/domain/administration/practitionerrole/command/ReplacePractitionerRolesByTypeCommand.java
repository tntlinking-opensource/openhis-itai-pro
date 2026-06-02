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

package com.openhis.health.domain.administration.practitionerrole.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * 按人员+角色类型全量替换岗位集合命令。
 *
 * <p>对应 DM-ADM-006 §6.1「全量替换岗位（replaceRolesByType）」与 SDD §5.1.8。</p>
 */
public class ReplacePractitionerRolesByTypeCommand {
    /** 医务人员标识。 */
    private Long practitionerId;
    /** 目标角色编码。 */
    private Integer roleCode;
    /** 替换后的岗位集合。 */
    private List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items;
    /** 操作人标识。 */
    private Long operatorId;
}
