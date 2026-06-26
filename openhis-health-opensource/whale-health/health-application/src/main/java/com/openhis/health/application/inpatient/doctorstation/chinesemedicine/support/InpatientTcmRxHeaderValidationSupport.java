/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.support;

import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request.TcmRxDraftHeaderRequest;
import com.openhis.health.domain.share.constants.DoctorStationTcmConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationTcmConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BR-IP-TCM-005/006：住院中药方头长临与方头一致性校验。
 */
@Component
public class InpatientTcmRxHeaderValidationSupport {

    /**
     * 校验方头长临、频次与同方一致性。
     *
     * @param headers 待校验方头列表
     * @throws BusinessException 长临/频次非法或同方头字段不一致时
     */
    public void validateTherapyAndRateAndHeaderConsistency(List<TcmRxDraftHeaderRequest> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<String, TcmRxDraftHeaderRequest> canonicalByGroupKey = new HashMap<>();
        for (TcmRxDraftHeaderRequest header : headers) {
            validateSingleHeaderTherapyAndRate(header);
            if (StringUtils.isBlank(header.getPrescriptionGroupKey())) {
                continue;
            }
            String groupKey = header.getPrescriptionGroupKey().trim();
            TcmRxDraftHeaderRequest canonical = canonicalByGroupKey.get(groupKey);
            if (canonical == null) {
                canonicalByGroupKey.put(groupKey, header);
            } else {
                assertHeaderFieldsMatch(canonical, header);
            }
        }
    }

    /**
     * 解析 therapyTypeCode 为 therapyEnum；非法值抛异常。
     *
     * @param therapyTypeCode 请求筛选码
     * @return therapyEnum 或 null（全部）
     * @throws BusinessException 筛选码非法时
     */
    public String resolveTherapyFilterOrNull(String therapyTypeCode) {
        if (StringUtils.isBlank(therapyTypeCode)) {
            return null;
        }
        String trimmed = therapyTypeCode.trim();
        if (EnumUtil.fromCode(TherapyType.class, trimmed) == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "长临筛选参数非法");
        }
        return trimmed;
    }

    /**
     * 断言目录行均为中草药分类（BR-TCM-RX-003）。
     *
     * @param products 产品读模型
     * @throws BusinessException 存在非中草药分类产品时
     */
    public void assertHerbalCategoryOnly(List<ProductDefinitionDisplayDto> products) {
        if (products == null) {
            return;
        }
        for (ProductDefinitionDisplayDto product : products) {
            if (product == null) {
                continue;
            }
            if (!DoctorStationTcmConstants.HERBAL_CATEGORY_CODE.equals(product.getCategoryCode())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "仅允许开立中草药饮片");
            }
        }
    }

    /**
     * 校验单方头长临与频次组合是否合法。
     *
     * @param header 方头请求
     * @throws BusinessException 长临为空/非法，或长临与频次不匹配时
     */
    private void validateSingleHeaderTherapyAndRate(TcmRxDraftHeaderRequest header) {
        // 方头长临必填
        if (header == null || StringUtils.isBlank(header.getTherapyEnum())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "方头长临不能为空");
        }
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, header.getTherapyEnum().trim());
        // 长临 code 须为有效枚举
        if (therapyType == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "方头长临参数非法");
        }
        String rateCode = header.getRateCode();
        // 临嘱频次须为「立即」
        if (therapyType == TherapyType.TEMPORARY) {
            if (!InpatientDoctorStationTcmConstants.IMMEDIATE_RATE_CODE.equals(StringUtils.trimToEmpty(rateCode))) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    InpatientDoctorStationTcmConstants.MSG_TEMPORARY_RATE_NOT_IMMEDIATE);
            }
        }
        // 长嘱频次禁止「立即」
        if (therapyType == TherapyType.LONG_TERM) {
            if (InpatientDoctorStationTcmConstants.IMMEDIATE_RATE_CODE.equals(StringUtils.trimToEmpty(rateCode))) {
                throw new BusinessException(
                    ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                    InpatientDoctorStationTcmConstants.MSG_LONG_TERM_RATE_IMMEDIATE);
            }
        }
    }

    /**
     * 断言同一 prescriptionGroupKey 下两行方头关键字段一致。
     *
     * @param left  基准方头
     * @param right 待比对方头
     * @throws BusinessException 长临/频次/剂数/用法等不一致时
     */
    private void assertHeaderFieldsMatch(TcmRxDraftHeaderRequest left, TcmRxDraftHeaderRequest right) {
        if (!Objects.equals(trimString(left.getTherapyEnum()), trimString(right.getTherapyEnum()))
            || !Objects.equals(trimString(left.getRateCode()), trimString(right.getRateCode()))
            || !bigDecimalEquals(left.getChineseHerbsDoseQuantity(), right.getChineseHerbsDoseQuantity())
            || !Objects.equals(left.getSufferingFlag(), right.getSufferingFlag())
            || !Objects.equals(trimString(left.getMethodCode()), trimString(right.getMethodCode()))) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT,
                InpatientDoctorStationTcmConstants.MSG_HEADER_INCONSISTENT);
        }
    }

    /**
     * 去首尾空白；null 保持 null。
     *
     * @param value 原始字符串
     * @return trim 后值或 null
     */
    private static String trimString(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    /**
     * 比较两个 BigDecimal 数值是否相等（含双 null）。
     *
     * @param left  左值
     * @param right 右值
     * @return true 表示数值相等或均为 null
     */
    private static boolean bigDecimalEquals(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }
}
