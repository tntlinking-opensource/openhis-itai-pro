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
 * 更新岗位就诊类型集合命令。
 *
 * <p>对应 DM-ADM-006 §6.1「更新就诊类型（updateServices）」与 SDD §5.1.6。</p>
 */
public class UpdatePractitionerRoleServicesCommand extends BatchCommand<UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 更新岗位就诊类型集合命令项。
     */
    public static class UpdatePractitionerRoleServicesItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 目标就诊类型集合。 */
        private List<Long> serviceIds;
    }
}
