/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.practitionerrole.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 停用岗位角色命令。
 *
 * <p>对应 DM-ADM-006 §6.1「停用岗位（deactivateRole）」与 SDD §5.1.4。</p>
 */
public class DeactivatePractitionerRoleCommand extends BatchCommand<DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand> {
    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    /**
     * 停用岗位角色命令项。
     */
    public static class DeactivatePractitionerRoleItemCommand {
        /** 岗位角色标识。 */
        private Long assignmentId;
        /** 停用原因。 */
        private String reason;
    }
}
