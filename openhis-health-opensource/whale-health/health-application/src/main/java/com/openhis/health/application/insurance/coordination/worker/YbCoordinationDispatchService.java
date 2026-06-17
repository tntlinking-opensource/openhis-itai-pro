/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.coordination.worker;

import com.openhis.health.application.insurance.coordination.executor.YbCoordinationStepExecutor;
import com.openhis.health.application.insurance.coordination.executor.YbCoordinationStepExecutorRegistry;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationQueryService;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医保协同单派发服务：按 {@link YbCoordinationBizType} 路由至对应步骤执行器。
 *
 * <p>派发在事务边界内重新加载协同单聚合，确保状态与步骤表一致后再调用执行器。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YbCoordinationDispatchService {

    /** 协同单读侧。 */
    private final YbCoordinationQueryService coordinationQueryService;

    /** 协同单写侧（requireById 带乐观锁）。 */
    private final YbCoordinationDomainService coordinationDomainService;

    /** biz_type → 步骤执行器注册表。 */
    private final YbCoordinationStepExecutorRegistry executorRegistry;

    /**
     * 按主键派发协同单。
     *
     * @param coordinationId 协同单主键；为 null 或不存在时静默返回
     */
    @Transactional
    public void dispatchById(Long coordinationId) {
        if (coordinationId == null) {
            return;
        }
        // Pre：加载协同单读模型；不存在则无需派发。
        YbCoordination coordination = coordinationQueryService.getById(coordinationId);
        if (coordination == null) {
            return;
        }
        this.dispatch(coordination);
    }

    /**
     * 派发已加载的协同单实体。
     *
     * @param coordination 协同单（至少含 id 与 biz_type）；为 null 或无执行器时静默返回
     */
    @Transactional
    public void dispatch(YbCoordination coordination) {
        if (coordination == null) {
            return;
        }
        // Pre：解析业务类型并查找注册的执行器。
        YbCoordinationBizType bizType = coordination.bizTypeEnum();
        if (bizType == null) {
            return;
        }
        YbCoordinationStepExecutor executor = executorRegistry.get(bizType);
        if (executor == null) {
            log.debug("no executor for bizType={}", bizType);
            return;
        }
        try {
            // Cmd：在写侧重新锁定聚合后委派步骤执行器推进平台/本地步骤。
            executor.dispatch(coordinationDomainService.requireById(coordination.getId()));
        } catch (Exception ex) {
            log.error("coordination dispatch failed id={}", coordination.getId(), ex);
        }
    }
}
