/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * 启动时校验扩展模块 classpath 与 {@code openhis.modules.*.enabled} 是否一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionModuleProfileValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final String[][] MODULE_CHECKS = {
            {"emr", "com.openhis.whale.emr.application.config.EmrAutoConfiguration", "openhis.modules.emr.enabled"},
            {"chs", "com.openhis.whale.chs.starter.ChsJilinModuleAutoConfiguration", "openhis.modules.chs.enabled"},
            {"elep", "com.openhis.elep.application.config.ElepAutoConfiguration", "openhis.modules.elep.enabled"},
            {"booking", "com.openhis.whale.booking.application.config.BookingAutoConfiguration", "openhis.modules.booking.enabled"},
            {"einvoice-boss", "com.openhis.whale.einvoice.boss.application.config.EinvoiceBossAutoConfiguration", "openhis.modules.einvoice-boss.enabled"},
            {"unionpay-bpc", "com.openhis.whale.unionpay.bpc.application.config.UnionPayBpcAutoConfiguration", "openhis.modules.unionpay-bpc.enabled"},
    };

    private final Environment environment;

    /**
     * 启动时校验扩展模块 classpath 与 {@code openhis.modules.*.enabled} 配置是否一致。
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ClassLoader classLoader = event.getApplicationContext().getClassLoader();
        // 遍历内置模块：classpath 与 enabled 开关不匹配时打 warn，便于排查 Profile/依赖
        for (String[] check : MODULE_CHECKS) {
            String moduleId = check[0];
            String configClassName = check[1];
            String enabledProperty = check[2];
            boolean onClasspath = ClassUtils.isPresent(configClassName, classLoader);
            boolean enabled = environment.getProperty(enabledProperty, Boolean.class, false);
            // 已在 classpath 但未启用：AutoConfiguration 不会生效
            if (onClasspath && !enabled) {
                log.warn(
                        "扩展模块 {} 已在 classpath（{}）但 {}=false，AutoConfiguration 不会生效；"
                                + "请设置 openhis.modules.{}.enabled=true 或激活 Spring profile dev",
                        moduleId,
                        configClassName,
                        enabledProperty,
                        moduleId);
            }
            // 已启用但 classpath 缺实现：通常是 Maven Profile 或依赖未挂载
            if (!onClasspath && enabled) {
                log.warn(
                        "扩展模块 {} 配置 {}=true 但未发现 {}，请检查 Maven Profile（如 -Pwith-{}）与依赖",
                        moduleId,
                        enabledProperty,
                        configClassName,
                        moduleId);
            }
        }
        validateOptionalModule(
                classLoader,
                "module-chs-jilin-bridge",
                "com.openhis.whale.chs.adapter.config.ChsBridgeAutoConfiguration",
                "openhis.modules.chs.enabled");
        validateOptionalModule(
                classLoader,
                "elep-jilin-adapter",
                "com.openhis.whale.elep.adapter.config.ElepJilinAdapterAutoConfiguration",
                "openhis.modules.elep.enabled");
        validateOptionalModule(
                classLoader,
                "unionpay-bpc-adapter",
                "com.openhis.whale.unionpay.bpc.adapter.config.UnionPayBpcAdapterAutoConfiguration",
                "openhis.modules.unionpay-bpc.enabled");
        validateChsGatewayMode(classLoader);
        validateElepDeploymentMode(classLoader);
    }

    /**
     * 校验可选扩展模块（如 module-chs-jilin-bridge）的 classpath 与开关一致性。
     *
     * @param classLoader 应用 ClassLoader
     * @param moduleId 模块标识，用于日志
     * @param configClassName AutoConfiguration 全类名
     * @param enabledProperty 对应 enabled 配置键
     */
    private void validateOptionalModule(
            ClassLoader classLoader, String moduleId, String configClassName, String enabledProperty) {
        boolean onClasspath = ClassUtils.isPresent(configClassName, classLoader);
        boolean enabled = environment.getProperty(enabledProperty, Boolean.class, false);
        // 已在 classpath 但未启用
        if (onClasspath && !enabled) {
            log.warn(
                    "扩展模块 {} 已在 classpath（{}）但 {}=false",
                    moduleId,
                    configClassName,
                    enabledProperty);
        }
        // 已启用但 classpath 缺实现
        if (!onClasspath && enabled) {
            log.warn(
                    "扩展模块 {} 配置 {}=true 但未发现 {}，请检查 Maven Profile / 依赖",
                    moduleId,
                    enabledProperty,
                    configClassName);
        }
    }

    /**
     * 校验 CHS 部署模式（embedded/remote）与 Gateway 实现类 classpath 是否匹配。
     *
     * @param classLoader 应用 ClassLoader
     */
    private void validateChsGatewayMode(ClassLoader classLoader) {
        boolean chsEnabled = environment.getProperty("openhis.modules.chs.enabled", Boolean.class, false);
        if (!chsEnabled) {
            return;
        }
        String deploymentMode = environment.getProperty("openhis.chs.deployment.mode", "embedded");
        boolean remote = "remote".equalsIgnoreCase(deploymentMode);
        boolean localImplPresent = ClassUtils.isPresent(
                "com.openhis.chs.gateway.local.InsuranceGatewayClientLocalImpl", classLoader);
        boolean remoteImplPresent = ClassUtils.isPresent(
                "com.openhis.health.integration.chs.remote.InsuranceGatewayClientRemoteImpl", classLoader);
        if (remote && !remoteImplPresent) {
            log.warn("CHS remote 模式但未发现 InsuranceGatewayClientRemoteImpl，请激活 Maven Profile -Pwith-chs-remote");
        }
        if (!remote && !localImplPresent) {
            log.warn("CHS embedded 模式但未发现 LocalImpl，请激活 Maven Profile -Pwith-chs");
        }
    }

    /**
     * 校验 ELEP 部署模式（embedded/remote）与 Operations 实现类 classpath 是否匹配。
     *
     * @param classLoader 应用 ClassLoader
     */
    private void validateElepDeploymentMode(ClassLoader classLoader) {
        boolean elepEnabled = environment.getProperty("openhis.modules.elep.enabled", Boolean.class, false);
        if (!elepEnabled) {
            return;
        }
        String deploymentMode = environment.getProperty("openhis.elep.deployment.mode", "embedded");
        boolean remote = "remote".equalsIgnoreCase(deploymentMode);
        boolean localImplPresent = ClassUtils.isPresent(
                "com.openhis.elep.application.prescription.workflow.ElepPrescriptionOrchestrator", classLoader);
        boolean remoteImplPresent = ClassUtils.isPresent(
                "com.openhis.health.integration.elep.remote.client.ElepPrescriptionOperationsRemoteClientImpl",
                classLoader);
        if (remote && !remoteImplPresent) {
            log.warn("ELEP remote 模式但未发现 ElepPrescriptionOperationsRemoteClientImpl，请激活 Maven Profile -Pwith-elep-remote");
        }
        if (!remote && !localImplPresent) {
            log.warn("ELEP embedded 模式但未发现 ElepPrescriptionOrchestrator，请激活 Maven Profile -Pwith-elep");
        }
    }
}
