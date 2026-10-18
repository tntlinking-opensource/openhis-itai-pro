/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.specimentubemerge.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除设备下全部合管绑定命令。
 */
@Getter
@Setter
public class DeleteDeviceBindingsCommand {
    /** 合管设备主键。 */
    private Long deviceId;
    /** 操作人主键。 */
    private Long operatorId;
}
