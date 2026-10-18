/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.starter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * 启动时校验扩展模块 classpath 与 {@code zxplat.modules.*.enabled} 是否一致。
 *
 * <p>方案 A（Maven 为真源）：未配置 enabled 时跟随 classpath；仅显式 {@code true}/{@code false} 覆盖。
 * yml 一般不必再写一串 {@code enabled: true}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionModuleProfileValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final String[][] MODULE_CHECKS = {
            {"emr", "com.zxplat.plugin.emr.application.config.EmrAutoConfiguration", "zxplat.modules.emr.enabled"},
            {"chs", "com.zxplat.plugin.chs.starter.ChsJilinModuleAutoConfiguration", "zxplat.modules.chs.enabled"},
            {"elep", "com.zxplat.plugin.elep.application.config.ElepAutoConfiguration", "zxplat.modules.elep.enabled"},
            {"booking", "com.zxplat.plugin.booking.application.config.BookingAutoConfiguration", "zxplat.modules.booking.enabled"},
            {"campus", "com.zxplat.plugin.campus.application.config.CampusAutoConfiguration", "zxplat.modules.campus.enabled"},
            {"miniapp", "com.zxplat.plugin.miniapp.application.config.MiniappAutoConfiguration", "zxplat.modules.miniapp.enabled"},
            {"einvoice-boss", "com.zxplat.plugin.einvoice.boss.application.config.EinvoiceBossAutoConfiguration", "zxplat.modules.einvoice-boss.enabled"},
            {"unionpay-bpc", "com.zxplat.plugin.unionpay.bpc.application.config.UnionPayBpcAutoConfiguration", "zxplat.modules.unionpay-bpc.enabled"},
            {"peis", "com.zxplat.plugin.peis.application.config.PeisAutoConfiguration", "zxplat.modules.peis.enabled"},
            {"pacs", "com.zxplat.plugin.pacs.application.config.PacsAutoConfiguration", "zxplat.modules.pacs.enabled"},
            {"lis", "com.zxplat.plugin.lis.application.config.LisAutoConfiguration", "zxplat.modules.lis.enabled"},
    };

    private final Environment environment;

    /**
     * 启动时校验扩展模块 classpath 与 enabled 显式配置是否冲突。
     *
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ClassLoader classLoader = event.getApplicationContext().getClassLoader();
        // 遍历内置模块：仅对「显式配置与 classpath 冲突」打 warn
        for (String[] check : MODULE_CHECKS) {
            String moduleId = check[0];
            String configClassName = check[1];
            String enabledProperty = check[2];
            boolean onClasspath = ClassUtils.isPresent(configClassName, classLoader);
            Boolean configured = environment.getProperty(enabledProperty, Boolean.class);
            // 已在 classpath 但显式关闭
            if (onClasspath && Boolean.FALSE.equals(configured)) {
                log.warn(
                        "扩展模块 {} 已在 classpath（{}）但 {}=false，AutoConfiguration 不会生效；"
                                + "若要启用请删除该配置或设为 true；若故意关闭可忽略",
                        moduleId,
                        configClassName,
                        enabledProperty);
            }
            // 显式启用但 classpath 缺实现：通常是 Maven Profile 或依赖未挂载
            if (!onClasspath && Boolean.TRUE.equals(configured)) {
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
                "com.zxplat.plugin.chs.bridge.config.ChsBridgeAutoConfiguration",
                "zxplat.modules.chs.enabled");
        validateOptionalModule(
                classLoader,
                "elep-jilin-adapter",
                "com.zxplat.plugin.elep.bridge.config.ElepJilinBridgeAutoConfiguration",
                "zxplat.modules.elep.enabled");
        validateOptionalModule(
                classLoader,
                "unionpay-bpc-adapter",
                "com.zxplat.plugin.unionpay.bpc.bridge.config.UnionPayBpcBridgeAutoConfiguration",
                "zxplat.modules.unionpay-bpc.enabled");
        validateChsGatewayMode(classLoader);
        validateElepDeploymentMode(classLoader);
    }

    /**
     * 校验可选扩展模块（如 module-chs-jilin-bridge）的 classpath 与显式开关是否冲突。
     *
     * @param classLoader 应用 ClassLoader
     * @param moduleId 模块标识，用于日志
     * @param configClassName AutoConfiguration 全类名
     * @param enabledProperty 对应 enabled 配置键
     */
    private void validateOptionalModule(
            ClassLoader classLoader, String moduleId, String configClassName, String enabledProperty) {
        boolean onClasspath = ClassUtils.isPresent(configClassName, classLoader);
        Boolean configured = environment.getProperty(enabledProperty, Boolean.class);
        if (onClasspath && Boolean.FALSE.equals(configured)) {
            log.warn(
                    "扩展模块 {} 已在 classpath（{}）但 {}=false",
                    moduleId,
                    configClassName,
                    enabledProperty);
        }
        if (!onClasspath && Boolean.TRUE.equals(configured)) {
            log.warn(
                    "扩展模块 {} 配置 {}=true 但未发现 {}，请检查 Maven Profile / 依赖",
                    moduleId,
                    enabledProperty,
                    configClassName);
        }
    }

    /**
     * 模块是否有效启用：未配置跟随 classpath；显式 true/false 覆盖。
     *
     * @param enabledProperty 配置键
     * @param onClasspath 对应 AutoConfiguration 是否在 classpath
     * @return 有效启用时 true
     */
    private boolean isEffectivelyEnabled(String enabledProperty, boolean onClasspath) {
        Boolean configured = environment.getProperty(enabledProperty, Boolean.class);
        if (configured == null) {
            return onClasspath;
        }
        return configured.booleanValue();
    }

    /**
     * 校验 CHS 部署模式（embedded/remote）与 Gateway 实现类 classpath 是否匹配。
     *
     * @param classLoader 应用 ClassLoader
     */
    private void validateChsGatewayMode(ClassLoader classLoader) {
        boolean chsOnClasspath = ClassUtils.isPresent(
                "com.zxplat.plugin.chs.starter.ChsJilinModuleAutoConfiguration", classLoader);
        if (!isEffectivelyEnabled("zxplat.modules.chs.enabled", chsOnClasspath)) {
            return;
        }
        String deploymentMode = environment.getProperty("zxplat.chs.deployment.mode", "embedded");
        boolean remote = "remote".equalsIgnoreCase(deploymentMode);
        boolean localImplPresent = ClassUtils.isPresent(
                "com.zxplat.plugin.chs.gateway.local.InsuranceGatewayClientLocalImpl", classLoader);
        boolean remoteImplPresent = ClassUtils.isPresent(
                "com.zxplat.plugin.chs.host.remote.InsuranceGatewayClientRemoteImpl", classLoader);
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
        boolean elepOnClasspath = ClassUtils.isPresent(
                "com.zxplat.plugin.elep.application.config.ElepAutoConfiguration", classLoader);
        if (!isEffectivelyEnabled("zxplat.modules.elep.enabled", elepOnClasspath)) {
            return;
        }
        String deploymentMode = environment.getProperty("zxplat.elep.deployment.mode", "embedded");
        boolean remote = "remote".equalsIgnoreCase(deploymentMode);
        boolean localImplPresent = ClassUtils.isPresent(
                "com.zxplat.plugin.elep.application.prescription.workflow.ElepPrescriptionOrchestrator", classLoader);
        boolean remoteImplPresent = ClassUtils.isPresent(
                "com.zxplat.plugin.elep.host.client.ElepPrescriptionOperationsRemoteClientImpl",
                classLoader);
        if (remote && !remoteImplPresent) {
            log.warn("ELEP remote 模式但未发现 ElepPrescriptionOperationsRemoteClientImpl，请激活 Maven Profile -Pwith-elep-remote");
        }
        if (!remote && !localImplPresent) {
            log.warn("ELEP embedded 模式但未发现 ElepPrescriptionOrchestrator，请激活 Maven Profile -Pwith-elep");
        }
    }
}
