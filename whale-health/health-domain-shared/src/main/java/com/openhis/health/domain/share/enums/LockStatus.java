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
 * 库存锁定状态枚举（DM-WKF-008 §4.3）。
 */
public enum LockStatus implements EnumInterface {
    /**
     * LOCKED。
     */
    LOCKED(0L, "LOCKED", "锁定中：库存项已锁定，不可修改", "", 0L),
    /**
     * UNLOCKED。
     */
    UNLOCKED(0L, "UNLOCKED", "已解锁：正常业务完成后解锁", "", 1L),
    /**
     * FORCE_UNLOCKED。
     */
    FORCE_UNLOCKED(0L, "FORCE_UNLOCKED", "强制解锁：异常恢复场景下强制解锁", "", 2L),
    /**
     * PENDING_CONFIRM。
     */
    PENDING_CONFIRM(0L, "PENDING_CONFIRM", "待确认：等待确认的锁定状态", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * LockStatus。
     */
    LockStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
