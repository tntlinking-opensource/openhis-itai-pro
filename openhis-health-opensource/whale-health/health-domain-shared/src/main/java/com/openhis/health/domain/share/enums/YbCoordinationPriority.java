/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.share.enums;

/**
 * 医保协同单调度优先级。
 */
public enum YbCoordinationPriority {

    /** 最高优先级，阻塞结算类协同默认使用。 */
    P0,
    /** 普通优先级。 */
    P1,
    /** 低优先级，对账/批量类任务。 */
    P2;

    public String getCode() {
        return name();
    }

    public static YbCoordinationPriority fromCode(String code) {
        if (code == null || code.isBlank()) {
            return P1;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return P1;
        }
    }
}
