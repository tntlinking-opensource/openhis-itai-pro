/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.enums.EncounterParticipantRole;

/**
 * 就诊参与者 {@code role_code} → 展示名（DM-ADM-002 §3.2.1）。
 *
 * <p>已知 code 经 {@link EncounterParticipantRole} + {@link EnumUtil#fromCode} 解析；
 * 未登记 code 回退为 trimmed 原串，便于兼容历史数据。</p>
 */
public final class EncounterParticipantRoleDisplayUtil {

    private EncounterParticipantRoleDisplayUtil() {
    }

    /**
     * 将参与者角色 code 解析为中文展示名。
     *
     * @param roleCode 子表 {@code role_code}
     * @return 展示名；未知 code 时返回 trimmed 原值；空入参返回 {@code null}
     */
    public static String resolveDisplayText(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String code = roleCode.trim();
        EncounterParticipantRole role = EnumUtil.fromCode(EncounterParticipantRole.class, code);
        if (role != null) {
            return role.getDisplay();
        }
        return code;
    }
}
