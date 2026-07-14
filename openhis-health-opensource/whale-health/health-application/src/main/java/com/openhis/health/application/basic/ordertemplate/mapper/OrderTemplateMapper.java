/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.mapper;

import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateBindingGroupDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateHeaderRowDto;
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineEditDto;
import com.openhis.health.application.basic.ordertemplate.request.SaveMaintainedOrderTemplateHeaderRequest;
import com.openhis.health.application.basic.ordertemplate.request.SaveOrderTemplateFromAdviceRequest;
import com.openhis.health.application.basic.ordertemplate.response.GetOrderTemplateDetailResponse;
import com.openhis.health.application.basic.ordertemplate.response.InitOrderTemplateMaintenanceResponse;
import com.openhis.health.application.basic.ordertemplate.response.PageMaintainedOrderTemplateResponse;
import com.openhis.health.application.basic.ordertemplate.response.PreviewOrderTemplateApplyResponse;
import com.openhis.health.domain.custom.ordertemplate.command.AddOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.openhis.health.domain.share.enums.OrderTemplateScene;
import com.openhis.health.domain.custom.ordertemplate.command.CreateOrderTemplateCommand;
import com.openhis.health.domain.custom.ordertemplate.command.RemoveOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.command.UpdateOrderTemplateHeaderCommand;
import com.openhis.health.domain.custom.ordertemplate.command.UpdateOrderTemplateLineCommand;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateHeaderQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateLineQueryModel;
import com.openhis.health.domain.custom.ordertemplate.queries.model.OrderTemplateWithLinesQueryModel;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 组套模板 Request/Response 与领域 Command/QueryModel 映射（不含业务流程判断）。
 */
public interface OrderTemplateMapper {

    /**
     * 装配维护页初始化下拉响应。
     *
     * @return 初始化响应
     */
    InitOrderTemplateMaintenanceResponse toInitOrderTemplateMaintenanceResponse();

    /**
     * 分页 QueryModel 页 → 维护端分页响应。
     *
     * @param page 领域分页结果
     * @param keyword 名称/编码关键字（当前页内过滤）
     * @return 分页响应
     */
    PageMaintainedOrderTemplateResponse toPageMaintainedOrderTemplateResponse(
        Page<OrderTemplateHeaderQueryModel> page,
        String keyword
    );

    /**
     * 头 QueryModel 列表 → 头行 DTO 列表。
     *
     * @param models 头读模型列表
     * @return DTO 列表
     */
    List<OrderTemplateHeaderRowDto> toHeaderRowDtos(List<OrderTemplateHeaderQueryModel> models);

    /**
     * 头行聚合读模型 → 详情响应。
     *
     * @param model 头行读模型；null 时返回 null
     * @return 详情响应
     */
    GetOrderTemplateDetailResponse toGetOrderTemplateDetailResponse(OrderTemplateWithLinesQueryModel model);

    /**
     * 新建头 Request → CreateOrderTemplateCommand（固定 MAINTAINED）。
     *
     * @param request 保存头请求
     * @param operatorId 操作人
     * @param objectId 归属对象 id
     * @return 创建命令
     */
    CreateOrderTemplateCommand toCreateOrderTemplateCommand(
        SaveMaintainedOrderTemplateHeaderRequest request,
        Long operatorId,
        Long objectId
    );

    /**
     * 更新头 Request → UpdateOrderTemplateHeaderCommand。
     *
     * @param request 保存头请求
     * @param operatorId 操作人
     * @return 更新命令
     */
    UpdateOrderTemplateHeaderCommand toUpdateOrderTemplateHeaderCommand(
        SaveMaintainedOrderTemplateHeaderRequest request,
        Long operatorId
    );

    /**
     * 行编辑 DTO → 新增行命令。
     *
     * @param line 行编辑项
     * @param orderTemplateId 头 id
     * @param operatorId 操作人
     * @return 新增命令
     */
    AddOrderTemplateLineCommand toAddLineCommand(
        OrderTemplateLineEditDto line,
        Long orderTemplateId,
        Long operatorId
    );

    /**
     * 行编辑 DTO → 更新行命令。
     *
     * @param line 行编辑项
     * @param operatorId 操作人
     * @return 更新命令
     */
    UpdateOrderTemplateLineCommand toUpdateLineCommand(OrderTemplateLineEditDto line, Long operatorId);

    /**
     * 行 id → 移除行命令。
     *
     * @param lineId 行 id
     * @param operatorId 操作人
     * @return 移除命令
     */
    RemoveOrderTemplateLineCommand toRemoveLineCommand(Long lineId, Long operatorId);

    /**
     * 全量对齐 diff：计算待删/待改/待增行集合。
     *
     * @param submitted 提交行
     * @param existing 存量行
     * @return diff 结果
     */
    LineDiffResult diffLines(List<OrderTemplateLineEditDto> submitted, List<OrderTemplateLineQueryModel> existing);

