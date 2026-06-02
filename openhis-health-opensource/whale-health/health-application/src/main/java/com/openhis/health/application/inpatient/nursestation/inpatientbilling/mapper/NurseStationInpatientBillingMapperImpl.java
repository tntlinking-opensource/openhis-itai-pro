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

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingParentRequestAnchorDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationSupplementParentOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.request.PageNurseStationPricingCatalogRequest;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.constants.ChargeItemDmConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.devicerequest.command.CreateExecutedDeviceOrderCommand;
import com.openhis.health.domain.workflow.devicedispense.command.GenerateDeviceDispenseFromTemporaryPathCommand;
import com.openhis.health.domain.workflow.servicerequest.command.CreateFromNursePricingCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link NurseStationInpatientBillingMapper} 实现：字段搬运与 Gate 校验，无跨域编排。
 */
@Component
public class NurseStationInpatientBillingMapperImpl implements NurseStationInpatientBillingMapper {

    /**
     * 组装住院信息读模型可见性查询（护士站队列 + 就诊/病区收窄）。
     *
     * @param encounterIds   请求就诊列表
     * @param managedWardIds 管理病区
     * @return 住院信息读模型查询
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * 选品目录请求 → 产品定义分页查询（排除药品，BR-IP-PRC-002）。
     *
     * @param request 分页请求
     * @return 读模型分页入参
     */
    @Override
    public ProductDefinitionPageQuery toProductDefinitionPageQuery(PageNurseStationPricingCatalogRequest request) {
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        PageQueryBinder.bind(request, query);
        // 划价选品固定排除药品（BR-IP-PRC-002）
        query.setPricingFlag(1);
        query.setActiveFlag(Boolean.TRUE);
        query.setProductTableNames(this.resolvePricingProductTableNames(request.getProductTableNames()));
        return query;
    }

    /**
     * 解析划价产品表集合（默认诊疗+耗材，BR-IP-PRC-002）。
     *
     * @param requestTables 请求表名；可空
     * @return 物理表名列表
     */
    @Override
    public List<String> resolvePricingProductTableNames(List<String> requestTables) {
        if (requestTables != null && !requestTables.isEmpty()) {
            List<String> tables = new ArrayList<>();
            for (String table : requestTables) {
                if (StringUtils.isBlank(table)) {
                    continue;
                }
                String trimmed = table.trim();
                // 划价域禁止药品表（BR-IP-PRC-002）
                if (TableNameConstants.MED_MEDICATION_DEFINITION.equalsIgnoreCase(trimmed)) {
                    continue;
                }
                tables.add(trimmed);
            }
            if (!tables.isEmpty()) {
                return tables;
            }
        }
        return Arrays.asList(
            TableNameConstants.WKF_ACTIVITY_DEFINITION,
            TableNameConstants.ADM_DEVICE_DEFINITION);
    }

