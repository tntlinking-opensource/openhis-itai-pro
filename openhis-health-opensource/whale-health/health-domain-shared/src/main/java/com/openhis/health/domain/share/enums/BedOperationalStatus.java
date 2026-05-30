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
 * 床位运营状态（床位一览读模型 {@code operational_status} 持久化码）。
 */
public enum BedOperationalStatus implements EnumInterface {

    /** 空床（未占用）。 */
    UNOCCUPIED(0L, "UNOCCUPIED", "空床", "", 1L),

    /** 已占用。 */
    OCCUPIED(0L, "OCCUPIED", "已占用", "", 2L),

    /** 预留（已预留给特定患者）。 */
    RESERVED(0L, "RESERVED", "预留", "", 3L),

    /** 暂停使用。 */
    SUSPENDED(0L, "SUSPENDED", "暂停使用", "", 4L),

    /** 已关闭。 */
    CLOSED(0L, "CLOSED", "已关闭", "", 5L),

    /** 已废止。 */
    ABOLISHED(0L, "ABOLISHED", "已废止", "", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    BedOperationalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
}
