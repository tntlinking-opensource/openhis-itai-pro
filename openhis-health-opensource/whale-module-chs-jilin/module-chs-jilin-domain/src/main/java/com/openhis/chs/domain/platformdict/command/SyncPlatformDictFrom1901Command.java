/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 1901 手动同步请求 */
@Data
@Accessors(chain = true)
public class SyncPlatformDictFrom1901Command {

    private String contractBusNo;
    private Long contractId;

    /** 1901 type；默med_type（基线规范字典表） */
    private String dictType;

    /** 1901 admdvs；缺省取契约 adm_vs 100000 */
    private String queryAdmdvs;

    /** 1901 parentValue；根级传空串 */
    private String parentValue;

    /** 是否 BFS 拉取子级 parentValue */
    private Boolean recursive;

    private Integer pageSize;
}
