/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.medicalinfo.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.medicalinfo.command.MedicalInfoQueryBaseCommand;
import com.openhis.chs.domain.medicalinfo.command.QueryCum5206Command;
import com.openhis.chs.domain.medicalinfo.command.QueryDise5202Command;
import com.openhis.chs.domain.medicalinfo.command.QueryFee5204Command;
import com.openhis.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspDrug5205Command;
import com.openhis.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.openhis.chs.domain.medicalinfo.command.QuerySetl5203Command;
import com.openhis.chs.domain.medicalinfo.constants.ChsMedicalInfoConstants;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 医保信息查询（5201–5301）出站公共支撑：契约解析、签到上下文组装与入参校验。
 *
 * <p>供 {@link com.openhis.chs.bridge.medicalinfo.invoker.Med5201Invoker}–
 * {@link com.openhis.chs.bridge.medicalinfo.invoker.Med5301Invoker} 共用，
 * 统一从 {@link ChsInsuranceContractReader} 解析 contractBusNo，
 * 并通过 {@link ChsSignCredentialResolver} 填充操作员签到凭证。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicalInfoInvokeSupport {

    private final ChsInsuranceContractReader contractReader;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /** 解析契约 bus_no，供 5201–5301 gateway 出站使用。 */
    public String resolveBusNo(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        return contractReader.requireContractBusNo(contract);
    }

    /** 构建含签到凭证的操作员出站上下文。 */
    public InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再查询医保信息");
        InvokeContext partial = InvokeContext.builder().operatorId(String.valueOf(operatorId)).build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    /** 校验 5201 就诊信息查询入参。 */
    public void validateMdtrt5201(QueryMdtrt5201Command command) {
        requirePsnNo(command);
        requireNonBlank(command.getBegntime(), "开始时间");
        requireNonBlank(command.getEndtime(), "结束时间");
        requireNonBlank(StringUtils.defaultIfBlank(command.getMedType(), "11"), "医疗类别");
        assertDateTimeRange(command.getBegntime(), command.getEndtime());
    }

    /** 校验 5202 诊断信息查询入参。 */
    public void validateDise5202(QueryDise5202Command command) {
        requirePsnNo(command);
        requireNonBlank(command.getMdtrtId(), "就诊ID");
    }

    /** 校验 5203 结算信息查询入参。 */
    public void validateSetl5203(QuerySetl5203Command command) {
        requirePsnNo(command);
        requireNonBlank(command.getMdtrtId(), "就诊ID");
        requireNonBlank(command.getSetlId(), "结算ID");
    }

    /** 校验 5204 费用明细查询入参。 */
    public void validateFee5204(QueryFee5204Command command) {
        requirePsnNo(command);
        requireNonBlank(command.getMdtrtId(), "就诊ID");
    }

    /** 校验 5205 慢特病用药记录查询入参。 */
    public void validateOpspDrug5205(QueryOpspDrug5205Command command) {
        requirePsnNo(command);
        requireNonBlank(command.getBegntime(), "开始时间");
        if (isNotEmpty(command.getEndtime())) {
            assertDateTimeRange(command.getBegntime(), command.getEndtime());
        }
    }

    /** 校验 5206 人员累计信息查询入参。 */
    public void validateCum5206(QueryCum5206Command command) {
        requirePsnNo(command);
        if (isNotEmpty(command.getCumYm()) && !command.getCumYm().trim().matches("\\d{6}")) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "累计年月格式应为 yyyyMM");
        }
    }

    /** 校验 5301 慢特病备案信息查询入参。 */
    public void validateOpspFiling5301(QueryOpspFiling5301Command command) {
        requirePsnNo(command);
    }

    private void requirePsnNo(MedicalInfoQueryBaseCommand command) {
        if (command == null || isEmpty(command.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "人员编号 psn_no 不能为空");
        }
        command.setPsnNo(command.getPsnNo().trim());
    }

    private void requireNonBlank(String value, String label) {
        if (isEmpty(value)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, label + "不能为空");
        }
    }

    private void assertDateTimeRange(String begntime, String endtime) {
        LocalDateTime begin = parseDateTime(begntime, "开始时间");
        LocalDateTime end = parseDateTime(endtime, "结束时间");
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束时间不能早于开始时间");
        }
        long days = ChronoUnit.DAYS.between(begin.toLocalDate(), end.toLocalDate());
        if (days > ChsMedicalInfoConstants.DEFAULT_MAX_RANGE_DAYS) {
            throw new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID,
                "查询跨度不能超过 " + ChsMedicalInfoConstants.DEFAULT_MAX_RANGE_DAYS + " 天");
        }
    }

    private LocalDateTime parseDateTime(String text, String label) {
        try {
            return LocalDateTime.parse(text.trim(), ChsMedicalInfoConstants.DATE_TIME_FMT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, label + "格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
