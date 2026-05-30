/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.ordertemplate.service;

import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplate;
import com.openhis.health.domain.custom.ordertemplate.entity.OrderTemplateLine;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateLineQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateWithLinesQueryModel;
import com.openhis.health.domain.custom.ordertemplate.repository.OrderTemplateLineRepository;
import com.openhis.health.domain.custom.ordertemplate.repository.OrderTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.health.domain.share.enums.OrderTemplateStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 医嘱组套模板读侧查询服务。
 *
 * <p>职责：加载组套头行结构、按绑定/场景/状态分页检索、按拥有者与场景列出可用模板（含公共模板并集）。</p>
 *
 * <p>边界：只读；{@link #getOrderTemplateWithLines(Long)} 在模板不存在时抛 {@link BusinessException}。</p>
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
        List<OrderTemplateLine> lines = orderTemplateLineRepository.findAllByOrderTemplateIdOrderBySortOrderAscIdAsc(orderTemplateId);
        OrderTemplateWithLinesQueryModel model = new OrderTemplateWithLinesQueryModel();
        model.setHeader(toHeaderModel(template));
        model.setLines(toLineModels(lines));
        return model;
    }

    /**
     * 按绑定类型、适用场景与状态分页查询组套头。
     *
     * @param bindingEnum 绑定维度编码
     * @param sceneEnum   适用场景枚举
     * @param statusEnum  模板状态码
     * @param pageable    分页参数
     * @return 组套头分页模型
     */
    public Page<OrderTemplateHeaderQueryModel> pageOrderTemplates(String bindingEnum, OrderTemplateScene sceneEnum, String statusEnum, Pageable pageable) {
        Page<OrderTemplate> page = orderTemplateRepository.findAllByBindingEnumAndSceneEnumAndStatusEnumOrderBySortWeightAscIdAsc(
            bindingEnum,
            sceneEnum,
            statusEnum,
            pageable
        );
        return page.map(this::toHeaderModel);
    }

    /**
     * 列出某拥有者在指定场景下可用的激活模板：包含「公共」场景与目标场景的并集。
     *
     * @param objectId    拥有者主键
     * @param bindingEnum 绑定维度编码
     * @param sceneEnum   当前业务场景（必填）
     * @return 组套头列表
     * @throws BusinessException 场景为空
     */
    public List<OrderTemplateHeaderQueryModel> listOrderTemplatesByOwnerAndScene(Long objectId, String bindingEnum, OrderTemplateScene sceneEnum) {
        if (sceneEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ORDER_TEMPLATE_INVALID_ARGUMENT, "适用场景不能为空");
        }
        List<OrderTemplate> templates = orderTemplateRepository.findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInOrderBySortWeightAscIdAsc(
            objectId,
            bindingEnum,
            OrderTemplateStatus.ACTIVE.getCode(),
            Arrays.asList(OrderTemplateScene.COMMON, sceneEnum)
        );
        List<OrderTemplateHeaderQueryModel> result = new ArrayList<>();
        for (OrderTemplate template : templates) {
            result.add(toHeaderModel(template));
        }
        return result;
    }

    /**
     * 组套头实体映射为读模型。
     */
    private OrderTemplateHeaderQueryModel toHeaderModel(OrderTemplate template) {
        OrderTemplateHeaderQueryModel model = new OrderTemplateHeaderQueryModel();
        model.setId(template.getId());
        model.setObjectId(template.getObjectId());
        model.setBindingEnum(template.getBindingEnum());
        model.setSceneEnum(template.getSceneEnum());
        model.setTemplateCode(template.getTemplateCode());
        model.setName(template.getName());
        model.setDescription(template.getDescription());
        model.setSortWeight(template.getSortWeight());
        model.setStatusEnum(template.getStatusEnum());
        model.setRemark(template.getRemark());
        return model;
    }

    /**
     * 批量映射组套明细行。
     */
    private List<OrderTemplateLineQueryModel> toLineModels(List<OrderTemplateLine> lines) {
        List<OrderTemplateLineQueryModel> result = new ArrayList<>();
        for (OrderTemplateLine line : lines) {
            result.add(toLineModel(line));
        }
        return result;
    }

    /**
     * 单条组套明细映射为读模型。
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
        return model;
    }
}
