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
 * 号源时段状态（FHIR Slot.status）。
 */
public enum SlotStatus implements EnumInterface {
    /**
     * FREE。
     */
    FREE(0L, "free", "空闲，可接受新预约", "", 1L),
    /**
     * BUSY。
     */
    BUSY(0L, "busy", "已占用，至少存在一条预约占用事实", "", 2L),
    /**
     * BUSY_UNAVAILABLE。
     */
    BUSY_UNAVAILABLE(0L, "BUSY_UNAVAILABLE", "封锁，不可用（停诊、设备检修等非预约原因）", "", 3L),
    /**
     * BUSY_TENTATIVE。
     */
    BUSY_TENTATIVE(0L, "BUSY_TENTATIVE", "暂定占用，待确认", "", 4L),
    /**
     * ENTERED_IN_ERROR。
     */
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "录入差错终态，仅用于审计纠错，不参与正常预约", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * SlotStatus。
     */
    SlotStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
