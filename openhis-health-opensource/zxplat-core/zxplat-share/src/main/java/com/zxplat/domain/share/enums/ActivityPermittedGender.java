/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 诊疗项目适用性别（{@code ActivityDefinition.permitted_gender_enum}）。
 *
 * <p>体检目录用于限制项目对男/女体检者的适用性；医嘱开立等场景通常为 {@link #BOTH}。</p>
 */
public enum ActivityPermittedGender implements EnumInterface {
    /** 不限性别（ActivityPermittedGender，编码 {@code BOTH}）。 */
    BOTH(0L, "BOTH", "不限", "男女均可适用", 0L),
    /** 仅男性（ActivityPermittedGender，编码 {@code MALE}）。 */
    MALE(0L, "MALE", "男", "仅男性适用", 1L),
    /** 仅女性（ActivityPermittedGender，编码 {@code FEMALE}）。 */
    FEMALE(0L, "FEMALE", "女", "仅女性适用", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     *
     * @param codeSystemId 编码系统标识（本枚举占位 0）
     * @param code         持久化码
     * @param display      展示名
     * @param definition   定义说明
     * @param sortOrder    排序
     */
    ActivityPermittedGender(
        Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
