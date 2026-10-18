/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 废弃岗位角色命令（逻辑删除）。
 *
 * <p>对应 DM-ADM-006 §6.1「废弃岗位（retireRole）」与 SDD §5.1.5。</p>
 */
public class RetirePractitionerRoleCommand extends BatchCommand<RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 废弃岗位角色命令项。
     */
    public static class RetirePractitionerRoleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 废弃原因。 */
        private String reason;
    }
}
