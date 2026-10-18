/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 本地 1901 字典镜像行DTO */
@Data
@Accessors(chain = true)
public class PlatformDictItemResult {

    private Long id;
    private String dictType;
    private String dictValue;
    private String dictLabel;
    private String parentValue;
    private Integer sortOrder;
    private String valiFlag;
    private String queryAdmdvs;
    private LocalDateTime syncedAt;
}
