/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 释放发放预减占用命令。
 */
@Getter
@Setter
/**
 * ReleaseDispenseReserveCommand。
 */
public class ReleaseDispenseReserveCommand {
    /** 药品申请标识。 */
    /**
     * medReqId。
     */
    private Long medReqId;
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