    /**
     * 四段绑定列表 → 绑定分组 DTO（IF-BAS-OTL-07/11）。
     *
     * @param personalList 个人归属头列表
     * @param departmentList 科室归属头列表
     * @param hospitalList 全院归属头列表
     * @param agreementList 协定处方归属头列表
     * @param searchKey 名称关键字（可空）
     * @return 分段列表
     */
    OrderTemplateBindingGroupDto toListBindingGroupResponse(
        List<OrderTemplateHeaderQueryModel> personalList,
        List<OrderTemplateHeaderQueryModel> departmentList,
        List<OrderTemplateHeaderQueryModel> hospitalList,
        List<OrderTemplateHeaderQueryModel> agreementList,
        String searchKey
    );

    /**
     * 头行读模型 → 预览套用响应（IF-BAS-OTL-08）。
     *
     * @param model 头行读模型
     * @return 预览响应
     */
    PreviewOrderTemplateApplyResponse toPreviewOrderTemplateApplyResponse(OrderTemplateWithLinesQueryModel model);

    /**
     * 断言组套头可预览/套用（IF-BAS-OTL-08/09/12）。
     *
     * @param header 组套头读模型
     * @param applyContextCode 套用上下文 DOCTOR / NURSE_PRICING
     * @param encounterScene 就诊场景（护士站预览可空）
     * @param workstationKindCode 医生站工作站类型（DOCTOR 时必填）
     */
    void assertPreviewable(
        OrderTemplateHeaderQueryModel header,
        String applyContextCode,
        OrderTemplateScene encounterScene,
        String workstationKindCode
    );

    /**
     * 另存医嘱模板 → 创建头命令（IF-BAS-OTL-10，FROM_ADVICE + 个人/科室归属）。
     *
     * @param request 另存请求
     * @param scene 就诊场景
     * @param templateKindEnum 推导后的种类 code
     * @param bindingEnum 绑定类型 code（PERSONAL 或 DEPARTMENT）
     * @param objectId 归属对象 id（医务人员或科室组织）
     * @param operatorId 操作人
     * @return 创建命令
     */
    CreateOrderTemplateCommand toCreateOrderTemplateFromAdviceCommand(
        SaveOrderTemplateFromAdviceRequest request,
        OrderTemplateScene scene,
        String templateKindEnum,
        String bindingEnum,
        Long objectId,
        Long operatorId
    );

    /**
     * 由源医嘱行是否含 TCM 药推导模板种类（IF-BAS-OTL-10）。
     *
     * @param anyTcm 是否存在 TCM 药品行
     * @return GENERAL 或 TCM code
     */
    String deriveTemplateKindFromAdviceLines(boolean anyTcm);

    /**
     * 药品申请 → 新增组套行命令（IF-BAS-OTL-10）。
     *
     * @param command 待填充命令
     * @param medicationDefinitionId 药品定义 id
     * @param quantity 数量
     * @param unitCode 单位
     * @param routeCode 用法
     * @param frequencyId 频次 id
     * @param defaultDays 天数
     * @param dosageText 剂量文本
     * @param groupKey 成组键
     * @param sortOrder 排序
     * @param chineseHerbsDoseQuantity TCM 付数
     * @param sufferingFlag TCM 代煎
     * @param performOrgId 执行科室
     * @param lineRemark 备注
     */
    void fillAddOrderTemplateLineFromMedicationAdvice(
        AddOrderTemplateLineCommand command,
        Long medicationDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        String routeCode,
        Long frequencyId,
        Integer defaultDays,
        String dosageText,
        String groupKey,
        Integer sortOrder,
        java.math.BigDecimal chineseHerbsDoseQuantity,
        Boolean sufferingFlag,
        Long performOrgId,
        String lineRemark
    );

    /**
     * 诊疗申请 → 新增组套行命令（IF-BAS-OTL-10）。
     */
    void fillAddOrderTemplateLineFromServiceAdvice(
        AddOrderTemplateLineCommand command,
        Long activityDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        Long performOrgId,
        Integer sortOrder,
        String lineRemark
    );

    /**
     * 耗材申请 → 新增组套行命令（IF-BAS-OTL-10）。
     */
    void fillAddOrderTemplateLineFromDeviceAdvice(
        AddOrderTemplateLineCommand command,
        Long deviceDefinitionId,
        java.math.BigDecimal quantity,
        String unitCode,
        Long performOrgId,
        Integer sortOrder,
        String lineRemark
    );

    /**
     * 行全量对齐 diff 结果。
     */
    final class LineDiffResult {

        /** 待删除行 id。 */
        private final List<Long> removeLineIds;

        /** 待更新行。 */
        private final List<OrderTemplateLineEditDto> updateLines;

        /** 待新增行。 */
        private final List<OrderTemplateLineEditDto> addLines;

        /**
         * 构造 diff 结果。
         *
         * @param removeLineIds 待删 id
         * @param updateLines 待更新
         * @param addLines 待新增
         */
        public LineDiffResult(
            List<Long> removeLineIds,
            List<OrderTemplateLineEditDto> updateLines,
            List<OrderTemplateLineEditDto> addLines
        ) {
            this.removeLineIds = removeLineIds;
            this.updateLines = updateLines;
            this.addLines = addLines;
        }

        public List<Long> getRemoveLineIds() {
            return removeLineIds;
        }

        public List<OrderTemplateLineEditDto> getUpdateLines() {
            return updateLines;
        }

        public List<OrderTemplateLineEditDto> getAddLines() {
            return addLines;
        }
    }
}
