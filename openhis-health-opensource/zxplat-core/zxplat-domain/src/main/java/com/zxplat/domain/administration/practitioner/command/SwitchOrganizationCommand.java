/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 切换医疗卫生人员所属机构命令（批量）。
 * <p>
 * 将人员的主执业或人事归属从原机构调整至目标机构（如调动）。
 */
@Getter
@Setter
public class SwitchOrganizationCommand extends BatchCommand<SwitchOrganizationCommand.SwitchOrganizationItemCommand> {
    /** 执行本次批量机构切换的操作人标识。 */
    private Long operatorId;

    /** 单条切换项：人员与目标机构。 */
    @Getter
    @Setter
    public static class SwitchOrganizationItemCommand {
        /** 待调整机构的医疗卫生人员主键。 */
        private Long practitionerId;
        /** 目标组织机构主键。 */
        private Long targetOrgId;
    }
}
