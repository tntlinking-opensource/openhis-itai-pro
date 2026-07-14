/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 调整机构上级命令：变更机构在组织树中的父节点。
 */
@Getter
@Setter
public class RelocateOrganizationCommand extends BatchCommand<RelocateOrganizationCommand.RelocateOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条调整项。 */
    @Getter
    @Setter
    public static class RelocateOrganizationItemCommand {
        /** 被调整的机构主键。 */
        private Long organizationId;
        /** 新父机构主键。 */
        private Long newParentId;
    }
}
