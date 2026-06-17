/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.bridge.shared.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Bridge 共享组件自装配（科室名、诊断快照、操作人等）。
 * <p>院点启动类通常不扫描 {@code com.openhis.health.bridge.shared}，须经 AutoConfiguration 注册。</p>
 * <p>querysupport 读侧组装与 scope validator 供 Module adapter 共用。</p>
 */
@AutoConfiguration
@ComponentScan(basePackages = {
        "com.openhis.health.bridge.shared",
        "com.openhis.health.module.querysupport",
        "com.openhis.health.application.querysupport",
        "com.openhis.health.application.common.validator",
        "com.openhis.health.application.common.assembler",
        "com.openhis.health.application.common.assembler.advice",
        "com.openhis.health.application.inpatient.nursestation.costdetail.assembler",
        "com.openhis.health.application.inpatient.nursestation.costdetail.mapper",
        "com.openhis.health.application.inpatient.financial.settle.mapper",
        "com.openhis.health.application.inpatient.nursestation.orderexecute.assembler",
        "com.openhis.health.application.inpatient.nursestation.orderexecute.mapper",
        "com.openhis.health.application.inpatient.nursestation.orderview.assembler",
        "com.openhis.health.application.inpatient.doctorstation.advice.query",
        "com.openhis.health.application.inpatient.doctorstation.advice.assembler",
        "com.openhis.health.application.outpatient.pharmacy.dispense.mapper",
        "com.openhis.health.application.inpatient.pharmacy.dispense.mapper"
})
public class HealthBridgeSharedAutoConfiguration {
}
