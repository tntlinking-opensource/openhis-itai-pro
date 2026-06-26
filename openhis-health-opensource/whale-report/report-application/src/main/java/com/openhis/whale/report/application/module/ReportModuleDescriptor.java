package com.openhis.whale.report.application.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * Whale Report 模块元数据（依赖 report-adapter 契约实现）。
 */
@Component
public class ReportModuleDescriptor implements WhaleModuleDescriptor {

    /**
     * 模块唯一标识。
     *
     * @return {@code report}
     */
    @Override
    public String moduleId() {
        return "report";
    }

    /**
     * 模块展示名称。
     *
     * @return 英文显示名
     */
    @Override
    public String displayName() {
        return "Whale Report";
    }

    /**
     * Spring 配置项：是否启用本模块。
     *
     * @return {@code openhis.modules.report.enabled}
     */
    @Override
    public String enabledProperty() {
        return "openhis.modules.report.enabled";
    }

    /**
     * 运行时依赖的 契约实现模块。
     *
     * @return 依赖模块 id 数组
     */
    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"report-adapter"};
    }

    /**
     * 与 HIS 的集成方式。
     *
     * @return Port 模式
     */
    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
