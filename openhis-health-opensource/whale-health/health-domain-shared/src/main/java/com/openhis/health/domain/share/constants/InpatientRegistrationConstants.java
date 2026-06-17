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
 * 住院登记读侧列表筛选常量（对齐 AS-IP-FIN-001 / SDD-GAP-001）。
 */
public interface InpatientRegistrationConstants {

    /** 待登记：{@code status_enum = PLANNED}。 */
    String LIST_STATUS_PLANNED = "PLANNED";

    /** 待入科：{@code status_enum = REGISTERED}。 */
    String LIST_STATUS_WAIT_ADMISSION = "WAIT_ADMISSION";

    /** 已入科：{@code status_enum = IN_PROGRESS}。 */
    String LIST_STATUS_ADMITTED = "ADMITTED";

    /** 缺省列表筛选（待登记），由应用层装配至 Query。 */
    String DEFAULT_LIST_STATUS = LIST_STATUS_PLANNED;

    /** 门诊转住院默认入院方式码（与 {@code ter_concept} {@code in_way_code} code=1 对齐）。 */
    String DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER = "1";

    /** 登记必填项缺失（AS-IP-FIN-001 IF-IP-REG-03）。 */
    String EX_IP_REG_001 = "EX-IP-REG-001";

    /** 科室/病区/费用性质无效。 */
    String EX_IP_REG_002 = "EX-IP-REG-002";

    /** 就诊状态不允许登记。 */
    String EX_IP_REG_003 = "EX-IP-REG-003";

    /** 预交金收款失败（整单回滚）。 */
    String EX_IP_REG_004 = "EX-IP-REG-004";

    /** 取消时预交金余额大于 0。 */
    String EX_IP_REG_005 = "EX-IP-REG-005";

    /** 已分床或重复取消。 */
    String EX_IP_REG_006 = "EX-IP-REG-006";

    /** 列表查询参数无效。 */
    String EX_IP_REG_101 = "EX-IP-REG-101";

    /** 就诊不存在或读模型未投影。 */
    String EX_IP_REG_102 = "EX-IP-REG-102";
}
