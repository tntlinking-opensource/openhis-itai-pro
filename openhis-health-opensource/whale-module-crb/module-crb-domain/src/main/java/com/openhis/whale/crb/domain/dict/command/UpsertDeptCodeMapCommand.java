/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室对照单行 upsert 命令。
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpsertDeptCodeMapCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 主键；空表示按 locationBusNo 查找或新建。 */
    private Long id;

    /** 院内科室业务码。 */
    private String locationBusNo;

    /** 院内科室名称。 */
    private String locationName;

    /** 国家科室码。 */
    private String targetDeptCode;

    /** 国家科室名称。 */
    private String targetDeptName;
}
