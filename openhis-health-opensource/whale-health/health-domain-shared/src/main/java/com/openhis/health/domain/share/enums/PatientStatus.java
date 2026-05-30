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

package com.openhis.health.domain.share.enums;

/**
 * 患者生命周期状态枚举。
 */
public enum PatientStatus implements EnumInterface {
    DRAFT(0L, "DRAFT", "临时建档", "", 1L),
    ACTIVE(0L, "ACTIVE", "正式活跃", "", 2L),
    INACTIVE(0L, "INACTIVE", "已停用", "", 3L),
    MERGED(0L, "MERGED", "已合并", "", 4L),
    CANCELLED(0L, "CANCELLED", "已废弃", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PatientStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 判断是否为终止状态。
     *
     * @return 终止状态返回 true
     */
    public boolean isTerminal() {
        if (MERGED.equals(this)) {
            return true;
        }
        if (CANCELLED.equals(this)) {
            return true;
        }
        return false;
    }

}
