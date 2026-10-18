/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.servicelocationtemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按科室删除发放规则命令。
 */
@Getter
@Setter
/**
 * DeleteDistributionRulesByOrganizationCommand。
 */
public class DeleteDistributionRulesByOrganizationCommand {
    /** 机构主键。 */
    /**
     * organizationId。
     */
    private Long organizationId;
    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
