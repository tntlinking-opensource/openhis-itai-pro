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
 * 急诊分诊状态枚举。
 */
public enum TriageStatus implements EnumInterface {
    PENDING(0L, "PENDING", "待分诊", "", 1L),
    TRIAGED(0L, "TRIAGED", "已分诊", "", 2L),
    IN_PROGRESS(0L, "IN_PROGRESS", "处理中", "", 3L),
    COMPLETED(0L, "COMPLETED", "已完成", "", 4L),
    CANCELLED(0L, "CANCELLED", "已取消", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    TriageStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
     * 处理逻辑。
     * @return 返回结果
     */
    public boolean isTerminal() {
        return COMPLETED.equals(this) || CANCELLED.equals(this);
    }

}
