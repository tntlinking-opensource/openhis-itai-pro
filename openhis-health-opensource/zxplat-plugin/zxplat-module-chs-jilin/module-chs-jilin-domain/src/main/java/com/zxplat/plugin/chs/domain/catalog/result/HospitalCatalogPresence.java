/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 院内品项相对 1317 镜像的存在性解析结果。
 */
@Data
@Accessors(chain = true)
public class HospitalCatalogPresence {

    /** 产品表名 */
    private String productTable;
    /** 产品主键 */
    private Long productId;
    /** 发布状态 status_enum */
    private String statusEnum;
    /** 院内医保编码 yb_no */
    private String ybNo;

    /**
     * 是否院内启用。
     *
     * @return true=ACTIVE
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(statusEnum);
    }
}
