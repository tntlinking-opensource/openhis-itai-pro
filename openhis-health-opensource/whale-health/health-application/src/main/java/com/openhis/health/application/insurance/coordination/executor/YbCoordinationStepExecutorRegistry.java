/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医保协同步骤执行器注册表：Spring 注入全部 {@link YbCoordinationStepExecutor} 实现并按 biz_type 索引。
 */
@Component
public class YbCoordinationStepExecutorRegistry {

    /** biz_type → 步骤执行器不可变映射。 */
    private final Map<YbCoordinationBizType, YbCoordinationStepExecutor> executorMap;

    /**
     * 构造注册表，忽略 bizType 为 null 的执行器 bean。
     *
     * @param executors Spring 容器中的全部步骤执行器实现
     */
    public YbCoordinationStepExecutorRegistry(List<YbCoordinationStepExecutor> executors) {
        Map<YbCoordinationBizType, YbCoordinationStepExecutor> map = new HashMap<>();
        if (executors != null) {
            for (YbCoordinationStepExecutor executor : executors) {
                if (executor != null && executor.bizType() != null) {
                    map.put(executor.bizType(), executor);
                }
            }
        }
        this.executorMap = map;
    }

    /**
     * 按业务类型获取步骤执行器。
     *
     * @param bizType 协同单业务类型
     * @return 对应执行器；bizType 为 null 或未注册时返回 null
     */
    public YbCoordinationStepExecutor get(YbCoordinationBizType bizType) {
        if (bizType == null) {
            return null;
        }
        return executorMap.get(bizType);
    }
}
