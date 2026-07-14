/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 删除未发药记录命令。
 */
@Getter
@Setter
/**
 * RemoveUndispensedDispenseCommand。
 */
public class RemoveUndispensedDispenseCommand {
    /** 待删除发放单标识集合。 */
    /**
     * ids。
     */
    private List<Long> ids;
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
