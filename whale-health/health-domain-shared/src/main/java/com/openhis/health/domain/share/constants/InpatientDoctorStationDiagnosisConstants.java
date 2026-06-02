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
 * 住院医生站「诊断录入」跨层共享常量（AS-IP-DOR-002）。
 *
 * <p>与 AS 异常表 EX-IP-DOD-* 一一对应；删除原因码与领域命令 removeReason/reason 字段对齐。</p>
 */
public interface InpatientDoctorStationDiagnosisConstants {

    /** 列表或目录检索失败（EX-IP-DOD-101）。 */
    String EX_IP_DOD_101 = "EX-IP-DOD-101";

    /** 诊断已有医嘱引用（EX-IP-DOD-102）。 */
    String EX_IP_DOD_102 = "EX-IP-DOD-102";

    /** 未选择患者或就诊（EX-IP-DOD-104）。 */
    String EX_IP_DOD_104 = "EX-IP-DOD-104";

    /** 持久化失败（EX-IP-DOD-105）。 */
    String EX_IP_DOD_105 = "EX-IP-DOD-105";

    /** 主诊断多于一个（EX-IP-DOD-106）。 */
    String EX_IP_DOD_106 = "EX-IP-DOD-106";

    /** 患者疾病诊断类型码无效（EX-IP-DOD-107）。 */
    String EX_IP_DOD_107 = "EX-IP-DOD-107";

    /** 就诊状态不允许编辑诊断（EX-IP-DOD-108）。 */
    String EX_IP_DOD_108 = "EX-IP-DOD-108";

    /** 住院医生站删除诊断时写入关联/事实的移除原因码。 */
    String DELETE_REASON_INPATIENT_DOCTOR_STATION = "INPATIENT_DOCTOR_STATION_DELETE";
}
