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
 * 医疗服务状态枚举。
 *
 * <p>对应 {@code adm_healthcare_service.status_enum} 的存量码值体系：
 * DRAFT=0、ACTIVE=1、RETIRED=2、DELETED=3。</p>
 */
public enum HealthcareServiceStatus implements EnumInterface {
    DRAFT(0L, "DRAFT", "草稿（信息录入中，尚未对外发布）", "", 0L),
    ACTIVE(0L, "ACTIVE", "已发布（生效中，可被挂号/预约流程使用）", "", 1L),
    RETIRED(0L, "RETIRED", "已停用（暂时下线，可重新发布）", "", 2L),
    DELETED(0L, "DELETED", "已废弃（逻辑删除，不可恢复）", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    HealthcareServiceStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
