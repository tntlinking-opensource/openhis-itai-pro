/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.platformdict.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 平台字典同步日志查询入参 */
@Data
@Accessors(chain = true)
public class GetPlatformDictSyncLogRequest {

    /** 字典类型 */
    private String dictType;
}
