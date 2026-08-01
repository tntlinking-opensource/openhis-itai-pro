/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药品对照单行 upsert 命令。
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpsertDrugCodeMapCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 主键；空表示按 medBusNo 查找或新建。 */
    private Long id;

    /** 院内药品业务码。 */
    private String medBusNo;

    /** 院内药品名称。 */
    private String medName;

    /** 国家药品码。 */
    private String nationalDrugCode;

    /** 国家药品名称。 */
    private String nationalDrugName;
}
