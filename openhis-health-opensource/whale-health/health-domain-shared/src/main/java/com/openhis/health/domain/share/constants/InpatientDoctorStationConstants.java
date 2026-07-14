/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 就诊不在当前会话科室医生站可见范围（EX-IP-DOR-103；**BR-IP-DOR-010**）。 */
    String EX_IP_DOR_103 = "EX-IP-DOR-103";
}
