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
 * 门诊挂号/挂号单就诊状态（读模型 {@code status_enum} 持久化码，与历史整数码字符串一致）。
 */
public enum ClinicRegisterStatus implements EnumInterface {

    /** 已挂号（初始状态）。 */
    REGISTERED(0L, "10", "已挂号", "", 10L),

    /** 已分诊。 */
    TRIAGED(0L, "20", "已分诊", "", 20L),

    /** 就诊中（接诊医生已接诊）。 */
    IN_PROGRESS(0L, "30", "就诊中", "", 30L),

    /** 暂离（就诊中暂时离开）。 */
    ON_HOLD(0L, "40", "暂离", "", 40L),

    /** 已完诊。 */
    COMPLETED(0L, "50", "已完诊", "", 50L),

    /** 已取消/已退号。 */
    CANCELLED(0L, "90", "已取消", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ClinicRegisterStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
