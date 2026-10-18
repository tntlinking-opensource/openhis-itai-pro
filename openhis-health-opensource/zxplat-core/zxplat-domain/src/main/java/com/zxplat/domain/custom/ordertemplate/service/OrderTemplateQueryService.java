/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.service;

import com.zxplat.domain.custom.ordertemplate.entity.OrderTemplate;
import com.zxplat.domain.custom.ordertemplate.entity.OrderTemplateLine;
import com.zxplat.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.zxplat.domain.custom.ordertemplate.queries.model.OrderTemplateLineQueryModel;
import com.zxplat.domain.custom.ordertemplate.queries.model.OrderTemplateWithLinesQueryModel;
import com.zxplat.domain.custom.ordertemplate.repository.OrderTemplateLineRepository;
import com.zxplat.domain.custom.ordertemplate.repository.OrderTemplateRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.enums.OrderTemplateScene;
import com.zxplat.domain.share.enums.OrderTemplateStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 组套模板读侧查询服务。
 *
 * <p>职责：加载组套模板头行结构、按绑定/场景/状态分页检索、按拥有者与场景列出可用模板（含公共模板并集）。</p>
 *
 * <p>边界：只读；{@link #getOrderTemplateWithLines(Long)} 在模板不存在时抛 {@link BusinessException}。</p>
 * <p>聚合边界：仅本聚合 {@link OrderTemplateRepository} / {@link OrderTemplateLineRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTemplateQueryService {

    private final OrderTemplateRepository orderTemplateRepository;
    private final OrderTemplateLineRepository orderTemplateLineRepository;


    /**
     * 加载组套模板头及全部明细行（行序按 sortOrder、id 升序）。
     *
     * @param orderTemplateId 组套模板主键
     * @return 头行聚合读模型
     * @throws BusinessException 模板不存在
     */
    public OrderTemplateWithLinesQueryModel getOrderTemplateWithLines(Long orderTemplateId) {
        OrderTemplate template = orderTemplateRepository.findById(orderTemplateId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_NOT_FOUND, "组套模板不存在"));
        List<OrderTemplateLine> lines = orderTemplateLineRepository
            .findAllByOrderTemplateIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(orderTemplateId);
        OrderTemplateWithLinesQueryModel model = new OrderTemplateWithLinesQueryModel();
        model.setHeader(toHeaderModel(template));
        model.setLines(toLineModels(lines));
        return model;
    }

    /**
     * 按绑定类型、适用场景、状态、组套种类与模板来源分页查询组套模板头。
     *
     * @param bindingEnum         绑定维度编码
     * @param sceneEnum           适用场景枚举
     * @param statusEnum          模板状态码
     * @param templateKindEnum    组套种类（可空=不过滤）
     * @param templateSourceEnum  模板来源（可空=不过滤）
     * @param pageable            分页参数
     * @return 组套模板头分页模型
     */
    public Page<OrderTemplateHeaderQueryModel> pageOrderTemplates(
        String bindingEnum,
        OrderTemplateScene sceneEnum,
        String statusEnum,
        String templateKindEnum,
        String templateSourceEnum,
        Pageable pageable
    ) {
        String binding = normalizeFilter(bindingEnum);
        String status = normalizeFilter(statusEnum);
        String kind = normalizeFilter(templateKindEnum);
        String source = normalizeFilter(templateSourceEnum);
        // 维护端可选筛选：binding/scene/status 任一为空时走 Specification 动态谓词
        if (binding == null || sceneEnum == null || status == null) {
            Page<OrderTemplate> specPage = orderTemplateRepository.findAll(
                buildPageSpecification(binding, sceneEnum, status, kind, source),
                pageable
            );
            return specPage.map(this::toHeaderModel);
        }
        Page<OrderTemplate> page;
        if (kind == null && source == null) {
            page = orderTemplateRepository.findAllByBindingEnumAndSceneEnumAndStatusEnumOrderBySortWeightAscIdAsc(
                binding, sceneEnum, status, pageable);
        } else if (kind != null && source == null) {
            page = orderTemplateRepository.findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateKindEnumOrderBySortWeightAscIdAsc(
                binding, sceneEnum, status, kind, pageable);
        } else if (kind == null) {
            page = orderTemplateRepository.findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
                binding, sceneEnum, status, source, pageable);
        } else {
            page = orderTemplateRepository.findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
                binding, sceneEnum, status, kind, source, pageable);
        }
        return page.map(this::toHeaderModel);
    }

    /**
     * 维护端分页动态谓词：支持 binding/scene/status 可选过滤，kind/source 精确匹配。
     *
     * @param bindingEnum        绑定类型 code，可空
     * @param sceneEnum          适用场景，可空
     * @param statusEnum         状态 code，可空
     * @param templateKindEnum   组套种类，可空
     * @param templateSourceEnum 模板来源，可空
     * @return JPA Specification
     */
    private Specification<OrderTemplate> buildPageSpecification(
        String bindingEnum,
        OrderTemplateScene sceneEnum,
        String statusEnum,
        String templateKindEnum,
        String templateSourceEnum
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (bindingEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.BINDING_ENUM), bindingEnum));
            }
            if (sceneEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SCENE_ENUM), sceneEnum));
            }
            if (statusEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnum));
            }
            if (templateKindEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.TEMPLATE_KIND_ENUM), templateKindEnum));
            }
            if (templateSourceEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.TEMPLATE_SOURCE_ENUM), templateSourceEnum));
            }
            if (query != null) {
                query.orderBy(
                    cb.asc(root.get(FieldNameConstants.SORT_WEIGHT)),
                    cb.asc(root.get(FieldNameConstants.ID))
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 列出某拥有者在指定场景、组套种类与模板来源下可用的激活模板：包含「公共」场景与目标场景的并集。
     *
     * @param objectId            拥有者主键
     * @param bindingEnum         绑定维度编码
     * @param sceneEnum           当前业务场景（必填）
     * @param templateKindEnum    组套种类（必填）
     * @param templateSourceEnum  模板来源（可空=维护组套与医嘱模板合并展示）
     * @return 组套模板头列表
     * @throws BusinessException 场景或组套种类为空
     */
    public List<OrderTemplateHeaderQueryModel> listOrderTemplatesByOwnerAndScene(
        Long objectId,
        String bindingEnum,
        OrderTemplateScene sceneEnum,
        String templateKindEnum,
        String templateSourceEnum
    ) {
        if (sceneEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "适用场景不能为空");
        }
        if (templateKindEnum == null || templateKindEnum.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "组套种类不能为空");
        }
        String kind = templateKindEnum.trim();
        String source = normalizeFilter(templateSourceEnum);
        List<OrderTemplateScene> scenes = Arrays.asList(OrderTemplateScene.COMMON, sceneEnum);
        List<OrderTemplate> templates;
        if (source == null) {
            templates = orderTemplateRepository
                .findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInAndTemplateKindEnumOrderBySortWeightAscIdAsc(
                    objectId, bindingEnum, OrderTemplateStatus.ACTIVE.getCode(), scenes, kind);
        } else {
            templates = orderTemplateRepository
                .findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(
                    objectId, bindingEnum, OrderTemplateStatus.ACTIVE.getCode(), scenes, kind, source);
        }
        List<OrderTemplateHeaderQueryModel> result = new ArrayList<>();
        for (OrderTemplate template : templates) {
            result.add(toHeaderModel(template));
        }
        return result;
    }

    /**
     * 列表筛选值归一：空白 → null，否则 trim。
     *
     * @param value 原始筛选值
     * @return 归一结果
     */
    private String normalizeFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 组套模板头实体映射为读模型。
     */
    private OrderTemplateHeaderQueryModel toHeaderModel(OrderTemplate template) {
        OrderTemplateHeaderQueryModel model = new OrderTemplateHeaderQueryModel();
        model.setId(template.getId());
        model.setObjectId(template.getObjectId());
        model.setBindingEnum(template.getBindingEnum());
        model.setSceneEnum(template.getSceneEnum());
        model.setTemplateKindEnum(template.getTemplateKindEnum());
        model.setTemplateSourceEnum(template.getTemplateSourceEnum());
        model.setTemplateCode(template.getTemplateCode());
        model.setName(template.getName());
        model.setDescription(template.getDescription());
        model.setSortWeight(template.getSortWeight());
        model.setStatusEnum(template.getStatusEnum());
        model.setRemark(template.getRemark());
        model.setDefaultChineseHerbsDoseQuantity(template.getDefaultChineseHerbsDoseQuantity());
        model.setDefaultSufferingFlag(template.getDefaultSufferingFlag());
        return model;
    }

    /**
     * 批量映射组套模板明细行。
     */
    private List<OrderTemplateLineQueryModel> toLineModels(List<OrderTemplateLine> lines) {
        List<OrderTemplateLineQueryModel> result = new ArrayList<>();
        for (OrderTemplateLine line : lines) {
            result.add(toLineModel(line));
        }
        return result;
    }

    /**
     * 单条组套模板明细映射为读模型。
     */
    private OrderTemplateLineQueryModel toLineModel(OrderTemplateLine line) {
        OrderTemplateLineQueryModel model = new OrderTemplateLineQueryModel();
        model.setId(line.getId());
        model.setOrderTemplateId(line.getOrderTemplateId());
        model.setLineKindEnum(line.getLineKindEnum());
        model.setMedicationDefinitionId(line.getMedicationDefinitionId());
        model.setActivityDefinitionId(line.getActivityDefinitionId());
        model.setDeviceDefinitionId(line.getDeviceDefinitionId());
        model.setGroupKey(line.getGroupKey());
        model.setSortOrder(line.getSortOrder());
        model.setDefaultQuantity(line.getDefaultQuantity());
        model.setDefaultUnitCode(line.getDefaultUnitCode());
        model.setDefaultDays(line.getDefaultDays());
        model.setFrequencyId(line.getFrequencyId());
        model.setRouteCode(line.getRouteCode());
        model.setDosageText(line.getDosageText());
        model.setDefaultPerformOrgId(line.getDefaultPerformOrgId());
        model.setLineRemark(line.getLineRemark());
        model.setChineseHerbsDoseQuantity(line.getChineseHerbsDoseQuantity());
        model.setSufferingFlag(line.getSufferingFlag());
        return model;
    }
}
