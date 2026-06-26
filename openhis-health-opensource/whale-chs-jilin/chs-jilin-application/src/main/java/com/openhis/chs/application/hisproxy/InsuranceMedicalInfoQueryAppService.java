/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 5201–5206、5301 bridge：{@code /api/app/insuranceMedicalInfoQuery/*}，委托 {@link ChsMedicalInfoQueryOperations}。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceMedicalInfoQueryAppService {

    private final ChsMedicalInfoQueryOperations medicalInfoQueryOperations;

    /**
     * 【5201】查询就诊信息。
     *
     * @param request 人员编号与就诊筛选
     * @return 就诊信息查询结果
     */
    public MedicalInfoQueryResult queryMdtrt5201(QueryMdtrt5201Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.queryMdtrt5201(request);
    }

    /**
     * 【5202】查询诊断信息。
     *
     * @param request 就诊 ID 与筛选条件
     * @return 诊断信息查询结果
     */
    public MedicalInfoQueryResult queryDise5202(QueryDise5202Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.queryDise5202(request);
    }

    /**
     * 【5203】查询结算信息。
     *
     * @param request 结算 ID 与筛选条件
     * @return 结算信息结果
     */
    public Setl5203Result querySetl5203(QuerySetl5203Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.querySetl5203(request);
    }

    /**
     * 【5204】查询费用明细。
     *
     * @param request 就诊 ID 与筛选条件
     * @return 费用明细查询结果
     */
    public MedicalInfoQueryResult queryFee5204(QueryFee5204Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.queryFee5204(request);
    }

    /**
     * 【5205】查询门慢门特用药记录。
     *
     * @param request 人员编号与筛选条件
     * @return 门慢门特用药查询结果
     */
    public MedicalInfoQueryResult queryOpspDrug5205(QueryOpspDrug5205Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.queryOpspDrug5205(request);
    }

    /**
     * 【5206】查询累计信息。
     *
     * @param request 人员编号与累计类型
     * @return 累计信息查询结果
     */
    public MedicalInfoQueryResult queryCum5206(QueryCum5206Request request) {
        // Cmd：委托 facade
        return medicalInfoQueryOperations.queryCum5206(request);
    }

    /**
     * 【5301】查询人员慢特病备案。
     *
     * @param request 人员编号与契约标识
     * @return 备案病种行（供慢病结算 dise_codg 校验）
     */
    public MedicalInfoQueryResult queryOpspFiling5301(QueryOpspFiling5301Request request) {
        // Cmd：委托 facade（Gate/平台 invoke 在 ChsMedicalInfoQueryAppService）
        return medicalInfoQueryOperations.queryOpspFiling5301(request);
    }
}
