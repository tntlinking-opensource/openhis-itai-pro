/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.organization.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布机构命令：将机构从草稿/待发布状态发布为可被业务引用。
 */
@Getter
@Setter
public class PublishOrganizationCommand extends BatchCommand<PublishOrganizationCommand.PublishOrganizationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条发布项。 */
    @Getter
    @Setter
    public static class PublishOrganizationItemCommand {
        /** 机构主键。 */
        private Long organizationId;
    }
}
