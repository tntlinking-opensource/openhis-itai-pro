/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import com.openhis.whale.core.application.util.StringUtils;

/**
 * 医院节点 {@code adm_organization.medins_type}（机构类型）码值约定。
 *
 * <p>用于病案首页「中医信息」显隐等：中医院开启、西医院关闭。科室节点不使用本字段判定。</p>
 */
public final class HospitalMedinsTypeConstants {

    /** 中医院（设置页「机构类型」推荐持久化码）。 */
    public static final String TCM = "TCM";

    /** 西医院（设置页「机构类型」推荐持久化码）。 */
    public static final String WESTERN = "WESTERN";

    private HospitalMedinsTypeConstants() {
    }

    /**
     * 是否判定为中医院。
     *
     * <p>认 {@link #TCM}（忽略大小写）及历史手填「中医院」「中医」；空或其它值视为非中医。</p>
     *
     * @param medinsType 医院节点 {@code medins_type}；可空
     * @return true 表示中医院
     */
    public static boolean isTcmHospital(String medinsType) {
        if (StringUtils.isEmpty(medinsType)) {
            return false;
        }
        String trimmed = medinsType.trim();
        if (TCM.equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("中医院".equals(trimmed)) {
            return true;
        }
        if ("中医".equals(trimmed)) {
            return true;
        }
        return false;
    }
}
