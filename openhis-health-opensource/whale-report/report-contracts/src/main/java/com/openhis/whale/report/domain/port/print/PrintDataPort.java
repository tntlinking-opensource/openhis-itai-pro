package com.openhis.whale.report.domain.port.print;

import java.util.Map;

/**
 * B 类单证打印数据集 Port（由 health 侧实现，按 {@code packCode} 加载 bindPath 根 Map）。
 *
 * <p>{@code packCode} 与 {@link com.openhis.whale.report.domain.constants.ReportPackConstants} 对齐；
 * 不支持该 pack 时返回空 Map。</p>
 */
public interface PrintDataPort {

    /**
     * 加载打印数据集。
     *
     * @param packCode 元素包 / 数据集编码
     * @param params   业务参数（各 pack 约定见 adapter 路由）
     */
    Map<String, Object> load(String packCode, Map<String, Object> params);
}
