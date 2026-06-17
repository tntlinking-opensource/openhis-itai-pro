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
 * 组套模板应用编排常量（生命周期动作等离散路由码，非领域状态枚举）。
 */
public interface OrderTemplateConstants {

    /**
     * 维护端头生命周期动作码（IF-BAS-OTL-06）。
     */
    interface LifecycleAction {

        /** 发布：DRAFT → ACTIVE。 */
        String PUBLISH = "PUBLISH";

        /** 停用：ACTIVE/DRAFT → RETIRED。 */
        String RETIRE = "RETIRE";

        /** 逻辑删除头并级联软删行。 */
        String DELETE = "DELETE";
    }

    /**
     * 组套套用预览/写路径上下文（IF-BAS-OTL-08/09/12）。
     */
    interface ApplyContext {

        /** 医生站开嘱套用。 */
        String DOCTOR = "DOCTOR";

        /** 护士站划价组套套用。 */
        String NURSE_PRICING = "NURSE_PRICING";
    }

    /**
     * 医生站确认套用工作站类型（IF-BAS-OTL-09）。
     */
    interface WorkstationKind {

        /** 门诊医生站。 */
        String OUTPATIENT = "OUTPATIENT";

        /** 住院医生站。 */
        String INPATIENT = "INPATIENT";
    }
}
