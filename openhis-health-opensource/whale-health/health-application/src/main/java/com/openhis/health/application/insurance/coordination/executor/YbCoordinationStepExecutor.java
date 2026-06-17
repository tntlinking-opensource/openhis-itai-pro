/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.executor;

import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;

/**
 * 医保协同单步骤执行器契约（按 {@link YbCoordinationBizType} 注册到 {@link YbCoordinationStepExecutorRegistry}）。
 *
 * <p>实现类负责推进平台 infno 步骤、本地落账与失败重试策略。</p>
 */
public interface YbCoordinationStepExecutor {

    /**
     * 本执行器处理的协同单业务类型。
     *
     * @return 业务类型枚举
     */
    YbCoordinationBizType bizType();

    /**
     * 派发并推进协同单下一步（平台调用或本地收口）。
     *
     * @param coordination 已锁定的协同单聚合（含步骤列表）
     */
    void dispatch(YbCoordination coordination);
}
