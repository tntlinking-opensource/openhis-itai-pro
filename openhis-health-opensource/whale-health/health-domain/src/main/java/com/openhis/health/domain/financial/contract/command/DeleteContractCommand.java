/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.contract.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除费用性质命令。
 */
@Getter
@Setter
/**
 * DeleteContractCommand。
 */
public class DeleteContractCommand {
    /** 主键。 */
    /**
     * id。
     */
    private Long id;
    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
