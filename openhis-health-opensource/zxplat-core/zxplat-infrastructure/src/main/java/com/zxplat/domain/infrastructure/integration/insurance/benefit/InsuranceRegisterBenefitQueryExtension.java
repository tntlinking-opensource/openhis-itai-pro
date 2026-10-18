/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.benefit;

import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceCumulativeQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceEncounterPsnSnapshot;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 门诊挂号待遇确认前的第三方信息查询扩展点（1101/2001/5301/5206 及就医地区划解析）。
 *
 * <p><b>调用方</b>：{@code OutpatientRegistrationAppService} 待遇预览/确认流程。</p>
 * <p><b>吉林实现</b>：{@code InsuranceRegisterBenefitQueryExtensionImpl}（module-chs-jilin-bridge）；1101 经 {@code Op1101Invoker}。</p>
 */
public interface InsuranceRegisterBenefitQueryExtension {

    /** 是否已装配第三方查询 Module。 */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 费用性质是否走第三方查询。
     *
     * @param contractId {@code fin_contract.id}
     */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 读卡获取参保人基本信息与险种列表（1101）。
     */
    default InsurancePersonQueryExtensionOut queryPerson(InsurancePersonQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方参保人信息查询未装配");
    }

    /**
     * 人员待遇享受检查（2001）。
     */
    default InsuranceTreatmentCheckExtensionOut checkTreatment(InsuranceTreatmentCheckExtensionIn command) {
        throw new UnsupportedOperationException("第三方待遇检查未装配");
    }

    /**
     * 慢特病备案查询（5301）。
     */
    default InsuranceOpspFilingQueryExtensionOut queryOpspFiling(InsuranceOpspFilingQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方慢特病备案查询未装配");
    }

    /**
     * 解析就诊侧 5301 所需人员编号与费用性质（医生站 IF-DOR-06B）。
     *
     * <p>实现可依次回退：平台就诊映射 → 预结算快照 → 读卡当日人员缓存；
     * 均不可用时返回 {@code null}（自费/未读卡）。</p>
     *
     * @param encounterId 就诊主键
     * @return 上下文；未启用或无可用人员编号时 {@code null}
     */
    default InsuranceOpspFilingEncounterSnapshot resolveOpspFilingEncounter(Long encounterId) {
        return null;
    }

    /**
     * 人员累计信息查询（5206）。
     */
    default InsuranceCumulativeQueryExtensionOut queryCumulative(InsuranceCumulativeQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方累计信息查询未装配");
    }

    /**
     * 解析费用性质对应的就医地医保区划（契约 adm_vs）。
     *
     * @param contractId 费用性质 ID
     * @return 就医地区划，未配置时 {@code null}
     */
    default String resolveTreatmentAreaCode(Long contractId) {
        return null;
    }

    /**
     * 按就诊批量解析医保人员编号（平台 {@code psn_no}）。
     *
     * <p>实现侧应从就诊平台映射等本地落库批量读取，禁止按行远程调平台。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 人员编号；无映射或不含键；未启用第三方时返回空 Map
     */
    default Map<Long, String> mapPsnNoByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, InsuranceEncounterPsnSnapshot> snapshots = mapEncounterPsnByEncounterIds(encounterIds);
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyMap();
        }
            Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, InsuranceEncounterPsnSnapshot> entry : snapshots.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String psnNo = entry.getValue().getPsnNo();
            if (psnNo == null || psnNo.trim().isEmpty()) {
                continue;
            }
            result.put(entry.getKey(), psnNo.trim());
        }
        return result;
    }

    /**
     * 按就诊批量解析医保人员编号与医疗类别（本地平台映射）。
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → 展示快照；无映射或不含键
     */
    default Map<Long, InsuranceEncounterPsnSnapshot> mapEncounterPsnByEncounterIds(
        Collection<Long> encounterIds) {
        return Collections.emptyMap();
    }

    /**
     * 按就诊批量解析险种类型码（310 职工 / 390 城乡居民等）。
     *
     * <p>实现侧应从本地落库批量读取（人员缓存/预结算等），禁止按行远程调平台。
     * 读卡缓存允许含已过期行（住院跨日仍需展示职工/居民）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → insutype；无映射不含键；未启用第三方时返回空 Map
     */
    default Map<Long, String> mapInsutypeByEncounterIds(Collection<Long> encounterIds) {
        return Collections.emptyMap();
    }
}
