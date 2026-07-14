/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废弃机构命令：机构永久下线并记录废止原因，通常不可再用于新开业务。
 */
@Getter
@Setter
public class AbolishOrganizationCommand extends BatchCommand<AbolishOrganizationCommand.AbolishOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条废止项。 */
    @Getter
    @Setter
    public static class AbolishOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
        /** 废止原因。 */
        private String abolishReason;
    }
}
