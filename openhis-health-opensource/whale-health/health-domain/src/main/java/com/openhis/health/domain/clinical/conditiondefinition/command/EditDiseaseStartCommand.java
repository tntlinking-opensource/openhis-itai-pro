/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量启用诊断目录命令。
 */
@Getter
@Setter
/**
 * EditDiseaseStartCommand。
 */
public class EditDiseaseStartCommand {
    /** 标识集合。 */
    /**
     * ids。
     */
    private List<Long> ids;
    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
