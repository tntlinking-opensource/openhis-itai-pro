/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.command;

import com.zxplat.domain.share.command.BatchCommand;
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
