/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.platformdict.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地 1901 字典镜像分页查询 */
@Data
@Accessors(chain = true)
public class QueryPlatformDictPageCommand {

    private Integer pageNo;
    private Integer pageSize;

    /** 1901 type；默med_type（基线规范字典表） */
    private String dictType;

    /** 区划码或地名关键字（模糊） */
    private String keyword;
}
