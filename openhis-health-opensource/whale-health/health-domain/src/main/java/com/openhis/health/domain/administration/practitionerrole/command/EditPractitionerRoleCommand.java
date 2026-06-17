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
 * 编辑岗位角色命令。
 *
 * <p>对应 DM-ADM-006 §6.1「编辑岗位（editRole）」与 SDD §5.1.2。</p>
 */
public class EditPractitionerRoleCommand extends BatchCommand<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 编辑岗位角色命令项。
     */
    public static class EditPractitionerRoleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 岗位名称。 */
        private String name;
        /** 科室标识。 */
        private Long orgId;
        /**
         * 位置标识。
         * 普通岗位为单个 ID；库房/药房岗位可为逗号分隔的多个 ID，由应用层解析后走 {@code replaceRolesByType}。
         */
        private String locationId;
        /** 专业编码。 */
        private String specialtyCode;
        /** 岗位类型编码。 */
        private Integer typeCode;
        /** 可接诊号源类型（HealthcareService）主键集合；{@code null} 表示不调整。 */
        private List<Long> serviceIds;
    }
}
