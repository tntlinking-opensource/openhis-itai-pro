/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用机构命令：机构业务暂停，需记录停用原因（可恢复，与废止区分）。
 */
@Getter
@Setter
public class RetireOrganizationCommand extends BatchCommand<RetireOrganizationCommand.RetireOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条停用项。 */
    @Getter
    @Setter
    public static class RetireOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 停用原因。 */
        private String retireReason;
    }
}
