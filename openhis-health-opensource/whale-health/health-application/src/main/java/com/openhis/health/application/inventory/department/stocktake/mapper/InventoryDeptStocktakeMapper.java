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

package com.openhis.health.application.inventory.department.stocktake.mapper;

import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.department.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.stocktake.dto.DeptStocktakeDetailLineDto;
import com.openhis.health.application.inventory.department.stocktake.dto.DeptStocktakeListRowDto;
import com.openhis.health.application.inventory.department.stocktake.request.*;
import com.openhis.health.application.inventory.department.stocktake.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.supplydelivery.command.CreateStocktakingDeliveryCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.AgreeRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateStocktakeCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RejectRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.RemoveByBusNoCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.SubmitApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.WithdrawApprovalCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 应用层装配契约：HTTP 请求/响应与领域命令、读模型行之间的转换。
 *
 * <p>职责边界：仅做字段搬运与枚举 code 归一；不承载库存、审批状态机等业务流程判断。</p>
 */
public interface InventoryDeptStocktakeMapper {

    /**
     * 将启用药库位置列表映射为下拉选项。
     *
     * @param locations 启用库位列表
     * @return 
     */
    List<OptionDto> toOptionDtoListFromLocations(List<Location> locations);

    /**
     * 将启用人员列表映射为下拉选项。
     *
     * @param practitioners 人员列表
     * @return 人员下拉选项
     */
    List<OptionDto> toOptionDtoListFromPractitioners(List<Practitioner> practitioners);

    /**
     * 装配入库项目类型两档固定下拉选项。
     *
     * @return 药品/耗材类型选项
     */
    List<OptionDto> toOptionDtoListInboundItemTableTypes();

    /**
     * 装配供应单据状态下拉选项。
     *
     * @return 单据状态选项
     */
    List<OptionDto> toOptionDtoListSupplyStatuses();

    /**
     * 装配初始化响应。
     *
     * @param deptLocationOpts  药库选项
     * @param inboundTypes   入库项目类型选项
     * @param statusOpts     状态选项
     * @param nurseOpts 护士选项
     * @param keeperOpts     库管选项
     * @return 页初始化响应
     */
    LoadDeptStocktakeFormInitResponse toLoadDeptStocktakeFormInitResponse(
        List<OptionDto> deptLocationOpts,
        List<OptionDto> inboundTypes,
        List<OptionDto> statusOpts,
        List<OptionDto> nurseOpts,
        List<OptionDto> keeperOpts);

