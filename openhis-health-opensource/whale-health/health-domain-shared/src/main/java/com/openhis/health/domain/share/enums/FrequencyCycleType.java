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

/**
 * 频次周期类型（DM-CUS-008 §4.1）。
 *
 * <p>持久化身份为 {@link #getCode()} 字符串；{@link #getSortOrder()} 仅用于维护页下拉排序，非库表身份。</p>
 */
public enum FrequencyCycleType implements EnumInterface {

    /** 日周期：QD、BID、TID 等按自然日重复。 */
    DAILY(0L, "daily", "日周期", "按天重复执行", 1L),

    /** 周周期：QW、BIW 等按周重复。 */
    WEEKLY(0L, "weekly", "周周期", "按周重复执行", 2L),

    /** 间隔周期：Q8H、QOD、隔 N 天等固定间隔。 */
    INTERVAL(0L, "interval", "间隔周期", "按固定时间或日间隔重复", 3L),

    /** 必要时：PRN，无固定计划时点。 */
    AS_NEEDED(0L, "as-needed", "必要时", "按需执行，不展开固定计划点", 4L),

    /** 固定次数：ST 等限定总执行次数。 */
    FIXED_COUNT(0L, "fixed-count", "固定次数", "总执行次数固定", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * @param codeSystemId 编码系统标识（频次主数据非术语，固定 0）
     * @param code         持久化 code
     * @param display      展示名称
     * @param definition   业务定义
     * @param sortOrder    维护页下拉排序（非持久化身份）
     */
    FrequencyCycleType(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder
    ) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
