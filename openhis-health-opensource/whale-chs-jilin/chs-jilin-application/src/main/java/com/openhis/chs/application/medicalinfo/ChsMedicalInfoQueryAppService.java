/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.medicalinfo;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.support.ChsMedicalInfoOutputParser;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.application.medicalinfo.assembler.ChsMedicalInfoPlatformAssembler;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.domain.medicalinfo.constants.ChsMedicalInfoConstants;
import com.openhis.chs.domain.medicalinfo.dto.request.MedicalInfoQueryBaseRequest;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryCum5206Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryDise5202Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryFee5204Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryMdtrt5201Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspDrug5205Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QueryOpspFiling5301Request;
import com.openhis.chs.domain.medicalinfo.dto.request.QuerySetl5203Request;
import com.openhis.chs.domain.medicalinfo.dto.response.MedicalInfoQueryResult;
import com.openhis.chs.domain.medicalinfo.dto.response.Setl5203Result;
import com.openhis.chs.domain.medicalinfo.facade.ChsMedicalInfoQueryOperations;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 医保信息查询应用服务（5201–5206）：入参校验、契约与操作人解析、平台 invoke 及出参解析。
 * <p>
 * 职责：实现 {@link ChsMedicalInfoQueryOperations}，将 bridge/hisproxy 请求编排为省平台交易，
 * 不负责院内业务状态变更。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsMedicalInfoQueryAppService implements ChsMedicalInfoQueryOperations {

    private final InsuranceGatewayClient gatewayClient;
    private final ChsInsuranceContractPort contractPort;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /**
     * 【5201】就诊信息查询。
     *
     * @param request 人员编号、起止时间、医疗类别及契约标识
     * @return 平台返回的就诊信息列表
     * @throws BusinessException 必填项缺失、时间格式/跨度非法或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryMdtrt5201(QueryMdtrt5201Request request) {
        // Gate：人员编号、起止时间与医疗类别必填；时间跨度不超过平台允许上限
        requirePsnNo(request);
        requireNonBlank(request.getBegntime(), "开始时间");
        requireNonBlank(request.getEndtime(), "结束时间");
        requireNonBlank(StringUtils.defaultIfBlank(request.getMedType(), "11"), "医疗类别");
        assertDateTimeRange(request.getBegntime(), request.getEndtime());
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5201 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5201(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.MDTRT_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.MDTRT_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5201(body, ChsInfNo.MDTRT_QUERY);
    }

    /**
     * 【5202】诊断信息查询。
     *
     * @param request 人员编号、就诊 ID 及契约标识
     * @return 平台返回的诊断信息列表
     * @throws BusinessException 必填项缺失或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryDise5202(QueryDise5202Request request) {
        // Gate：人员编号与就诊 ID 必填
        requirePsnNo(request);
        requireNonBlank(request.getMdtrtId(), "就诊ID");
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5202 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5202(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.DISE_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.DISE_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5202(body, ChsInfNo.DISE_QUERY);
    }

    /**
     * 【5203】结算信息查询。
     *
     * @param request 人员编号、就诊 ID、结算 ID 及契约标识
     * @return 平台返回的结算明细（含基金分项等）
     * @throws BusinessException 必填项缺失或平台拒绝时
     */
    @Override
    public Setl5203Result querySetl5203(QuerySetl5203Request request) {
        // Gate：人员编号、就诊 ID 与结算 ID 必填
        requirePsnNo(request);
        requireNonBlank(request.getMdtrtId(), "就诊ID");
        requireNonBlank(request.getSetlId(), "结算ID");
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5203 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5203(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.SETL_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.SETL_QUERY);
        // Asm：解析平台出参为结算 DTO
        return ChsMedicalInfoOutputParser.parse5203(body, ChsInfNo.SETL_QUERY);
    }

    /**
     * 【5204】费用明细查询。
     *
     * @param request 人员编号、就诊 ID 及契约标识
     * @return 平台返回的费用明细列表
     * @throws BusinessException 必填项缺失或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryFee5204(QueryFee5204Request request) {
        // Gate：人员编号与就诊 ID 必填
        requirePsnNo(request);
        requireNonBlank(request.getMdtrtId(), "就诊ID");
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5204 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5204(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.FEE_DETAIL_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.FEE_DETAIL_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5204(body, ChsInfNo.FEE_DETAIL_QUERY);
    }

    /**
     * 【5205】门慢门特用药查询。
     *
     * @param request 人员编号、开始时间、可选结束时间及契约标识
     * @return 平台返回的用药信息列表
     * @throws BusinessException 必填项缺失、时间格式/跨度非法或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryOpspDrug5205(QueryOpspDrug5205Request request) {
        // Gate：人员编号与开始时间必填
        requirePsnNo(request);
        requireNonBlank(request.getBegntime(), "开始时间");
        // Gate：结束时间非空时校验格式与跨度
        if (StringUtils.isNotBlank(request.getEndtime())) {
            assertDateTimeRange(request.getBegntime(), request.getEndtime());
        }
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5205 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5205(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.OPSP_DRUG_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.OPSP_DRUG_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5205(body, ChsInfNo.OPSP_DRUG_QUERY);
    }

    /**
     * 【5206】人员累计信息查询。
     *
     * @param request 人员编号、可选累计年月及契约标识
     * @return 平台返回的累计信息列表
     * @throws BusinessException 人员编号缺失、累计年月格式非法或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryCum5206(QueryCum5206Request request) {
        // Gate：人员编号必填
        requirePsnNo(request);
        // Gate：累计年月非空时须为 yyyyMM 六位数字
        if (StringUtils.isNotBlank(request.getCumYm()) && !request.getCumYm().trim().matches("\\d{6}")) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "累计年月格式应为 yyyyMM");
        }
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5206 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5206(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.PERSON_CUM_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.PERSON_CUM_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5206(body, ChsInfNo.PERSON_CUM_QUERY);
    }

    /**
     * 【5301】人员慢特病备案查询。
     *
     * @param request 人员编号及契约标识
     * @return 备案病种行列表（opsp_dise_code/name + 有效期）
     * @throws BusinessException 人员编号缺失或平台拒绝时
     */
    @Override
    public MedicalInfoQueryResult queryOpspFiling5301(QueryOpspFiling5301Request request) {
        // Gate：人员编号必填（慢病结算前校验 dise_codg 是否在备案有效期内）
        requirePsnNo(request);
        // Pre：解析契约 bus_no 与网关配置
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        // Cmd：组装 5301 入参并调平台
        JSONObject input = ChsMedicalInfoPlatformAssembler.assemble5301(request);
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.PERSON_OPSP_FILING_QUERY, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.PERSON_OPSP_FILING_QUERY);
        // Asm：解析平台出参为领域 DTO
        return ChsMedicalInfoOutputParser.parse5301(body, ChsInfNo.PERSON_OPSP_FILING_QUERY);
    }

    private void requirePsnNo(MedicalInfoQueryBaseRequest request) {
        if (request == null || StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "人员编号 psn_no 不能为空");
        }
        request.setPsnNo(request.getPsnNo().trim());
    }

    private void requireNonBlank(String value, String label) {
        if (StringUtils.isBlank(value)) {
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

    private ContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractPort.requireContract(contractBusNo, contractId);
        String busNo = contractPort.requireContractBusNo(contract);
        ContractConfig config = ContractConfigLoader.getByBusNo(busNo);
        return new ContractContext(contract, busNo, config);
    }

    private InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InvokeContext partial = InvokeContext.builder()
            .operatorId(String.valueOf(operatorId))
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    private record ContractContext(ChsContractSnapshot contract, String busNo, ContractConfig config) {
    }
}
