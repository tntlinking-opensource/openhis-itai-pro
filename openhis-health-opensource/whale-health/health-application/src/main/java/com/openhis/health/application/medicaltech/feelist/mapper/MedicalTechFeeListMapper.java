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
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 医技费用清单应用层装配：ChargeItem/产品读模型 → 行 DTO；打印 Prepare 入参。
 *
 * <p>禁止承载 BR-MT-FEE-002 过滤判定；过滤在 {@code MedicalTechFeeListAppService}。</p>
 */
public interface MedicalTechFeeListMapper {

    /**
     * 构造按就诊装载收费明细命令。
     *
     * @param encounterId 就诊主键
     * @return 收费查询命令
     */
    ChargeItemQueryCommand toChargeItemQueryCommand(Long encounterId);

    /**
     * 将已过滤的收费行装配为清单行 DTO。
     *
     * @param charges                    本科相关收费行
     * @param productByKey               产品展示索引（compositeKey → 读模型）
     * @param orgNameById               组织主键 → 展示名（可选 enrich）
     * @param practitionerNameById       医务人员主键 → 展示名（确费人/计费人）
     * @param billingPractitionerByChargeItemId chargeItemId → 计费人 practitionerId
     * @return 清单行
     */
    List<MedicalTechFeeItemRowDto> toFeeItemRows(
        List<ChargeItem> charges,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, String> orgNameById,
        Map<Long, String> practitionerNameById,
        Map<Long, Long> billingPractitionerByChargeItemId);

    /**
     * 行列表 + 合计 → 列表响应。
     *
     * @param rows      清单行
     * @param sumAmount 合计金额
     * @return 响应
     */
    ListMedicalTechFeeItemsResponse toListResponse(List<MedicalTechFeeItemRowDto> rows, BigDecimal sumAmount);

    /**
     * 打印请求 → 打印运行时 PreparePrintRequest（仅场景码）。
     *
     * @param request HTTP 打印请求
     * @return 打印准备入参
     */
    PreparePrintRequest toPrintPrepareRequest(PrintMedicalTechFeeListRequest request);

    /**
     * 打印 prepare 结果 → IF 响应。
     *
     * @param prepareResult 打印运行时结果
     * @return 打印响应
     */
    PrintMedicalTechFeeListResponse toPrintResponse(PreparePrintResultDto prepareResult);
}
