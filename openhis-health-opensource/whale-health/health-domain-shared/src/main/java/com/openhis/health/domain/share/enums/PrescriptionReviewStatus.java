/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 处方点评状态（DM-CUS-015）。
 *
 * <p>{@code PENDING} 仅展示/筛选用；有效落库行固定 {@code REVIEWED}。</p>
 */
public enum PrescriptionReviewStatus implements EnumInterface {
    /** 待点评（无有效点评行时的展示态）。 */
    PENDING(0L, "PENDING", "待点评", "该处方尚无有效点评", 1L),
    /** 已点评（有效点评行存库值）。 */
    REVIEWED(0L, "REVIEWED", "已点评", "存在有效点评行", 2L);

    /** 编码系统标识（本枚举未绑定外部系统时可为 0）。 */
    private final Long codeSystemId;
    /** 持久化与契约身份 code。 */
    private final String code;
    /** 前端/报表展示名。 */
    private final String display;
    /** 业务定义说明。 */
    private final String definition;
    /** 下拉/展示排序号。 */
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     *
     * @param codeSystemId 编码系统标识
     * @param code         持久化 code
     * @param display      展示名
     * @param definition   定义说明
     * @param sortOrder    排序
     */
    PrescriptionReviewStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 返回编码系统标识。
     *
     * @return 编码系统标识
     */
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    /**
     * 返回持久化 code（状态/字典列身份）。
     *
     * @return 持久化 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 返回展示名。
     *
     * @return 展示名
     */
    public String getDisplay() {
        return display;
    }

    /**
     * 返回定义说明。
     *
     * @return 定义说明
     */
    public String getDefinition() {
        return definition;
    }

    /**
     * 返回排序号。
     *
     * @return 排序号
     */
    public Long getSortOrder() {
        return sortOrder;
    }
}
