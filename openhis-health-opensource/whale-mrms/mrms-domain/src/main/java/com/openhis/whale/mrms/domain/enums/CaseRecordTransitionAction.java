/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.enums;

/**
 * 病案状态迁移动作（工作流入口）。
 */
public enum CaseRecordTransitionAction {

    /** 创建主档。 */
    CREATE("create", "创建"),
    /** 医生提交首页。 */
    SUBMIT("submit", "提交"),
    /** 病案室接收。 */
    RECEIVE("receive", "接收"),
    /** 退回医生修改。 */
    RETURN("return", "退回"),
    /** 开始质控（Wave 2）。 */
    START_QC("startQc", "开始质控"),
    /** 归档（Wave 2）。 */
    ARCHIVE("archive", "归档");

    private final String code;
    private final String label;

    CaseRecordTransitionAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 动作 code。
     *
     * @return 动作标识
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
