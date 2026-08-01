/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.MATCHED_BY_PRESCRIPTION_NO;
import static com.openhis.whale.crb.domain.share.CrbLabPathBConstants.MATCHED_BY_SERVICE_REQUEST_ID;

/**
 * MedSync APPLYNO 反查实现：prescriptionNo 优先，其次 ServiceRequest.id。
 */
@Primary
@Component
@RequiredArgsConstructor
public class YunlisApplyNoServiceRequestResolverImpl implements YunlisApplyNoServiceRequestResolver {

    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 按 APPLYNO/BARCODE 反查 ServiceRequest 上下文（prescriptionNo 优先，其次 id）。
     *
     * @param applyNo MedSync 外发的申请单号
     * @return 命中上下文；未找到则 empty
     */
    @Override
    public Optional<YunlisApplyNoResolveResult> resolve(String applyNo) {
        String trimmed = trimToNull(applyNo);
        // 空申请单号无法反查
        if (trimmed == null) {
            return Optional.empty();
        }
        // 主路径：与 MedSync 外发一致，先按处方号/申请单号匹配
        Optional<ServiceRequestListModel> byPrescription =
                serviceRequestQueryService.findFirstActiveByPrescriptionNo(trimmed);
        if (byPrescription.isPresent()) {
            return Optional.of(toResult(byPrescription.get(), MATCHED_BY_PRESCRIPTION_NO));
        }
        // 回退：applyNo 为 ServiceRequest 雪花 id 的十进制串
        Long asId = tryParseLongId(trimmed);
        if (asId == null) {
            return Optional.empty();
        }
        Optional<ServiceRequestListModel> byId = serviceRequestQueryService.findActiveListModelById(asId);
        if (byId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toResult(byId.get(), MATCHED_BY_SERVICE_REQUEST_ID));
    }

    /**
     * 将读模型转为反查结果。
     *
     * @param model 服务申请列表模型
     * @param matchedBy 命中方式 code
     * @return 反查结果
     */
    private static YunlisApplyNoResolveResult toResult(ServiceRequestListModel model, String matchedBy) {
        YunlisApplyNoResolveResult result = new YunlisApplyNoResolveResult();
        result.setServiceRequestId(model.getId());
        result.setEncounterId(model.getEncounterId());
        result.setPatientId(model.getPatientId());
        result.setMatchedBy(matchedBy);
        return result;
    }

    /**
     * 尝试将 applyNo 解析为 ServiceRequest 主键；非纯数字则返回 null（非枚举 code 场景）。
     *
     * @param applyNo 申请单号
     * @return Long id 或 null
     */
    private static Long tryParseLongId(String applyNo) {
        try {
            return Long.valueOf(applyNo);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
