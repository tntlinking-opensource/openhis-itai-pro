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
 * 编码系统状态
 * 
 * <p>FHIR标准：CodeSystem.status</p>
 * 
 */
public enum CodeSystemStatus implements EnumInterface {
    DRAFT(0L, "DRAFT", "草稿 编码系统正在开发中，尚未正式使用", "", 1L),
    ACTIVE(0L, "ACTIVE", "活跃 编码系统已正式发布，正在使用中", "", 2L),
    RETIRED(0L, "RETIRED", "已废弃 编码系统已被新版本替代，不再使用", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CodeSystemStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
