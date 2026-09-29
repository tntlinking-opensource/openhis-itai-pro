/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

        /** 重新启用：RETIRED → ACTIVE，恢复套用。 */
        String REACTIVATE = "REACTIVATE";

        /** 逻辑删除头并级联软删行。 */
        String DELETE = "DELETE";
    }

    /**
     * 组套套用预览/写路径上下文（IF-BAS-OTL-08/09/12）。
     */
    interface ApplyContext {

/** OrderTemplate 共享常量：DOCTOR，值 {@code DOCTOR}。 */
        String DOCTOR = "DOCTOR";

        /** 护士站划价组套套用。 */
        String NURSE_PRICING = "NURSE_PRICING";
    }

    /**
     * 医生站确认套用工作站类型（IF-BAS-OTL-09）。
     */
    interface WorkstationKind {

/** OrderTemplate 共享常量：OUTPATIENT，值 {@code OUTPATIENT}。 */
        String OUTPATIENT = "OUTPATIENT";

/** OrderTemplate 共享常量：INPATIENT，值 {@code INPATIENT}。 */
        String INPATIENT = "INPATIENT";
    }
}
