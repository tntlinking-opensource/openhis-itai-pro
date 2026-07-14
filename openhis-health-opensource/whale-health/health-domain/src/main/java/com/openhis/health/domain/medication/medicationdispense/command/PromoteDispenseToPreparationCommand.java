/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 草稿推进待配药命令。
 */
@Getter
@Setter
/**
 * PromoteDispenseToPreparationCommand。
 */
public class PromoteDispenseToPreparationCommand {
    /** 待推进医嘱标识集合。 */
    /**
     * medReqIds。
     */
    private List<Long> medReqIds;
    /** 操作人标识。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
