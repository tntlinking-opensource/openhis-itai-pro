package com.openhis.whale.report.adapter.module;



import com.openhis.whale.core.module.IntegrationMode;

import com.openhis.whale.core.module.WhaleModuleDescriptor;

import org.springframework.stereotype.Component;



/**

 * report-adapter 模块元数据（提供 ReportTabularDatasetLoader 等 契约实现）。

 */

@Component

public class ReportAdapterModuleDescriptor implements WhaleModuleDescriptor {



    /**

     * 模块唯一标识。

     *

     * @return {@code report-adapter}

     */

    @Override

    public String moduleId() {

        return "report-adapter";

    }



    /**

     * 模块展示名称。

     *

     * @return 英文显示名

     */

    @Override

    public String displayName() {

        return "HIS-Report Adapter";

    }



    /**

     * Spring 配置项：是否启用报表模块（与 application 共用开关）。

     *

     * @return {@code openhis.modules.report.enabled}

     */

    @Override

    public String enabledProperty() {

        return "openhis.modules.report.enabled";

    }



    /**

     * 依赖的 sqlreport 域模块（Port 契约定义）。

     *

     * @return 依赖模块 id 数组

     */

    @Override

    public String[] dependsOnModuleIds() {

        return new String[] {"report"};

    }



    /**

     * 以 Port 方式向 application 提供 HIS 数据集。

     *

     * @return Port 模式

     */

    @Override

    public IntegrationMode integrationMode() {

        return IntegrationMode.PORT;

    }

}

