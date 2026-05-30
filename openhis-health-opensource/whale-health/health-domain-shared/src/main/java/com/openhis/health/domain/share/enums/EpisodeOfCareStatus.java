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

import lombok.Getter;

/**
 * 治疗周期（Episode of Care）生命周期状态（DM-ADM-013 §2.3）。
 *
 * <p>持久化与事件载荷身份为 {@link #code} 字符串；禁止以数字串 {@code "1"}～{@code "9"} 充当状态身份（§0.12）。</p>
 */
@Getter
public enum EpisodeOfCareStatus implements EnumInterface {

    /** 已计划，尚未进入活跃治疗。 */
    PLANNED(0L, "PLANNED", "已计划", "", 1L),

    /** 活跃中，当前周期内正在接受治疗或管理。 */
    ACTIVE(0L, "ACTIVE", "活跃中", "", 2L),

    /** 暂停中，临时中断但周期未结束。 */
    ON_HOLD(0L, "ON_HOLD", "暂停中", "", 3L),

    /** 已完成，周期正常结束。 */
    COMPLETED(0L, "COMPLETED", "已完成", "", 4L),

    /** 已取消，周期被撤销或作废。 */
    CANCELLED(0L, "CANCELLED", "已取消", "", 5L),

    /** 错误录入，需纠错或作废的数据行。 */
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "错误录入", "", 9L);

    /** 术语体系主键（展示/排序用，非持久化身份）。 */
    private final Long codeSystemId;

    /** 落库与比较用的状态 code。 */
    private final String code;

    /** 界面展示文案。 */
    private final String display;

    /** FHIR 风格定义说明（可空）。 */
    private final String definition;

    /** 列表排序权重（非持久化身份）。 */
    private final Long sortOrder;

    EpisodeOfCareStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
