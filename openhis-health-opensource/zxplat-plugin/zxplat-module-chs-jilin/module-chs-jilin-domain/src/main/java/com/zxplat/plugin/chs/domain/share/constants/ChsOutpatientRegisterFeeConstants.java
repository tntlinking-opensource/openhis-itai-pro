/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.constants;

/**
 * 门诊挂号费医保链路常量（2203 默认诊断等）。
 *
 * <p>国标「门诊挂号」字典码为 {@link #MED_TYPE_OUTPATIENT_REGISTRATION}（12）；
 * 长春等院点平台不支持该类别结算时，挂号费实际上报普通门诊 11，
 * 挂号费批次识别改由收费项生成来源（{@code registration}）判定，与 med_type 解耦。</p>
 *
 * <p>院点可在 {@code chs-contract.properties} 用
 * {@code register_fee_default_diag_code/name} 覆盖默认诊断；未配置时用本类默认值。</p>
 */
public final class ChsOutpatientRegisterFeeConstants {

    /**
     * 国标医疗类别：门诊挂号（med_type=12）；平台支持时可用。
     */
    public static final String MED_TYPE_OUTPATIENT_REGISTRATION = "12";

    /**
     * 挂号费尚无临床诊断时，2203 默认诊断编码（吉林省 1307 已承认）。
     */
    public static final String DEFAULT_DIAG_CODE = "Z00.001";

    /**
     * 挂号费默认诊断名称（与 {@link #DEFAULT_DIAG_CODE} 成对；院点可改配置名以对齐本地目录显示名）。
     */
    public static final String DEFAULT_DIAG_NAME = "一般医学检查";

    /**
     * 2203 diseinfo.vali_flag：有效。
     */
    public static final String DIAG_VALI_FLAG_VALID = "1";

    /**
     * 2203 diseinfo.maindiag_flag：主诊。
     */
    public static final String DIAG_MAINDIAG_FLAG_YES = "1";

    private ChsOutpatientRegisterFeeConstants() {
    }
}
