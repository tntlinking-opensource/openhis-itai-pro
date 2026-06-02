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
 * 家庭关系枚举。
 *
 * <p>仅用于下拉/展示与入参校验，持久化使用 code。</p>
 */
public enum FamilyRelationshipType implements EnumInterface {
    /**
     * SELF。
     */
    SELF(0L, "SELF", "本人", "", 1L),
    /**
     * HOUSEHOLDER。
     */
    HOUSEHOLDER(0L, "HOUSEHOLDER", "户主", "", 2L),
    /**
     * SPOUSE。
     */
    SPOUSE(0L, "SPOUSE", "配偶", "", 10L),
    /**
     * HUSBAND。
     */
    HUSBAND(0L, "HUSBAND", "夫", "", 11L),
    /**
     * WIFE。
     */
    WIFE(0L, "WIFE", "妻", "", 12L),
    /**
     * SON。
     */
    SON(0L, "SON", "子", "", 20L),
    /**
     * DAUGHTER。
     */
    DAUGHTER(0L, "DAUGHTER", "女", "", 30L),
    /**
     * PARENTS。
     */
    PARENTS(0L, "PARENTS", "父母", "", 50L),
    /**
     * FATHER。
     */
    FATHER(0L, "FATHER", "父亲", "", 51L),
    /**
     * MOTHER。
     */
    MOTHER(0L, "MOTHER", "母亲", "", 52L),
    /**
     * SIBLINGS。
     */
    SIBLINGS(0L, "SIBLINGS", "兄、弟、姐、妹", "", 70L),
    /**
     * OTHER_RELATIVES。
     */
    OTHER_RELATIVES(0L, "OTHER_RELATIVES", "其他", "", 80L),
    /**
     * NON_RELATIVE。
     */
    NON_RELATIVE(0L, "NON_RELATIVE", "非亲属", "", 99L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * FamilyRelationshipType。
     */
    FamilyRelationshipType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
