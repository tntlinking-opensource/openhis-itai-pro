/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.enums;

/**
 * 病案工作流角色（状态校验用）。
 */
public enum WorkflowRole {

    /** 住院医生：可编辑草稿/退回态首页并提交。 */
    DOCTOR("doctor", "住院医生"),
    /** 病案室：可接收/退回。 */
    RECORD_ROOM("recordRoom", "病案室"),
    /** 系统内部：离院建档等。 */
    SYSTEM("system", "系统");

    private final String code;
    private final String label;

    WorkflowRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 角色 code。
     *
     * @return 角色标识
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
}
