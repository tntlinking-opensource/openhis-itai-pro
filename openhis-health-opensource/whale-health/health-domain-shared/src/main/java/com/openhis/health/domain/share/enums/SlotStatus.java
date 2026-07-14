/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;
/**
 * 号源时段状态（FHIR Slot.status）。
 */
public enum SlotStatus implements EnumInterface {
    /** 空闲，可接受新预约（SlotStatus，编码 {@code free}）。 */
    FREE(0L, "free", "空闲，可接受新预约", "", 1L),
    /** 已占用，至少存在一条预约占用事实（SlotStatus，编码 {@code busy}）。 */
    BUSY(0L, "busy", "已占用，至少存在一条预约占用事实", "", 2L),
    /** 封锁，不可用（停诊、设备检修等非预约原因）（SlotStatus，编码 {@code BUSY_UNAVAILABLE}）。 */
    BUSY_UNAVAILABLE(0L, "BUSY_UNAVAILABLE", "封锁，不可用（停诊、设备检修等非预约原因）", "", 3L),
    /** 暂定占用，待确认（SlotStatus，编码 {@code BUSY_TENTATIVE}）。 */
    BUSY_TENTATIVE(0L, "BUSY_TENTATIVE", "暂定占用，待确认", "", 4L),
    /** 录入差错终态，仅用于审计纠错，不参与正常预约（SlotStatus，编码 {@code ENTERED_IN_ERROR}）。 */
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "录入差错终态，仅用于审计纠错，不参与正常预约", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    SlotStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }

}
