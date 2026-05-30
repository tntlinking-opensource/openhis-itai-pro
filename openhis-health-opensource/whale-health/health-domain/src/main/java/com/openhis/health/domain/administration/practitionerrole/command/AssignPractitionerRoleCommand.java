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

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * 分配岗位角色命令。
 *
 * <p>对应 DM-ADM-006 §6.1「分配岗位（assignRole）」与 SDD §5.1.1。</p>
 */
public class AssignPractitionerRoleCommand extends BatchCommand<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 分配岗位角色命令项。
     */
    public static class AssignPractitionerRoleItemCommand {
        /** 医务人员标识。 */
        private Long practitionerId;
        /** 科室标识。 */
        private Long orgId;
        /** 位置标识。 */
        private Long locationId;
        /** 角色编码。 */
        private Integer roleCode;
        /** 岗位名称。 */
        private String name;
        /** 专业编码。 */
        private String specialtyCode;
        /** 岗位类型编码。 */
        private Integer typeCode;
        /** 就诊类型（医疗服务）标识集合。 */
        private List<Long> serviceIds;
        /** 排班集合。 */
        private List<AvailabilityScheduleCommand> scheduleItems;
    }
}
