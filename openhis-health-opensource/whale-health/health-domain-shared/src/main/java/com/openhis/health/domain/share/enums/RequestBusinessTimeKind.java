/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 医嘱业务时间种类：住院改开立/停嘱时间（AS-IP-DOR-003 IF-IP-DOR-26）。
 *
 * <p>持久化身份一律使用 {@link #getCode()}；禁止改签发时间。</p>
 */
@Getter
public enum RequestBusinessTimeKind implements EnumInterface {

    /** 改开立时间（{@code req_opened_time}）。 */
    OPENED(0L, "OPENED", "开立时间", "改 Request 开立时间；不改开立人。", 1L),

    /** 改停嘱时间（{@code stop_time}）。 */
    STOP(0L, "STOP", "停嘱时间", "改 Request 停嘱时间；须已停嘱；不改停嘱人。", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * @param codeSystemId 编码系统标识
     * @param code         落库/入参 code
     * @param display      展示名
     * @param definition   业务定义
     * @param sortOrder    展示排序（非身份）
     */
    RequestBusinessTimeKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 按 code 解析；无法识别时返回 null。
     *
     * @param code 入参 code（大小写敏感，与 {@link #getCode()} 一致）
     * @return 枚举或 null
     */
    public static RequestBusinessTimeKind fromCodeOrNull(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (RequestBusinessTimeKind kind : values()) {
            // 仅按持久化 code 匹配，禁止用 display 作身份。
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        return null;
    }
}
