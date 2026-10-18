/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 门禁判定结果。
 */
@Data
public class ComplianceGateResult {

    /** true 允许继续开单/上传；false 硬阻断。 */
    private boolean allowed;
    /** 门禁码（阻断或放行标注如 C-PRICE-UNKNOWN）。 */
    private String code;
    /** 对人可读说明。 */
    private String message;
    /** 触发行的国家目录编码。 */
    private String hilistCode;
    /** 触发行的项目码；可空。 */
    private String projectCode;

    /** 收费项目展示名；可空。 */
    private String itemDisplayName;

    /** 院内产品定义主键；可空。 */
    private Long productDefinitionId;

    /** 产品表名；可空。 */
    private String productTableName;

    public static ComplianceGateResult allow() {
        ComplianceGateResult r = new ComplianceGateResult();
        r.setAllowed(true);
        return r;
    }

    public static ComplianceGateResult allowUnknown(String code, String message) {
        ComplianceGateResult r = allow();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static ComplianceGateResult block(String code, String message, String hilist, String project) {
        ComplianceGateResult r = new ComplianceGateResult();
        r.setAllowed(false);
        r.setCode(code);
        r.setMessage(message);
        r.setHilistCode(hilist);
        r.setProjectCode(project);
        return r;
    }

    public static ComplianceGateResult block(
        String code,
        String message,
        String hilist,
        String project,
        String itemDisplayName,
        Long productDefinitionId,
        String productTableName) {
        ComplianceGateResult r = block(code, message, hilist, project);
        r.setItemDisplayName(itemDisplayName);
        r.setProductDefinitionId(productDefinitionId);
        r.setProductTableName(productTableName);
        return r;
    }
}
