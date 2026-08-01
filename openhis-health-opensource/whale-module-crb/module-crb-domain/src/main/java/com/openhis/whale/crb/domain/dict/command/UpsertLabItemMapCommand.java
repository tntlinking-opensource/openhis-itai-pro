/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 检验项目对照单行 upsert 命令。
 */
@Getter
@Setter
@Accessors(chain = true)
public class UpsertLabItemMapCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 主键；空表示按 itemBusNo 查找或新建。 */
    private Long id;

    /** 院内项目业务码。 */
    private String itemBusNo;

    /** 院内项目名称。 */
    private String itemName;

    /** 国家/标化项目码。 */
    private String nationalItemCode;

    /** 结果形态 code：QUAL / QUANT / 空。 */
    private String resultShape;

    /** 附件二范围 code。 */
    private String annex2Scope;

    /** 是否启用推送；默认 true，范围外强制 false。 */
    private Boolean pushEnabled;
}
