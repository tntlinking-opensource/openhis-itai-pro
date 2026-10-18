/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.medicalinfo.service;

import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5201QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5202QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5203QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5204QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5205QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5206QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.access.Med5301QueryInvoker;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryCum5206Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryDise5202Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryFee5204Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryMdtrt5201Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspDrug5205Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QueryOpspFiling5301Command;
import com.zxplat.plugin.chs.domain.medicalinfo.command.QuerySetl5203Command;
import com.zxplat.plugin.chs.domain.medicalinfo.result.MedicalInfoQueryResult;
import com.zxplat.plugin.chs.domain.medicalinfo.result.Setl5203Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 医保信息查询 UI 应用服务（{@code POST /api/app/insuranceMedicalInfoQuery/*}）。
 * <p>职责：5201–5301 同页多查询；单 infno 直调 domain access 端口，无中间编排层。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceMedicalInfoQueryAppService {

    private final Med5201QueryInvoker med5201Invoker;
    private final Med5202QueryInvoker med5202Invoker;
    private final Med5203QueryInvoker med5203Invoker;
    private final Med5204QueryInvoker med5204Invoker;
    private final Med5205QueryInvoker med5205Invoker;
    private final Med5206QueryInvoker med5206Invoker;
    private final Med5301QueryInvoker med5301Invoker;

    /** 【5201】就诊信息查询。 */
    public MedicalInfoQueryResult queryMdtrt5201(QueryMdtrt5201Command request) {
        return med5201Invoker.invoke(request);
    }

    /** 【5202】诊断信息查询。 */
    public MedicalInfoQueryResult queryDise5202(QueryDise5202Command request) {
        return med5202Invoker.invoke(request);
    }

    /** 【5203】结算信息查询。 */
    public Setl5203Result querySetl5203(QuerySetl5203Command request) {
        return med5203Invoker.invoke(request);
    }

    /** 【5204】费用明细查询。 */
    public MedicalInfoQueryResult queryFee5204(QueryFee5204Command request) {
        return med5204Invoker.invoke(request);
    }

    /** 【5205】人员慢特病用药记录查询。 */
    public MedicalInfoQueryResult queryOpspDrug5205(QueryOpspDrug5205Command request) {
        return med5205Invoker.invoke(request);
    }

    /** 【5206】人员累计信息查询。 */
    public MedicalInfoQueryResult queryCum5206(QueryCum5206Command request) {
        return med5206Invoker.invoke(request);
    }

    /** 【5301】人员慢特病备案信息查询。 */
    public MedicalInfoQueryResult queryOpspFiling5301(QueryOpspFiling5301Command request) {
        return med5301Invoker.invoke(request);
    }
}
