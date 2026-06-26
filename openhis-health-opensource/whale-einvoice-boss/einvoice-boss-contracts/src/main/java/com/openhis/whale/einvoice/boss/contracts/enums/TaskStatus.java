package com.openhis.whale.einvoice.boss.contracts.enums;

/**
 * 异步开票任务状态（einv_boss_issue_task.status）。
 */
public enum TaskStatus {

    /** 待 Worker 消费。 */
    PENDING("PENDING"),

    /** Worker 正在处理。 */
    PROCESSING("PROCESSING"),

    /** 开具成功或已补偿对齐。 */
    SUCCESS("SUCCESS"),

    /** 开具失败，等待重试或人工补偿。 */
    FAILED("FAILED");

    private final String code;

    TaskStatus(String code) {
        this.code = code;
    }

    /**
     * 落库使用的状态码。
     *
     * @return status 字符串
     */
    public String getCode() {
        return code;
    }
}
