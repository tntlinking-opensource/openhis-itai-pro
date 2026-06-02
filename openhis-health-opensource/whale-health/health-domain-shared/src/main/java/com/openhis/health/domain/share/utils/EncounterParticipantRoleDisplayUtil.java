/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
