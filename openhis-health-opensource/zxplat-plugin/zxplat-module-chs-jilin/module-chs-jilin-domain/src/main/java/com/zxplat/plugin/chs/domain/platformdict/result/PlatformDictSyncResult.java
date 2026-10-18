/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 1901 同步结果 */
@Data
@Accessors(chain = true)
public class PlatformDictSyncResult {

    private String dictType;
    private String queryAdmdvs;
    private int rowsUpserted;
    private String status;
    private LocalDateTime finishedAt;
}
