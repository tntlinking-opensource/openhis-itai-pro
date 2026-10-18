/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 批号匹配（待发门控）命令。
 */
@Getter
@Setter
/**
 * MatchDispenseLotNumberCommand。
 */
public class MatchDispenseLotNumberCommand {

    /** 标识列表或请求key。 */
    /**
     * idListOrRequestKey。
     */
    private String idListOrRequestKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
