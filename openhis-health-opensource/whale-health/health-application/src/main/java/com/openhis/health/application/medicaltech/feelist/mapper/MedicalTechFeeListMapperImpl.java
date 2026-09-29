/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.mapper;

import com.openhis.health.application.medicaltech.feelist.dto.MedicalTechFeeItemRowDto;
import com.openhis.health.application.medicaltech.feelist.request.PrintMedicalTechFeeListRequest;
import com.openhis.health.application.medicaltech.feelist.response.ListMedicalTechFeeItemsResponse;
import com.openhis.health.application.medicaltech.feelist.response.PrintMedicalTechFeeListResponse;
import com.openhis.health.application.print.printjob.dto.PreparePrintResultDto;
import com.openhis.health.application.print.printjob.request.PreparePrintRequest;
import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link MedicalTechFeeListMapper} 实现：医技费用清单字段映射。
 *
 * <p>对应 IF-MT-FEE-01～02；null 安全返回空壳，禁止业务门闸。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalTechFeeListMapperImpl implements MedicalTechFeeListMapper {

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键
     * @return 收费查询命令
     */
    @Override
    public ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId) {
        ChargeItemQueryCommand command = new ChargeItemQueryCommand();
        command.setEncounterId(encounterId);
        return command;
    }

    /**
     * 将已过滤的收费行装配为清单行 DTO（含确费人/计费人展示名 enrich）。
     *
     * @param charges                           本科相关收费行
     * @param productByKey                      产品展示索引
     * @param orgNameById                      组织主键 → 展示名
     * @param practitionerNameById              医务人员主键 → 展示名
     * @param billingPractitionerByChargeItemId chargeItemId → 计费人
     * @return 清单行
     */
    @Override
    public List<MedicalTechFeeItemRowDto> toFeeItemRows(
        List<ChargeItem> charges,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, String> orgNameById,
        Map<Long, String> practitionerNameById,
        Map<Long, Long> billingPractitionerByChargeItemId) {
        List<MedicalTechFeeItemRowDto> rows = new ArrayList<>();
        if (charges == null || charges.isEmpty()) {
            return rows;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            MedicalTechFeeItemRowDto dto = new MedicalTechFeeItemRowDto();
            dto.setChargeItemId(charge.getId());
            dto.setQuantity(charge.getQuantityValue());
            dto.setUnitPrice(charge.getUnitPrice());
            dto.setTotalPrice(charge.getTotalPrice());
            dto.setChargeStatusEnum(charge.getStatusEnum());
            // 医技口语：PENDING_SETTLEMENT → 已划价待结算
            dto.setChargeStatusEnumText(
                AdviceChargeDisplayStatusAssembler.resolveMedicalTechChargeStatusDisplayText(
                    charge.getStatusEnum()));
            dto.setServiceTable(charge.getServiceTable());
            dto.setServiceId(charge.getServiceId());
            // 划价时间 = occurrence_time（护士执行入账/划价）
            dto.setPricedTime(charge.getOccurrenceTime());
            // 收费时间 = entered_date（真正结算戳，BR-OPC-016）；待结算时常为空
            dto.setOccurredTime(charge.getEnteredDate());
            // 产品名/编码：按 productTable+productId 复合键取读模型
            if (StringUtils.isNotEmpty(charge.getProductTable()) && charge.getProductId() != null
                && productByKey != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    charge.getProductTable(), charge.getProductId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    dto.setItemName(product.getProductName());
                    dto.setItemCode(product.getProductBusNo());
                }
            }
            // 相关科室展示：优先 requestingOrgId
            if (orgNameById != null && charge.getRequestingOrgId() != null) {
                dto.setRelatedOrgDisplay(orgNameById.get(charge.getRequestingOrgId()));
            }
            // 确费戳：有 confirmer 即已确费（出院门禁同源）
            dto.setFeeConfirmerId(charge.getFeeConfirmerId());
            dto.setFeeConfirmedAt(charge.getFeeConfirmedAt());
            // 确费人展示名：仅已确费行有值
            if (practitionerNameById != null && charge.getFeeConfirmerId() != null) {
                dto.setFeeConfirmerName(practitionerNameById.get(charge.getFeeConfirmerId()));
            }
            // 计费人：Procedure / Dispense 操作人或 Request.requester 回落
            if (billingPractitionerByChargeItemId != null) {
                Long billingId = billingPractitionerByChargeItemId.get(charge.getId());
                if (billingId != null) {
                    dto.setBillingPractitionerId(billingId);
                    if (practitionerNameById != null) {
                        dto.setBillingPractitionerName(practitionerNameById.get(billingId));
                    }
                }
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 行列表 + 合计 → 列表响应。
     *
     * @param rows      清单行
     * @param sumAmount 合计金额
     * @return 响应
     */
    @Override
    public ListMedicalTechFeeItemsResponse toListResponse(
        List<MedicalTechFeeItemRowDto> rows,
        BigDecimal sumAmount) {
        ListMedicalTechFeeItemsResponse response = new ListMedicalTechFeeItemsResponse();
        if (rows == null) {
            response.setRows(new ArrayList<>());
        } else {
            response.setRows(rows);
        }
        if (sumAmount == null) {
            response.setSumAmount(BigDecimal.ZERO);
        } else {
            response.setSumAmount(sumAmount);
        }
        return response;
    }

    /**
     * 打印请求 → 打印运行时 PreparePrintRequest（仅场景码）。
     *
     * @param request HTTP 打印请求
     * @return 打印准备入参
     */
    @Override
    public PreparePrintRequest toPrintPrepareRequest(PrintMedicalTechFeeListRequest request) {
        PreparePrintRequest prepare = new PreparePrintRequest();
        if (request == null) {
            return prepare;
        }
        prepare.setScenarioCode(request.getScenarioCode());
        return prepare;
    }

    /**
     * 打印 prepare 结果 → IF 响应。
     *
     * @param prepareResult 打印运行时结果
     * @return 打印响应
     */
    @Override
    public PrintMedicalTechFeeListResponse toPrintResponse(PreparePrintResultDto prepareResult) {
        PrintMedicalTechFeeListResponse response = new PrintMedicalTechFeeListResponse();
        response.setSucceeded(Boolean.TRUE);
        response.setMessage("打印准备成功");
        if (prepareResult != null) {
            response.setTraceId(prepareResult.getTraceId());
        }
        return response;
    }
}