    /**
     * therapyType → TherapyType code；all 返回 null。
     *
     * @param therapyType all/long/temp
     * @return 长临 code 或 null
     */
    @Override
    public String resolveTherapyEnum(String therapyType) {
        if (StringUtils.isBlank(therapyType)) {
            return null;
        }
        String normalized = therapyType.trim().toLowerCase();
        if (InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 直接划价：禁止父锚点（BR-IP-PRC-004）。
     *
     * @param items 写行
     */
    @Override
    public void validateDirectPricingItems(List<NurseStationPricingWriteItemDto> items) {
        if (items == null) {
            return;
        }
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null) {
                continue;
            }
            if (row.getParentRequestAnchor() != null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_201,
                    "直接划价请求格式错误，请使用划价确费入口");
            }
        }
    }

    /**
     * 补费：每行须父锚点（BR-IP-PRC-005）。
     *
     * @param items 写行
     */
    @Override
    public void validateSupplementPricingItems(List<NurseStationPricingWriteItemDto> items) {
        if (items == null) {
            return;
        }
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null) {
                continue;
            }
            NurseStationPricingParentRequestAnchorDto anchor = row.getParentRequestAnchor();
            if (anchor == null || anchor.getRequestId() == null || StringUtils.isBlank(anchor.getOrderType())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_301,
                    "关联医嘱无效，请刷新医嘱列表后重试");
            }
        }
    }

    /**
     * 父锚点 → based_on 表名与 id。
     *
     * @param anchor 父锚点
     * @return 成对字段
     */
    @Override
    public NurseStationPricingBasedOnPair resolveBasedOn(NurseStationPricingParentRequestAnchorDto anchor) {
        NurseStationPricingBasedOnPair pair = new NurseStationPricingBasedOnPair();
        if (anchor == null) {
            return pair;
        }
        // 父锚点 orderType → 物理 based_on 表
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(anchor.getOrderType())) {
            pair.setBasedOnTable(TableNameConstants.MED_MEDICATION_REQUEST);
            pair.setBasedOnId(anchor.getRequestId());
            return pair;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(anchor.getOrderType())) {
            pair.setBasedOnTable(TableNameConstants.WKF_SERVICE_REQUEST);
            pair.setBasedOnId(anchor.getRequestId());
            return pair;
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_PRC_301,
            "关联医嘱无效，请刷新医嘱列表后重试");
    }

    /**
     * 诊疗划价批量命令。
     *
     * @param serviceRows  诊疗行
     * @param operatorId   操作人
     * @param requesterId  申请人 practitioner
     * @return 领域命令
     */
    @Override
    public CreateFromNursePricingCommand toCreateFromNursePricingCommand(
        List<NurseStationPricingWriteItemDto> serviceRows,
        Long operatorId,
        Long requesterId) {
        CreateFromNursePricingCommand command = new CreateFromNursePricingCommand();
        command.setOperatorId(operatorId);
        List<CreateFromNursePricingCommand.CreateFromNursePricingItemCommand> items = new ArrayList<>();
        if (serviceRows != null) {
            for (NurseStationPricingWriteItemDto row : serviceRows) {
                if (row == null) {
                    continue;
                }
                CreateFromNursePricingCommand.CreateFromNursePricingItemCommand item =
                    new CreateFromNursePricingCommand.CreateFromNursePricingItemCommand();
                item.setEncounterId(row.getEncounterId());
                item.setPatientId(row.getPatientId());
                item.setActivityId(row.getActivityId());
                item.setQuantity(row.getQuantity());
                item.setUnitCode(row.getUnitCode());
                item.setRequesterId(requesterId);
                item.setPerformLocation(row.getPerformLocation());
                // 补费：父锚点 → based_on 表/id（直接划价在 validate 阶段已禁止锚点）
                if (row.getParentRequestAnchor() != null) {
                    NurseStationPricingBasedOnPair pair = this.resolveBasedOn(row.getParentRequestAnchor());
                    item.setBasedOnTable(pair.getBasedOnTable());
                    item.setBasedOnId(pair.getBasedOnId());
                }
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 耗材划价批量命令。
     *
     * @param deviceRows 耗材行
     * @param operatorId 操作人
     * @return 领域命令
     */
    @Override
    public CreateExecutedDeviceOrderCommand toCreateExecutedDeviceOrderCommand(
        List<NurseStationPricingWriteItemDto> deviceRows,
        Long operatorId) {
        CreateExecutedDeviceOrderCommand command = new CreateExecutedDeviceOrderCommand();
        command.setOperatorId(operatorId);
        List<CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand> items = new ArrayList<>();
        if (deviceRows != null) {
            for (NurseStationPricingWriteItemDto row : deviceRows) {
                if (row == null) {
                    continue;
                }
                CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand item =
                    new CreateExecutedDeviceOrderCommand.CreateExecutedDeviceOrderItemCommand();
                // 护士划价来源，请求态 COMPLETED（由域服务落库）
                item.setGenerateSourceEnum(GenerateSource.NURSE_PRICING.getCode());
                item.setDeviceDefId(row.getDeviceDefId());
                item.setQuantity(row.getQuantity());
                item.setEncounterId(row.getEncounterId());
                item.setPatientId(row.getPatientId());
                item.setPerformLocation(row.getPerformLocation());
                if (row.getParentRequestAnchor() != null) {
                    NurseStationPricingBasedOnPair pair = this.resolveBasedOn(row.getParentRequestAnchor());
                    item.setBasedOnTable(pair.getBasedOnTable());
                    item.setBasedOnId(pair.getBasedOnId());
                }
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

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
    @Override
    public CreateChargeItemCommand toCreateChargeItemCommand(
        NurseStationPricingWriteItemDto row,
        String serviceTable,
        Long serviceId,
        String dispenseTable,
        Long dispenseId,
        Long operatorId,
        Long orgId) {
        CreateChargeItemCommand command = new CreateChargeItemCommand();
        command.setOperatorId(operatorId);
        CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
        item.setPatientId(row.getPatientId());
        item.setEncounterId(row.getEncounterId());
        item.setAccountId(row.getAccountId());
        item.setDefinitionId(row.getChargeItemDefinitionId());
        item.setServiceTable(serviceTable);
        item.setServiceId(serviceId);
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
            item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            item.setProductId(row.getActivityId());
        } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
            item.setProductTable(TableNameConstants.ADM_DEVICE_DEFINITION);
            item.setProductId(row.getDeviceDefId());
        }
        item.setUnitPrice(row.getUnitPrice());
        BigDecimal qty = row.getQuantity();
        if (qty == null) {
            qty = BigDecimal.ONE;
        }
        item.setQuantityValue(qty);
        item.setQuantityUnit(StringUtils.defaultIfBlank(row.getUnitCode(), ChargeItemDmConstants.DEFAULT_QUANTITY_UNIT));
        item.setTotalPrice(row.getTotalPrice());
        item.setBaseAmount(row.getTotalPrice());
        item.setDiscountAmount(row.getTotalPrice());
        item.setSurchargeAmount(BigDecimal.ZERO);
        item.setOccurrenceTime(LocalDateTime.now());
        // 耗材轨关联发放 id，诊疗轨 dispense 为空
        item.setDispenseTable(dispenseTable);
        item.setDispenseId(dispenseId);
        item.setOrgId(orgId);
        item.setRequestingOrgId(orgId);
        item.setGenerateSourceEnum(GenerateSource.NURSE_PRICING.getCode());
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 临时路径生成耗材发放草稿。
     *
     * @param deviceReqId 耗材请求 id
     * @param row         写行
     * @param operatorId  操作人
     * @return 命令
     */
    @Override
    public GenerateDeviceDispenseFromTemporaryPathCommand toGenerateDeviceDispenseFromTemporaryPathCommand(
        Long deviceReqId,
        NurseStationPricingWriteItemDto row,
        Long operatorId) {
        GenerateDeviceDispenseFromTemporaryPathCommand command = new GenerateDeviceDispenseFromTemporaryPathCommand();
        command.setOperatorId(operatorId);
        GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand line =
            new GenerateDeviceDispenseFromTemporaryPathCommand.GenerateDeviceDispenseFromTemporaryPathItemCommand();
        line.setDeviceReqId(deviceReqId);
        line.setDeviceDefId(row.getDeviceDefId());
        line.setEncounterId(row.getEncounterId());
        line.setPatientId(row.getPatientId());
        line.setQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());
        line.setLimitTime(LocalDateTime.now());
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 关键字/科室二次过滤父医嘱行。
     *
     * @param rows    源行
     * @param keyword 关键字
     * @param orgId   科室
     * @return 过滤后
     */
    @Override
    public List<NurseStationSupplementParentOrderRowDto> filterByKeywordAndOrg(
        List<NurseStationSupplementParentOrderRowDto> rows,
        String keyword,
        Long orgId) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase();
        List<NurseStationSupplementParentOrderRowDto> filtered = new ArrayList<>();
        for (NurseStationSupplementParentOrderRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (orgId != null && row.getEncounterId() != null) {
                // 科室筛选在 App 层按 OrderInfo.orgId 已下推；此处仅关键字
            }
            if (StringUtils.isNotBlank(kw)) {
                String name = StringUtils.defaultString(row.getAdviceDisplayName()).toLowerCase();
                if (!name.contains(kw)) {
                    continue;
                }
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 已划价模式：DIRECT / SUPPLEMENT。
     *
     * @param skeleton OrderInfo 行
     * @return 模式 code
     */
    @Override
    public String resolvePricingMode(InpatientOrderListSkeleton skeleton) {
        // 无 based_on：直接划价；有 based_on（诊疗 based_on_id / 耗材 dev_based_on_id）：医嘱补费子行
        if (skeleton == null) {
            return InpatientNurseStationConstants.PRICING_MODE_DIRECT;
        }
        Long parentAnchorId = skeleton.getBasedOnId();
        if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(skeleton.getOrderType())
            && skeleton.getDevBasedOnId() != null) {
            parentAnchorId = skeleton.getDevBasedOnId();
        }
        if (parentAnchorId == null) {
            return InpatientNurseStationConstants.PRICING_MODE_DIRECT;
        }
        return InpatientNurseStationConstants.PRICING_MODE_SUPPLEMENT;
    }
}
