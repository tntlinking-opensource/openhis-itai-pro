/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.enums;

/**
 * 医保协同单状态（对齐 06-协同单与 Worker §2）。
 */
public enum YbCoordinationState {

    /** 已创建，等待 Worker 领取。 */
    CREATED,
    /** 平台接口调用中。 */
    PLATFORM_RUNNING,
    /** 平台步骤已成功。 */
    PLATFORM_OK,
    /** 本地落账执行中。 */
    LOCAL_RUNNING,
    /** 本地落账待重试。 */
    LOCAL_PENDING,
    /** 协同单全流程完成。 */
    COMPLETED,
    /** 协同单失败（可补偿或人工介入）。 */
    FAILED,
    /** 平台响应未知，待对账。 */
    UNKNOWN,
    /** 失败恢复中。 */
    RECOVERING,
    /** 补偿流程执行中。 */
    COMPENSATING,
    /** 补偿已完成。 */
    COMPENSATED,
    /** 协同单已取消。 */
    CANCELLED;

    public String getCode() {
        return name();
    }

    public static YbCoordinationState fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean sameAs(String stateCode) {
        return this.getCode().equals(stateCode);
    }
}
