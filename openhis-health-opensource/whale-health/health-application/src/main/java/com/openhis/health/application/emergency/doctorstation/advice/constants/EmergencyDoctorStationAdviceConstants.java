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

package com.openhis.health.application.emergency.doctorstation.advice.constants;

/**
 * 急诊医嘱集成工作站应用层常量（与设计方案 AS-EMG-ORD-001 对齐）。
 */
public final class EmergencyDoctorStationAdviceConstants {

    private EmergencyDoctorStationAdviceConstants() {
    }

    public static final String PRODUCT_KIND_MEDICATION = "MEDICATION";
    public static final String PRODUCT_KIND_DEVICE = "DEVICE";
    public static final String PRODUCT_KIND_ACTIVITY = "ACTIVITY";

    public static final String LINE_OP_CREATE = "CREATE";
    public static final String LINE_OP_UPDATE = "UPDATE";
    public static final String LINE_OP_DELETE = "DELETE";

    public static final String RESPONSE_MESSAGE_OK = "OK";

    /** 急诊签发默认发放库位（机构未配置时的占位，生产应由配置中心覆盖）。 */
    public static final long DEFAULT_EMERGENCY_DISPENSE_LOCATION_ID = 0L;
}
