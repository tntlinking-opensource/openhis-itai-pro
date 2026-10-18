/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * IF-DOR-20 / IF-IP-DOR-22：医生站医嘱查询范围 Tab（单接口分 Tab 入参）。
 *
 * <p>后端统一返回与 IF-DOR-10 / IF-IP-DOR-10 同构的全类型医嘱列表行；是否按处方形式展示由前端自行处理。</p>
 */
public interface DoctorStationAdviceScopeTab {

    /** 当前就诊医嘱（默认）：按 {@code encounterId} 收窄。 */
    String CURRENT_ADVICE = "CURRENT_ADVICE";

    /** 历史医嘱：按 {@code patientId} 查全量，并排除当前 {@code encounterId}。 */
    String HISTORY = "HISTORY";
}
