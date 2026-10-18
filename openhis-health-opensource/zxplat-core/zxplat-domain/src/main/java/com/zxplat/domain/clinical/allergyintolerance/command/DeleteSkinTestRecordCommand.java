/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除皮试记录命令。
 */
@Getter
@Setter
public class DeleteSkinTestRecordCommand {
    /** 记录主键。 */
    private Long recordId;
    /** 操作人主键。 */
    private Long operatorId;
    /** 原因。 */
    private String reason;
}
