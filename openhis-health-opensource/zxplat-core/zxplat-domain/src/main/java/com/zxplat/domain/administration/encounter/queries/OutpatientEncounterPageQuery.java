/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 门诊就诊上下文分页入参（替代 RM {@code OutpatientInfoPageQuery}）。
 */
@Getter
@Setter
public class OutpatientEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 患者主键。 */
    private Long patientId;
    /** 接诊医师 practitioner 主键（通用门诊列表筛选）。 */
    private Long doctorId;
    /**
     * 门诊医生站 IF-DOR-01：当前登录医师主键；非空时在读侧启用 BR-DOR-009
     * 「状态 × 参与者角色」可见性谓词（与 {@link #doctorId} 互斥，医生站路径仅用本字段）。
     */
    private Long doctorStationViewerPractitionerId;
    /** 就诊状态单值筛选。 */
    private String statusEnum;
    /** 就诊状态多值 IN 筛选（优先级高于 {@link #statusEnum}）。 */
    private List<String> statusEnumIn;
}
