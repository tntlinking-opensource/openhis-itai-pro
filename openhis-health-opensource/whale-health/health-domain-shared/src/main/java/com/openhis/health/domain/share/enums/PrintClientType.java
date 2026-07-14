/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 打印客户端类型（DM-PRT-002 §9.2 · {@code prt_print_event_log.client_type}）。
 */
@Getter
public enum PrintClientType implements EnumInterface {

    /** Vue + OpenHIS.Win 路径默认客户端。 */
    OPENHIS_WIN(0L, "openhis-win", "OpenHIS Windows 客户端", "", 1L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PrintClientType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断客户端类型码是否与当前枚举值匹配。
     *
     * @param clientTypeCode 库表 {@code client_type} 列值
     * @return 匹配返回 {@code true}
     */
    public boolean sameAs(String clientTypeCode) {
        return this.code.equals(clientTypeCode);
    }
}
