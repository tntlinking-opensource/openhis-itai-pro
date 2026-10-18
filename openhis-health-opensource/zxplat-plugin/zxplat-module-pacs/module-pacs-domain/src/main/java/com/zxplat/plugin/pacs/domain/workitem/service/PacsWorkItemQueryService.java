/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.service;

import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItem;
import com.zxplat.plugin.pacs.domain.workitem.entity.PacsWorkItemOrder;
import com.zxplat.plugin.pacs.domain.workitem.queries.PacsWorkItemSearchQuery;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemDetailQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemOrderQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.queries.model.PacsWorkItemQueryModel;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsWorkItemOrderRepository;
import com.zxplat.plugin.pacs.domain.workitem.repository.PacsWorkItemRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.SpecificationUtils;
import com.zxplat.framework.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 检查工单读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class PacsWorkItemQueryService {

    private final PacsWorkItemRepository workItemRepository;
    private final PacsWorkItemOrderRepository orderRepository;

    /**
     * 待登记确认列表（status=registered）。
     *
     * @param query 检索条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PacsWorkItemQueryModel> searchPendingRegistration(PacsWorkItemSearchQuery query, Pageable pageable) {
        // 构建查询条件（租户 + 未软删 + 业务过滤）
        Specification<PacsWorkItem> specification = this.buildBaseSpecification(query)
            .and((root, cq, cb) -> cb.equal(root.get("statusEnum"), PacsWorkItemStatus.REGISTERED.getCode()));
        // 单次 SQL/仓储调用并完成分页统计
        Page<PacsWorkItem> page = workItemRepository.findAll(specification, this.resolvePageable(pageable));
        // 组装 QueryModel（无写库、无 PHI 列）
        return this.toQueryModelPage(page);
    }

    /**
     * 待执行列表（scheduled；兼容 registered 且已有检查号）。
     *
     * @param query 检索条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PacsWorkItemQueryModel> searchPendingExecution(PacsWorkItemSearchQuery query, Pageable pageable) {
        Specification<PacsWorkItem> base = this.buildBaseSpecification(query);
        Specification<PacsWorkItem> executionSpec = base.and((root, cq, cb) -> {
            Predicate scheduled = cb.equal(root.get("statusEnum"), PacsWorkItemStatus.SCHEDULED.getCode());
            Predicate registered = cb.equal(root.get("statusEnum"), PacsWorkItemStatus.REGISTERED.getCode());
            Predicate accessionPresent = cb.and(
                cb.isNotNull(root.get("accessionNumber")),
                cb.notEqual(root.get("accessionNumber"), ""));
            Predicate registeredWithAccession = cb.and(registered, accessionPresent);
            return cb.or(scheduled, registeredWithAccession);
        });
        Page<PacsWorkItem> page = workItemRepository.findAll(executionSpec, this.resolvePageable(pageable));
        return this.toQueryModelPage(page);
    }

    /**
     * 已检列表（status=completed）。
     *
     * @param query 检索条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PacsWorkItemQueryModel> searchCompleted(PacsWorkItemSearchQuery query, Pageable pageable) {
        Specification<PacsWorkItem> specification = this.buildBaseSpecification(query)
            .and((root, cq, cb) -> cb.equal(root.get("statusEnum"), PacsWorkItemStatus.COMPLETED.getCode()));
        Page<PacsWorkItem> page = workItemRepository.findAll(specification, this.resolvePageable(pageable));
        return this.toQueryModelPage(page);
    }

    /**
     * 他科已检列表（completed 且科室不等于 excludeOrganizationId）。
     *
     * @param query 检索条件（须含 excludeOrganizationId）
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PacsWorkItemQueryModel> searchCompletedOtherDept(
        PacsWorkItemSearchQuery query, Pageable pageable) {
        if (query == null || query.getExcludeOrganizationId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "排除科室不能为空");
        }
        Long excludeOrganizationId = query.getExcludeOrganizationId();
        Specification<PacsWorkItem> specification = this.buildBaseSpecification(query)
            .and((root, cq, cb) -> cb.equal(root.get("statusEnum"), PacsWorkItemStatus.COMPLETED.getCode()))
            .and((root, cq, cb) -> cb.or(
                cb.isNull(root.get("organizationId")),
                cb.notEqual(root.get("organizationId"), excludeOrganizationId)));
        Page<PacsWorkItem> page = workItemRepository.findAll(specification, this.resolvePageable(pageable));
        return this.toQueryModelPage(page);
    }

    /**
     * 已作废列表（status=entered-in-error）。
     *
     * @param query 检索条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PacsWorkItemQueryModel> searchEnteredInError(
        PacsWorkItemSearchQuery query, Pageable pageable) {
        Specification<PacsWorkItem> specification = this.buildBaseSpecification(query)
            .and((root, cq, cb) -> cb.equal(
                root.get("statusEnum"), PacsWorkItemStatus.ENTERED_IN_ERROR.getCode()));
        Page<PacsWorkItem> page = workItemRepository.findAll(specification, this.resolvePageable(pageable));
        return this.toQueryModelPage(page);
    }

    /**
     * 工单详情（含 orderLinks）。
     *
     * @param workItemId 工单主键
     * @return 详情读模型
     */
    public PacsWorkItemDetailQueryModel getDetail(Long workItemId) {
        PacsWorkItem workItem = workItemRepository.findById(workItemId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "检查工单不存在"));
        List<PacsWorkItemOrder> orders = orderRepository.findByWorkItemId(workItemId);
        return this.toDetailModel(workItem, orders);
    }

    /**
     * 按 ServiceRequest 反查工单详情。
     *
     * @param serviceRequestId ServiceRequest 主键
     * @return 详情读模型
     */
    public PacsWorkItemDetailQueryModel findByServiceRequestId(Long serviceRequestId) {
        return this.findOptionalByServiceRequestId(serviceRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_NOT_FOUND, "未找到关联检查工单"));
    }

    /**
     * 按 ServiceRequest 反查工单详情（无关联时 empty）。
     *
     * @param serviceRequestId ServiceRequest 主键
     * @return 可选详情
     */
    public Optional<PacsWorkItemDetailQueryModel> findOptionalByServiceRequestId(Long serviceRequestId) {
        if (serviceRequestId == null) {
            return Optional.empty();
        }
        Optional<PacsWorkItemOrder> orderOptional = orderRepository.findByServiceRequestId(serviceRequestId);
        if (orderOptional.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.getDetail(orderOptional.get().getWorkItemId()));
    }

    /**
     * 按检查号反查工单详情（C-STORE 回写前定位 workItemId）。
     *
     * @param accessionNumber 检查号
     * @param tenantId 租户主键
     * @return 可选详情
     */
    public Optional<PacsWorkItemDetailQueryModel> findByAccessionNumber(String accessionNumber, Long tenantId) {
        if (StringUtils.isEmpty(accessionNumber) || tenantId == null) {
            return Optional.empty();
        }
        Optional<PacsWorkItem> workItemOptional =
            workItemRepository.findByAccessionNumberAndTenantId(accessionNumber.trim(), tenantId);
        if (workItemOptional.isEmpty()) {
            return Optional.empty();
        }
        PacsWorkItem workItem = workItemOptional.get();
        List<PacsWorkItemOrder> orders = orderRepository.findByWorkItemId(workItem.getId());
        return Optional.of(this.toDetailModel(workItem, orders));
    }

    /**
     * 列表公共条件：租户 + 未软删 + 业务过滤。
     *
     * @param query 检索条件
     * @return 规格
     */
    private Specification<PacsWorkItem> buildBaseSpecification(PacsWorkItemSearchQuery query) {
        Specification<PacsWorkItem> businessSpec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                if (query.getOrganizationId() != null) {
                    predicates.add(cb.equal(root.get("organizationId"), query.getOrganizationId()));
                }
                if (!StringUtils.isEmpty(query.getModalityCode())) {
                    predicates.add(cb.equal(root.get("modalityCode"), query.getModalityCode()));
                }
                if (query.getDateFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.getDateFrom()));
                }
                if (query.getDateTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.getDateTo()));
                }
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // 租户由当前上下文隔离；软删统一走 SpecificationUtils.notDeleted
        return SpecificationUtils.<PacsWorkItem>tenantFilter()
            .and(SpecificationUtils.notDeleted())
            .and(businessSpec);
    }

    private Pageable resolvePageable(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Page<PacsWorkItemQueryModel> toQueryModelPage(Page<PacsWorkItem> page) {
        List<PacsWorkItem> content = page.getContent();
        Map<Long, Integer> orderCountByWorkItemId = this.loadOrderCounts(content);
        List<PacsWorkItemQueryModel> models = new ArrayList<>();
        for (PacsWorkItem workItem : content) {
            Integer orderCount = orderCountByWorkItemId.get(workItem.getId());
            if (orderCount == null) {
                orderCount = 0;
            }
            models.add(this.toQueryModel(workItem, orderCount));
        }
        return new PageImpl<>(models, page.getPageable(), page.getTotalElements());
    }

    /**
     * 批量统计工单医嘱挂接数（一次 IN 查询，内存计数，禁 N+1）。
     *
     * @param workItems 当前页工单
     * @return workItemId → orderCount
     */
    private Map<Long, Integer> loadOrderCounts(List<PacsWorkItem> workItems) {
        Map<Long, Integer> counts = new HashMap<>();
        if (workItems == null || workItems.isEmpty()) {
            return counts;
        }
        List<Long> workItemIds = new ArrayList<>();
        for (PacsWorkItem workItem : workItems) {
            if (workItem != null && workItem.getId() != null) {
                workItemIds.add(workItem.getId());
                counts.put(workItem.getId(), 0);
            }
        }
        if (workItemIds.isEmpty()) {
            return counts;
        }
        List<PacsWorkItemOrder> orders = orderRepository.findByWorkItemIdIn(workItemIds);
        for (PacsWorkItemOrder order : orders) {
            if (order == null || order.getWorkItemId() == null) {
                continue;
            }
            Integer current = counts.get(order.getWorkItemId());
            if (current == null) {
                current = 0;
            }
            counts.put(order.getWorkItemId(), current + 1);
        }
        return counts;
    }

    private PacsWorkItemQueryModel toQueryModel(PacsWorkItem workItem, Integer orderCount) {
        PacsWorkItemQueryModel model = new PacsWorkItemQueryModel();
        model.setId(workItem.getId());
        model.setAccessionNumber(workItem.getAccessionNumber());
        model.setPatientId(workItem.getPatientId());
        model.setEncounterId(workItem.getEncounterId());
        model.setOrganizationId(workItem.getOrganizationId());
        model.setStatusEnum(workItem.getStatusEnum());
        model.setModalityCode(workItem.getModalityCode());
        model.setBodySiteCode(workItem.getBodySiteCode());
        model.setDeviceId(workItem.getDeviceId());
        model.setPerformerPractitionerId(workItem.getPerformerPractitionerId());
        model.setScheduledTime(workItem.getScheduledTime());
        model.setPerformedTime(workItem.getPerformedTime());
        model.setHasMedia(workItem.getHasMedia());
        model.setOrderCount(orderCount);
        model.setCreatedAt(workItem.getCreatedAt());
        return model;
    }

    private PacsWorkItemDetailQueryModel toDetailModel(PacsWorkItem workItem, List<PacsWorkItemOrder> orders) {
        PacsWorkItemDetailQueryModel model = new PacsWorkItemDetailQueryModel();
        model.setId(workItem.getId());
        model.setAccessionNumber(workItem.getAccessionNumber());
        model.setPatientId(workItem.getPatientId());
        model.setEncounterId(workItem.getEncounterId());
        model.setOrganizationId(workItem.getOrganizationId());
        model.setStatusEnum(workItem.getStatusEnum());
        model.setModalityCode(workItem.getModalityCode());
        model.setBodySiteCode(workItem.getBodySiteCode());
        model.setDeviceId(workItem.getDeviceId());
        model.setPerformerPractitionerId(workItem.getPerformerPractitionerId());
        model.setScheduledTime(workItem.getScheduledTime());
        model.setPerformedTime(workItem.getPerformedTime());
        model.setHasMedia(workItem.getHasMedia());
        model.setOrderCount(orders.size());
        model.setCreatedAt(workItem.getCreatedAt());
        List<PacsWorkItemOrderQueryModel> orderModels = new ArrayList<>();
        for (PacsWorkItemOrder order : orders) {
            orderModels.add(this.toOrderModel(order));
        }
        model.setOrderLinks(orderModels);
        return model;
    }

    private PacsWorkItemOrderQueryModel toOrderModel(PacsWorkItemOrder order) {
        PacsWorkItemOrderQueryModel model = new PacsWorkItemOrderQueryModel();
        model.setId(order.getId());
        model.setWorkItemId(order.getWorkItemId());
        model.setServiceRequestId(order.getServiceRequestId());
        model.setActivityId(order.getActivityId());
        model.setLinkStatusEnum(order.getLinkStatusEnum());
        model.setGroupCode(order.getGroupCode());
        model.setSortNumber(order.getSortNumber());
        return model;
    }
}
