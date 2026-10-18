/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 未发放逻辑删除命令。
 */
@Getter
@Setter
/**
 * RemoveUndispensedDeviceDispenseCommand。
 */
public class RemoveUndispensedDeviceDispenseCommand {

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
