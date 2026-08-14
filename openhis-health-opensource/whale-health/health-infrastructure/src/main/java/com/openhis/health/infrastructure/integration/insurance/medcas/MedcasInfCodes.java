/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas;

/**
 * 病案医保接口编码（health 核心常量，与 CHS Module 解耦）。
 *
 * <p>镜像 {@code ChsInfNo} 中病案相关交易号：4401 / 4101A / 4102 / 4103。</p>
 */
public final class MedcasInfCodes {

    /** 【4401】住院病案首页信息上传。 */
    public static final String MEDCAS_HOMEPAGE = "4401";

    /** 【4101A】医疗保障基金结算清单信息上传（新）。 */
    public static final String SETTLE_LIST_UPLOAD = "4101A";

    /** 【4102】医疗保障基金结算清单信息状态修改。 */
    public static final String SETTLE_LIST_STATUS = "4102";

    /** 【4103】医疗保障基金结算清单信息查询。 */
    public static final String SETTLE_LIST_QUERY = "4103";

    private MedcasInfCodes() {
    }
}
