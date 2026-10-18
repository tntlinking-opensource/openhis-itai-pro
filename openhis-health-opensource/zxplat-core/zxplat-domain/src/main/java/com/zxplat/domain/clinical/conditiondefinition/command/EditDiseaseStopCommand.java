/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量停用诊断目录命令。
 */
@Getter
@Setter
public class EditDiseaseStopCommand {
    /** 标识集合。 */
    private List<Long> ids;
    /** 操作人主键。 */
    private Long operatorId;
    /** 原因。 */
    private String reason;
}
