/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 回退未发药单据命令。
 */
@Getter
@Setter
/**
 * RollbackUndispensedDispenseCommand。
 */
public class RollbackUndispensedDispenseCommand {
    /** 待回退发放单标识集合。 */
    /**
     * ids。
     */
    private List<Long> ids;
}
