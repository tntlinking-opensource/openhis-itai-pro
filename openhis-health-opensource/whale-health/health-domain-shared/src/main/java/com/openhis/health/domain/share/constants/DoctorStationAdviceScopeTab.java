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

package com.openhis.health.domain.share.constants;

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
