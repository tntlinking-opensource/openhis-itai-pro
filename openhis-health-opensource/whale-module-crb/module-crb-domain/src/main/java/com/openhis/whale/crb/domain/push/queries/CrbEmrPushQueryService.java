/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push.queries;

import com.openhis.whale.crb.domain.push.CrbEmrPushLog;
import com.openhis.whale.crb.domain.push.CrbEmrPushLogRepository;
import com.openhis.whale.crb.domain.push.CrbEmrPushTask;
import com.openhis.whale.crb.domain.push.CrbEmrPushTaskRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 推送队列读侧（监控分页 / 任务日志；DM-CRB-PUSH-001 §6.2）。
 */
@Service
@RequiredArgsConstructor
public class CrbEmrPushQueryService {

    private final CrbEmrPushTaskRepository taskRepository;
    private final CrbEmrPushLogRepository logRepository;

    /**
     * 分页查询推送任务。
     *
     * @param tenantId 租户 id
     * @param status 可选状态 code；空则不过滤状态
     * @param pageable 分页
     * @return 任务页
     */
    public Page<CrbEmrPushTask> pageTasks(Long tenantId, String status, Pageable pageable) {
        // 租户缺失则无法界定监控范围
        if (tenantId == null) {
            return Page.empty(pageable);
        }
        Specification<CrbEmrPushTask> spec = buildTaskSpec(tenantId, status);
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 按任务查询推送日志（未删）。
     *
     * @param tenantId 租户 id
     * @param taskId 任务 id
     * @return 日志列表
     */
    public List<CrbEmrPushLog> listLogsByTask(Long tenantId, Long taskId) {
        // 租户或任务缺失则返回空列表
        if (tenantId == null || taskId == null) {
            return List.of();
        }
        return logRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("tenantId"), tenantId),
                        cb.equal(root.get("taskId"), taskId),
                        cb.equal(root.get("isDeleted"), false)));
    }

    /**
     * 构建任务分页 Specification。
     *
     * @param tenantId 租户
     * @param status 可选状态
     * @return Specification
     */
    private Specification<CrbEmrPushTask> buildTaskSpec(Long tenantId, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            String statusCode = trimToNull(status);
            // 传入 status 则追加等值条件
            if (statusCode != null) {
                predicates.add(cb.equal(root.get("status"), statusCode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
