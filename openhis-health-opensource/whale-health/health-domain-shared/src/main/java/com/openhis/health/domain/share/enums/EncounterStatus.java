/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * 就诊状态枚举。
 */
@Getter
public enum EncounterStatus implements EnumInterface {
/** 待挂号（EncounterStatus，编码 {@code PLANNED}）。 */
    PLANNED(0L, "PLANNED", "待挂号", "", 1L),
/** 已挂号（EncounterStatus，编码 {@code REGISTERED}）。 */
    REGISTERED(0L, "REGISTERED", "已挂号", "", 2L),
/** 已分诊（EncounterStatus，编码 {@code TRIAGED}）。 */
    TRIAGED(0L, "TRIAGED", "已分诊", "", 3L),
/** 预入院（EncounterStatus，编码 {@code PRE_ADMISSION}）。 */
    PRE_ADMISSION(0L, "PRE_ADMISSION", "预入院", "", 4L),
/** 在诊（EncounterStatus，编码 {@code IN_PROGRESS}）。 */
    IN_PROGRESS(0L, "IN_PROGRESS", "在诊", "", 5L),
/** 暂离（EncounterStatus，编码 {@code ON_HOLD}）。 */
    ON_HOLD(0L, "ON_HOLD", "暂离", "", 6L),
/** 完诊（EncounterStatus，编码 {@code DISCHARGED}）。 */
    DISCHARGED(0L, "DISCHARGED", "完诊", "", 7L),
/** 已完成（EncounterStatus，编码 {@code COMPLETED}）。 */
    COMPLETED(0L, "COMPLETED", "已完成", "", 8L),
/** 已取消（EncounterStatus，编码 {@code CANCELLED}；门诊退号终态，挂号窗展示「退号」）。 */
    CANCELLED(0L, "CANCELLED", "已取消", "", 9L),
/** 待出院结算（EncounterStatus，编码 {@code PENDING}）。 */
    PENDING(0L, "PENDING", "待出院结算", "", 10L),
/** 错误录入（EncounterStatus，编码 {@code ENTERED_IN_ERROR}；门诊未收费取消终态）。 */
    ENTERED_IN_ERROR(0L, "ENTERED_IN_ERROR", "错误录入", "", 11L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    EncounterStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断是否为终止状态。
     *
     * @return 终止状态返回 true
     */
    public boolean isTerminal() {
        if (DISCHARGED.equals(this)) {
            return true;
        }
        if (CANCELLED.equals(this)) {
            return true;
        }
        if (ENTERED_IN_ERROR.equals(this)) {
            return true;
        }
        return false;
    }

    /** 门诊防重占名额态（读 {@link #outpatientDuplicateBlockingStatusCodes()}）。 */
    private static final List<String> OUTPATIENT_DUPLICATE_BLOCKING_STATUS_CODES = Collections.unmodifiableList(Arrays.asList(
        PLANNED.getCode(),
        REGISTERED.getCode(),
        TRIAGED.getCode(),
        IN_PROGRESS.getCode(),
        ON_HOLD.getCode(),
        DISCHARGED.getCode(),
        COMPLETED.getCode()
    ));

    /**
     * 门诊同日同科室防重：视为占用号源的状态 code 列表（含待挂号 {@link #PLANNED}；不含 {@link #CANCELLED}）。
     *
     * @return 不可变状态 code 列表，供读/写侧防重规约共用
     */
    public static List<String> outpatientDuplicateBlockingStatusCodes() {
        return OUTPATIENT_DUPLICATE_BLOCKING_STATUS_CODES;
    }

    /** 门诊已接诊态（历史号复诊资格 BR-REG-020；不含已挂号未接诊）。 */
    private static final List<String> OUTPATIENT_RECEPTION_STARTED_STATUS_CODES = Collections.unmodifiableList(Arrays.asList(
        IN_PROGRESS.getCode(),
        ON_HOLD.getCode(),
        DISCHARGED.getCode()
    ));

    /**
     * 门诊医生已接诊（在诊/暂离/完诊）状态 code 列表。
     *
     * @return 不可变状态 code 列表
     */
    public static List<String> outpatientReceptionStartedStatusCodes() {
        return OUTPATIENT_RECEPTION_STARTED_STATUS_CODES;
    }

}
