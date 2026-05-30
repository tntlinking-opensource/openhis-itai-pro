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
 * 职业类型枚举。
 */
public enum OccupationType implements EnumInterface {
    /**
     * STATE_CIVIL_SERVANT。
     */
    STATE_CIVIL_SERVANT(0L, "STATE_CIVIL_SERVANT", "国家公务员", "", 11L),
    /**
     * PROFESSIONAL。
     */
    PROFESSIONAL(0L, "PROFESSIONAL", "专业技术人员", "", 13L),
    /**
     * CLERK。
     */
    CLERK(0L, "CLERK", "职员", "", 17L),
    /**
     * BUSINESS_MANAGER。
     */
    BUSINESS_MANAGER(0L, "BUSINESS_MANAGER", "企业管理人员", "", 21L),
    /**
     * WORKER。
     */
    WORKER(0L, "WORKER", "工人", "", 24L),
    /**
     * FARMER。
     */
    FARMER(0L, "FARMER", "农民", "", 27L),
    /**
     * STUDENT。
     */
    STUDENT(0L, "STUDENT", "学生", "", 31L),
    /**
     * ACTIVE_MILITARY。
     */
    ACTIVE_MILITARY(0L, "ACTIVE_MILITARY", "现役军人", "", 37L),
    /**
     * FREELANCER。
     */
    FREELANCER(0L, "FREELANCER", "自由职业者", "", 51L),
    /**
     * SELF_EMPLOYED。
     */
    SELF_EMPLOYED(0L, "SELF_EMPLOYED", "个体经营者", "", 54L),
    /**
     * UNEMPLOYED。
     */
    UNEMPLOYED(0L, "UNEMPLOYED", "无业人员", "", 70L),
    /**
     * RETIREE。
     */
    RETIREE(0L, "RETIREE", "退（离）休人员", "", 80L),
    /**
     * OTHER。
     */
    OTHER(0L, "OTHER", "其他", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * OccupationType。
     */
    OccupationType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
