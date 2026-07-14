/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 博思电子票据契约配置（来自 einvoice-boss-contract.properties）。
 */
@Getter
@Setter
public class EinvoiceBossContractConfig {

    /** 配置段键（通常为 fixmedins_code）。 */
    private String orgCode;

    private String contractName;
    private String fixmedinsCode;
    private String fixmedinsName;
    private String appId;
    private String key;
    private String url;
    /** 医疗机构类型（如二级甲等）。 */
    private String medicalInstitution;
    /** 医院等级码（01~10，映射 medicalInstitution）。 */
    private String hospitalLv;
    private boolean enabled = true;
}
