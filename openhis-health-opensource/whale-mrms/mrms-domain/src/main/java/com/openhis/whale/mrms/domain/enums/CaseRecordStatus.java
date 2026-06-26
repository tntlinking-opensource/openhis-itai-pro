/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.enums;

/**
 * 病案主档状态（持久化 {@code status_code} 列）。
 */
public enum CaseRecordStatus {

    /** 草稿：医生可编辑首页。 */
    DRAFT("draft", "草稿"),
    /** 已提交：等待病案室接收。 */
    SUBMITTED("submitted", "已提交"),
    /** 已退回：医生需修改后重新提交。 */
    RETURNED("returned", "已退回"),
    /** 已接收：病案室已确认。 */
    RECEIVED("received", "已接收"),
    /** 质控中：Wave 2 启用。 */
    QCING("qcing", "质控中"),
    /** 已归档：首页冻结不可编辑。 */
    ARCHIVED("archived", "已归档");

    private final String code;
    private final String label;

    CaseRecordStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 持久化状态码。
     *
     * @return 状态 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 中文展示名。
     *
     * @return 展示标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 按 code 解析枚举。
     *
     * @param code 持久化状态码
     * @return 枚举实例
     */
    public static CaseRecordStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CaseRecordStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
