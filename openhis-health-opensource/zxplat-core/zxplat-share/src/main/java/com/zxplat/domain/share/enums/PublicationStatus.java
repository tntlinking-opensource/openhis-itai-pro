/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 发布状态枚举。
 */
@Getter
public enum PublicationStatus implements EnumInterface {
    /** 草稿（PublicationStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿", "", 1L),
    /** 已启用（PublicationStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已启用", "", 2L),
    /** 已停用（PublicationStatus，编码 {@code INACTIVE}）。 */
    INACTIVE(0L, "INACTIVE", "已停用", "", 3L),
    /** 已废弃（PublicationStatus，编码 {@code DISCARDED}）。 */
    DISCARDED(0L, "DISCARDED", "已废弃", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    PublicationStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
