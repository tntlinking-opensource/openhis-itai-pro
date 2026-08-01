/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 治疗类型枚举（长/临/出院带药）。
 *
 * <p>对应库表字段 {@code therapy_enum}（药品申请、服务申请共用）。持久化与业务分支身份一律使用
 * {@link #getCode()} 英文编码（与 {@code DM-MED-003} §4.2、{@code DM-WKF-004} §4.5 一致），
 * 禁止数字串或展示名作身份。</p>
 *
 * <p>{@link #getSortOrder()} 仅用于展示排序，不得作为库表或业务分支的身份依据。</p>
 *
 * <p>数量语义：{@link #LONG_TERM} 为单次用量；{@link #TEMPORARY} 与 {@link #DISCHARGE_MEDICATION}
 * 为总量（见 {@code BR-MRQ-009} / {@code BR-IP-ORD-029}）。</p>
 */
@Getter
public enum TherapyType implements EnumInterface {

    /** 长期医嘱：可持续执行至停嘱；{@code quantity}/{@code unit_code} 为单次用量语义。 */
    LONG_TERM(0L, "LONG_TERM", "长期医嘱", "长期医嘱；数量为单次用量。", 1L),

    /** 临时医嘱：一次性开立；{@code quantity}/{@code unit_code} 为总量语义。 */
    TEMPORARY(0L, "TEMPORARY", "临时医嘱", "临时医嘱；数量为总量。", 2L),

    /**
     * 出院带药（仅住院药品路径使用）：开立生命周期同临时；数量语义同临时（总量）；
     * 不提供停嘱；执行后发放不写 {@code summary_no}（见 {@code BR-IP-ORD-029}）。
     */
    DISCHARGE_MEDICATION(0L, "DISCHARGE_MEDICATION", "出院带药", "住院出院带药；数量为总量；不停嘱。", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名、定义与排序。
     *
     * @param codeSystemId 编码系统标识（未接术语系统时可为 0）
     * @param code         落库 code（{@link #getCode()}）
     * @param display      展示名
     * @param definition   业务定义说明
     * @param sortOrder    展示排序（非身份）
     */
    TherapyType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
