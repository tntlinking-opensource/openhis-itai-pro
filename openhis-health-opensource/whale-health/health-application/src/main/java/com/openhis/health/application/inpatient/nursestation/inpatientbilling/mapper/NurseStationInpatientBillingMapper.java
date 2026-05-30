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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;

import java.util.List;

/**
 * 住院护士站划价 Request/Command 映射（AS-IP-NUR-005）。
 *
 * <p>职责：入参收窄、写行 Gate 校验、父锚点 → based_on、领域 Command 字段搬运；不含跨域编排。</p>
 */
public interface NurseStationInpatientBillingMapper {

    /**
     * 可见性查询入参。
     *
     * @param encounterIds   请求就诊列表
     * @param managedWardIds 管理病区
     * @return 住院信息读模型查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * 选品目录 → 产品定义分页查询。
     *
     * @param request 分页请求
     * @return 读模型分页入参
     */
    ProductDefinitionPageQuery toProductDefinitionPageQuery(PageNurseStationPricingCatalogRequest request);

    /**
     * 解析划价产品表集合（默认诊疗+耗材，BR-IP-PRC-002）。
     *
     * @param requestTables 请求表名；可空
     * @return 物理表名列表
     */
    List<String> resolvePricingProductTableNames(List<String> requestTables);

    /**
     * therapyType → TherapyType code；all 返回 null。
     *
     * @param therapyType all/long/temp
     * @return 长临 code 或 null
     */
    String resolveTherapyEnum(String therapyType);

    /**
     * 直接划价：禁止父锚点（BR-IP-PRC-004）。
     *
     * @param items 写行
     */
    void validateDirectPricingItems(List<NurseStationPricingWriteItemDto> items);

    /**
     * 补费：每行须父锚点（BR-IP-PRC-005）。
     *
     * @param items 写行
     */
    void validateSupplementPricingItems(List<NurseStationPricingWriteItemDto> items);

    /**
     * 父锚点 → based_on 表名与 id。
     *
     * @param anchor 父锚点
     * @return 成对字段
     */
    NurseStationPricingBasedOnPair resolveBasedOn(NurseStationPricingParentRequestAnchorDto anchor);

    /**
     * 诊疗划价批量命令。
     *
     * @param serviceRows  诊疗行
     * @param operatorId   操作人
     * @param requesterId  申请人 practitioner
     * @return 领域命令
     */
    CreateFromNursePricingCommand toCreateFromNursePricingCommand(
        List<NurseStationPricingWriteItemDto> serviceRows,
        Long operatorId,
        Long requesterId);

    /**
     * 耗材划价批量命令。
     *
     * @param deviceRows 耗材行
     * @param operatorId 操作人
     * @return 领域命令
     */
    CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        List<NurseStationPricingWriteItemDto> deviceRows,
        Long operatorId);

    /**
     * 护士划价费用命令（PENDING_SETTLEMENT）。
     *
     * @param row           写行
     * @param serviceTable  服务表
     * @param serviceId     服务 id
     * @param dispenseTable 发放表；可空
     * @param dispenseId    发放 id；可空
     * @param operatorId    操作人
     * @param orgId         机构
     * @return 收费命令
     */
    CreateChargeItemCommand toCreateChargeItemCommand(
        NurseStationPricingWriteItemDto row,
        String serviceTable,
        Long serviceId,
        String dispenseTable,
        Long dispenseId,
        Long operatorId,
        Long orgId);

    /**
     * 临时路径生成耗材发放草稿。
     *
     * @param deviceReqId 耗材请求 id
     * @param row         写行
     * @param operatorId  操作人
     * @return 命令
     */
    GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        NurseStationPricingWriteItemDto row,
        Long operatorId);

    /**
     * 关键字/科室二次过滤父医嘱行。
     *
     * @param rows    源行
     * @param keyword 关键字
     * @param orgId   科室
     * @return 过滤后
     */
    List<NurseStationSupplementParentOrderRowDto> filterByKeywordAndOrg(
        List<NurseStationSupplementParentOrderRowDto> rows,
        String keyword,
        Long orgId);

    /**
     * 已划价模式：DIRECT / SUPPLEMENT。
     *
     * @param skeleton OrderInfo 行
     * @return 模式 code
     */
    String resolvePricingMode(InpatientOrderListSkeleton skeleton);
}
