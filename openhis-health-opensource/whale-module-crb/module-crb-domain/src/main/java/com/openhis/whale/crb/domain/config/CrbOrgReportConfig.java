/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.config;

import com.openhis.whale.crb.domain.support.CrbTenantRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 院端前置软件连接与机构上报配置（crb_org_report_config）。
 */
@Entity
@Table(name = "crb_org_report_config")
@Getter
@Setter
public class CrbOrgReportConfig extends CrbTenantRowEntity {

    /** 前置 baseUrl，如 http://ip:port。 */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

    /** SSO license。 */
    @Column(name = "license", length = 128)
    private String license;

    /** 国家分配机构代码。 */
    @Column(name = "org_code", nullable = false, length = 64)
    private String orgCode;

    /** 机构名称。 */
    @Column(name = "org_name", nullable = false, length = 256)
    private String orgName;

    /** 是否启用本租户推送。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
