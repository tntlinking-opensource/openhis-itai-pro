/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import lombok.Getter;

/**
 * 执行事件状态枚举（与 {@code DM-CLN-004} §4.1 {@code EventStatus} 一致）。
 *
 * <p>持久化与事件载荷一律使用 {@link #getCode()} 字符串；取消执行终态为 {@link #CANCEL}，
 * {@link #STOPPED} 为执行中止（非取消），当前域写路径未使用。</p>
 */
@Getter
public enum EventStatus implements EnumInterface {
    /** 待执行（EventStatus，编码 {@code PREPARATION}）。 */
    PREPARATION(0L, "PREPARATION", "待执行", "", 1L),
    /** 执行中（EventStatus，编码 {@code IN_PROGRESS}）。 */
    IN_PROGRESS(0L, "IN_PROGRESS", "执行中", "", 2L),
    /** 已完成（EventStatus，编码 {@code COMPLETED}）。 */
    COMPLETED(0L, "COMPLETED", "已完成", "", 3L),
    /** 已停止（EventStatus，编码 {@code STOPPED}）；执行中止，非取消执行，域命令暂未使用。 */
    STOPPED(0L, "STOPPED", "已停止", "", 4L),
    /** 取消执行（EventStatus，编码 {@code CANCEL}）；{@code cancelProcedureRecord} 终态，护士站净次数「完成 − 取消」扣减口径。 */
    CANCEL(0L, "CANCEL", "取消执行", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    EventStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判定持久化 {@code status_enum} 是否计入「已取消次数」（净次数 = 完成 − 取消）。
     *
     * <p>历史存量可能仍为 {@code ABORTED}，与 {@link #CANCEL} 同语义；由 Flyway V43 收敛后仅应出现 {@code CANCEL}。</p>
     *
     * @param statusCode 库表或读模型中的状态 code
     * @return 若为取消执行口径则 true
     */
    public static boolean isCancelExecutionStatusCode(String statusCode) {
        if (statusCode == null) {
            return false;
        }
        if (CANCEL.getCode().equals(statusCode)) {
            return true;
        }
        if (GeneralSharedConstants.EVENT_STATUS_LEGACY_ABORTED_CODE.equals(statusCode)) {
            return true;
        }
        return false;
    }

}
