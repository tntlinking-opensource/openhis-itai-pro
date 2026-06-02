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
 * 费用性质（财务合约）分类：与基础数据维护页 {@code category} 下拉及库内 {@code category} 字符串码对齐。
 *
 * <p>持久化/接口字段与 {@link #getCode()}（{@code "1"}～{@code "5"}）对齐；展示经
 * {@link com.openhis.health.domain.share.utils.EnumUtil#fromCode(Class, String)} 解析后取 {@link #getDisplay()}。</p>
 */
public enum ChargeTypeCategory implements EnumInterface {

    /** 自费 */
    SELF_PAY(0L, "1", "自费", "", 1L),
    /** 公费 */
    PUBLIC_PAY(0L, "2", "公费", "", 2L),
    /** 省医保 */
    PROVINCIAL_INSURANCE(0L, "3", "省医保", "", 3L),
    /** 市医保 */
    MUNICIPAL_INSURANCE(0L, "4", "市医保", "", 4L),
    /** 职工医保 */
    EMPLOYEE_INSURANCE(0L, "5", "职工医保", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ChargeTypeCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
