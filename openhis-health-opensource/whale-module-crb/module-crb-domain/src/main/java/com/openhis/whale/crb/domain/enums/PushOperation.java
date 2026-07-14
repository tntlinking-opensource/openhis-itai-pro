/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.enums;

/**
 * EMR receive 推送操作类型。
 */
public enum PushOperation {

    /** 新增或更新 POST body。 */
    UPSERT("UPSERT"),

    /** 按 id 删除前置侧记录。 */
    DELETE("DELETE");

    private final String code;

    PushOperation(String code) {
        this.code = code;
    }

    /**
     * 持久化使用的字符串身份。
     *
     * @return 操作 code
     */
    public String getCode() {
        return code;
    }
}
