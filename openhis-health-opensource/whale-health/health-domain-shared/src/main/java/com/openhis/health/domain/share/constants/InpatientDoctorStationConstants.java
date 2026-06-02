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
 * 住院医生站就诊管理共享常量（AS-IP-DOR-001）。
 *
 * <p>编排消息码供 {@code InpatientDoctorStationEncounterManageAppService} 与前端错误展示对齐；
 * 落点遵守 B.10.2.1（禁止在 health-application 下自建 constants 包）。</p>
 */
public interface InpatientDoctorStationConstants {

    /** 列表/批量查询失败（EX-IP-DOR-101）。 */
    String EX_IP_DOR_101 = "EX-IP-DOR-101";

    /** 未选择科室上下文（EX-IP-DOR-102，实现侧常配合 {@code ENCOUNTER_INVALID_ARGUMENT}）。 */
    String EX_IP_DOR_102 = "EX-IP-DOR-102";
}