    /**
     * 将分页单号顺序与同页明细行聚合为行。
     *
     * @param busNoPageOrder 当前页单号顺序
     * @param detailRows     当前页涉及的全部明细行
     * @return 聚合后的列表行
     */
    List<DeptStocktakeListRowDto> toDeptStocktakeListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows);

    /**
     * 装配分页响应（pageNo 为 1-based）。
     *
     * @param mergedTotal 去重后的总单数
     * @param pageNo      页码（1-based）
     * @param pageSize    页大小
     * @param records     当前页聚合行
     * @return 分页响应
     */
    PageDeptStocktakesResponse toPageDeptStocktakesAggregatedResponse(
        long mergedTotal, int pageNo, int pageSize, List<DeptStocktakeListRowDto> records);

    /**
     * 将同号供应请求明细与产品目录提示装配为响应。
     *
     * @param lines        供应请求明细行
     * @param productHints 行 id 到目录编辑提示的映射
     * @return 详情响应
     */
    GetDeptStocktakeDetailResponse toGetDeptStocktakeDetailResponse(
        List<SupplyRequestModel> lines,
        Map<Long, ProductDefinitionCatalogEditorHintsDto> productHints);

    /**
     * 将单行供应请求明细装配为行 DTO。
     *
     * @param line 供应请求明细行
     * @return 详情行 DTO
     */
    DeptStocktakeDetailLineDto toDeptStocktakeDetailLineDto(SupplyRequestModel line);

    /**
     * 将请求装配为领域 CreateStocktakeCommand。
     *
     * @param request     新建请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @param busNo       预分配业务单号
     * @return 领域写命令
     */
    CreateStocktakeCommand toCreateStocktakeCommand(
        CreateDeptStocktakeRequest request,
        Long operatorId,
        Long applicantId,
        String busNo);

    /**
     * 将请求装配为领域 CreateStocktakeCommand（同号全量对齐）。
     *
     * @param request     修订请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    CreateStocktakeCommand toCreateStocktakeCommand(
        UpdateDeptStocktakeRequest request,
        Long operatorId,
        Long applicantId);

    /**
     * 装配成功响应。
     *
     * @param busNo 新建业务单号
     * @return 新建响应
     */
    CreateDeptStocktakeResponse toCreateDeptStocktakeResponse(String busNo);

    /**
     * 装配成功响应。
     *
     * @param busNo 业务单号
     * @return 修订响应
     */
    UpdateDeptStocktakeResponse toUpdateDeptStocktakeResponse(String busNo);

    /**
     * 将提交审批请求装配为领域 SubmitApprovalCommand。
     *
     * @param request     提交请求
     * @param operatorId  操作人
     * @param applicantId 制单人员
     * @return 领域写命令
     */
    SubmitApprovalCommand toSubmitApprovalCommand(SubmitDeptStocktakeApprovalRequest request, Long operatorId, Long applicantId);

    /**
     * 装配提交审批成功响应（含提交后展示状态）。
     *
     * @param statusAfter 提交后供应状态
     * @return 提交响应
     */
    SubmitDeptStocktakeApprovalResponse toSubmitDeptStocktakeApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将撤回审批请求装配为领域 WithdrawApprovalCommand。
     *
     * @param request    撤回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    WithdrawApprovalCommand toWithdrawApprovalCommand(WithdrawDeptStocktakeApprovalRequest request, Long operatorId);

    /**
     * 装配撤回审批成功响应（含撤回后展示状态）。
     *
     * @param statusAfter 撤回后供应状态
     * @return 撤回响应
     */
    WithdrawDeptStocktakeApprovalResponse toWithdrawDeptStocktakeApprovalResponse(SupplyStatus statusAfter);

    /**
     * 将同意请求装配为领域 AgreeRequestCommand。
     *
     * @param request    同意请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    AgreeRequestCommand toAgreeRequestCommand(AgreeDeptStocktakeRequest request, Long operatorId);

    /**
     * 将已同意明细行装配为命令。
     *
     * @param lines      已同意的供应请求明细行
     * @param operatorId 操作人
     * @return 命令
     */
    CreateStocktakingDeliveryCommand toCreateStocktakingDeliveryCommand(List<SupplyRequestModel> lines, Long operatorId);

    /**
     * 装配盘盈增库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param increaseQty     增加数量
     * @param operatorId      操作人
     * @return 增库存命令
     */
    IncreaseQuantityCommand toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId);

    /**
     * 装配盘亏减库存命令。
     *
     * @param inventoryItemId 库存项主键
     * @param decreaseQty     减少数量
     * @param allowNegative   是否允许负库存
     * @param operatorId      操作人
     * @return 减库存命令
     */
    DecreaseQuantityCommand toDecreaseQuantityCommand(
        Long inventoryItemId, BigDecimal decreaseQty, boolean allowNegative, Long operatorId);

    /**
     * 装配成功响应。
     *
     * @param busNo      业务单号
     * @param statusEnum 同意后状态 code
     * @return 同意响应
     */
    AgreeDeptStocktakeResponse toAgreeDeptStocktakeResponse(String busNo, String statusEnum);

    /**
     * 将驳回请求装配为领域 RejectRequestCommand。
     *
     * @param request    驳回请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RejectRequestCommand toRejectRequestCommand(RejectDeptStocktakeRequest request, Long operatorId);

    /**
     * 装配成功响应。
     *
     * @param ok 是否驳回成功
     * @return 驳回响应
     */
    RejectDeptStocktakeResponse toRejectDeptStocktakeResponse(boolean ok);

    /**
     * 将删除请求装配为领域 RemoveByBusNoCommand。
     *
     * @param request    删除请求
     * @param operatorId 操作人
     * @return 领域写命令
     */
    RemoveByBusNoCommand toRemoveByBusNoCommand(RemoveDeptStocktakeRequest request, Long operatorId);

    /**
     * 装配成功响应。
     *
     * @param ok 是否删除成功
     * @return 删除响应
     */
    RemoveDeptStocktakeResponse toRemoveDeptStocktakeResponse(boolean ok);

    /**
     * 将现库库存分页装配为分页响应。
     *
     * @param page 库存读模型分页
     * @return 选品分页响应
     */
    PageProductsForDeptStocktakeResponse toPageProductsForDeptStocktakeResponse(Page<InventoryItemModel> page);

    /**
     * 将库存行与产品读模型装配为选品行 DTO。
     *
     * @param inv 库存行
     * @param pr  产品读模型，可为 null
     * @return 选品行 DTO
     */
    ProductDefinitionItemDto toProductDefinitionItemDtoFromInventory(InventoryItemModel inv, ProductDefinitionDisplayDto pr);
}
