/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 家庭关系枚举。
 *
 * <p>仅用于下拉/展示与入参校验，持久化使用 code。</p>
 */
public enum FamilyRelationshipType implements EnumInterface {
    /** 本人（FamilyRelationshipType，编码 {@code SELF}）。 */
    SELF(0L, "SELF", "本人", "", 1L),
    /** 户主（FamilyRelationshipType，编码 {@code HOUSEHOLDER}）。 */
    HOUSEHOLDER(0L, "HOUSEHOLDER", "户主", "", 2L),
    /** 配偶（FamilyRelationshipType，编码 {@code SPOUSE}）。 */
    SPOUSE(0L, "SPOUSE", "配偶", "", 10L),
    /** 夫（FamilyRelationshipType，编码 {@code HUSBAND}）。 */
    HUSBAND(0L, "HUSBAND", "夫", "", 11L),
    /** 妻（FamilyRelationshipType，编码 {@code WIFE}）。 */
    WIFE(0L, "WIFE", "妻", "", 12L),
    /** 子（FamilyRelationshipType，编码 {@code SON}）。 */
    SON(0L, "SON", "子", "", 20L),
    /** 女（FamilyRelationshipType，编码 {@code DAUGHTER}）。 */
    DAUGHTER(0L, "DAUGHTER", "女", "", 30L),
    /** 父母（FamilyRelationshipType，编码 {@code PARENTS}）。 */
    PARENTS(0L, "PARENTS", "父母", "", 50L),
    /** 父亲（FamilyRelationshipType，编码 {@code FATHER}）。 */
    FATHER(0L, "FATHER", "父亲", "", 51L),
    /** 母亲（FamilyRelationshipType，编码 {@code MOTHER}）。 */
    MOTHER(0L, "MOTHER", "母亲", "", 52L),
    /** 兄、弟、姐、妹（FamilyRelationshipType，编码 {@code SIBLINGS}）。 */
    SIBLINGS(0L, "SIBLINGS", "兄、弟、姐、妹", "", 70L),
    /** 其他（FamilyRelationshipType，编码 {@code OTHER_RELATIVES}）。 */
    OTHER_RELATIVES(0L, "OTHER_RELATIVES", "其他", "", 80L),
    /** 非亲属（FamilyRelationshipType，编码 {@code NON_RELATIVE}）。 */
    NON_RELATIVE(0L, "NON_RELATIVE", "非亲属", "", 99L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
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
