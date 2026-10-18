/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.organization.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 重新启用机构命令：从停用状态恢复机构对外服务。
 */
@Getter
@Setter
public class ReactivateOrganizationCommand extends BatchCommand<ReactivateOrganizationCommand.ReactivateOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条启用项。 */
    @Getter
    @Setter
    public static class ReactivateOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
    }
}
