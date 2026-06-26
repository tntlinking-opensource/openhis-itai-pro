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
 * 住院发退药记录应用编排消息码与展示常量（AS-IP-PHA-004 §5）。
 */
public interface InpatientPharmacyDispenseRecordConstants {

    /** 页面初始化失败（EX-IPR-101）。 */
    String EX_IPR_101 = "EX-IPR-101";

    /** 发药时间窗非法（EX-IPR-102）。 */
    String EX_IPR_102 = "EX-IPR-102";

    /** 查询失败（EX-IPR-103）。 */
    String EX_IPR_103 = "EX-IPR-103";

    /** 导出失败（EX-IPR-104）。 */
    String EX_IPR_104 = "EX-IPR-104";
}
