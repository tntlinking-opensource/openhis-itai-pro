/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.util.Set;

/**
 * 体检目录与模块参数共享常量（CFG-001/003）。
 */
public final class PeisCatalogConstants {

    private PeisCatalogConstants() {
    }

    /**
     * 体检项目/套餐在 {@code wkf_activity_definition.category_code} 上的专用类目。
     * <p>门诊医生站目录检索应排除本码，避免体检套餐被开立。</p>
     */
    public static final String ACTIVITY_CATEGORY_PEIS = "PEIS";

    /**
     * 体检诊断建议库在 {@code cln_condition_definition.source_enum} 上的专用来源。
     * <p>对应术语 {@code condition_definition_source / peis_dx}；不纳入医生站开诊断候选。</p>
     */
    public static final String CONDITION_SOURCE_PEIS_DX =
        DoctorStationDiagnosisConstants.SOURCE_PEIS_DX;

    /** peis_config 合法 key 前缀。 */
    public static final String CONFIG_KEY_PREFIX = "peis.";

    /** 禁止占用的 framework Setting 前缀（BR-CFG3-04 / BR-PEIS-CFG-02）。 */
    public static final String FORBIDDEN_SETTING_PREFIX = "zxplat.";

    /** 小程序首页公告 HTML（管理端所见即所得编辑）。 */
    public static final String CONFIG_KEY_MINIAPP_HOME_NOTICE = "peis.miniapp.home_notice";

    /** P0 白名单配置键。 */
    public static final Set<String> ALLOWED_CONFIG_KEYS = Set.of(
        "peis.reg.charge_gate",
        "peis.reg.auto_dispatch",
        "peis.report.template_code",
        "peis.bus_no.rule",
        CONFIG_KEY_MINIAPP_HOME_NOTICE
    );

    /** 常见结果启用态。 */
    public static final String COMMON_RESULT_STATUS_ACTIVE = "active";

    /** 常见结果停用态。 */
    public static final String COMMON_RESULT_STATUS_INACTIVE = "inactive";
}
