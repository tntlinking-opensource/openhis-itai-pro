/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import lombok.Getter;

/**
 * 执行事件状态枚举（与 {@code DM-CLN-004} §4.1 {@code EventStatus} 一致）。
 *
 * <p>持久化与事件载荷一律使用 {@link #getCode()} 字符串；取消执行终态为 {@link #CANCEL}，
 * {@link #STOPPED} 为执行中止（非取消），当前域写路径未使用。</p>
 */
@Getter
public enum EventStatus implements EnumInterface {
    /**
     * 待执行。
     */
    PREPARATION(0L, "PREPARATION", "待执行", "", 1L),
    /**
     * 执行中。
     */
    IN_PROGRESS(0L, "IN_PROGRESS", "执行中", "", 2L),
    /**
     * 已完成。
     */
    COMPLETED(0L, "COMPLETED", "已完成", "", 3L),
    /**
     * 已停止（执行中止，非取消执行）；域命令暂未使用，保留与 DM 一致。
     */
    STOPPED(0L, "STOPPED", "已停止", "", 4L),
    /**
     * 取消执行（{@code cancelProcedureRecord} 终态；护士站净次数「完成 − 取消」扣减口径）。
     */
    CANCEL(0L, "CANCEL", "取消执行", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * EventStatus。
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
