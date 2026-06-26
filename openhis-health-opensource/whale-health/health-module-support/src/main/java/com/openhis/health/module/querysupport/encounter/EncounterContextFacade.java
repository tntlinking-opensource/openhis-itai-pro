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

package com.openhis.health.module.querysupport.encounter;

import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 就诊上下文读侧统一入口（B.6.1.3）：AppService 注入本 Facade，由 {@code *EncounterContextLoader} 完成跨聚合组装。
 *
 * <p>领域 {@code EncounterQueryService} 不再承载患者/科室/诊断展示列；对外 Response 须经 Mapper 结构化诊断字段（B.6.2）。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterContextFacade {

    private final InpatientEncounterContextLoader inpatientEncounterContextLoader;
    private final OutpatientEncounterContextLoader outpatientEncounterContextLoader;
    private final ClinicRegistrationLoader clinicRegistrationLoader;

    /** 住院登记队列分页（入院办理工作台）。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientRegQueue(InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pageRegQueue(query);
    }

    /** 住院预交金队列分页。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientPrepayQueue(InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pagePrepayQueue(query);
    }

    /** 护士站住院患者队列分页。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientNurseStationQueue(
        InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pageNurseStationQueue(query);
    }

    /** 住院医生站患者队列分页。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientDoctorStationQueue(
        InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pageDoctorStationQueue(query);
    }

    /** 住院结算队列分页。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientSettleQueue(InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pageSettleQueue(query);
    }

    /** 通用住院就诊上下文分页列表。 */
    public Page<InpatientEncounterContextQueryModel> pageInpatientEncounterContexts(
        InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.pageInpatientEncounterContexts(query);
    }

    /** 住院就诊上下文全量列表（无分页，调用方须控制查询范围）。 */
    public List<InpatientEncounterContextQueryModel> listInpatientEncounterContexts(
        InpatientEncounterPageQuery query) {
        return this.inpatientEncounterContextLoader.listInpatientEncounterContexts(query);
    }

    /**
     * 按就诊主键加载住院上下文（不存在时 empty）。
     *
     * @param encounterId 就诊主键
     * @return 上下文快照
     */
    public Optional<InpatientEncounterContextQueryModel> getInpatientContextByEncounterId(Long encounterId) {
        return this.inpatientEncounterContextLoader.getInpatientContextByEncounterId(encounterId);
    }

    /**
     * 按调用方给定顺序批量加载住院上下文（用于外部分页后的 encounterId 列表回填）。
     *
     * @param encounterIdsOrdered 就诊主键列表（顺序保留）
     * @return 与输入顺序一致的上下文列表（缺失项跳过）
     */
    public List<InpatientEncounterContextQueryModel> listInpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        return this.inpatientEncounterContextLoader.listInpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
    }

    /**
     * 按就诊主键加载住院上下文（不存在时抛业务异常，由 Loader 定义）。
     *
     * @param encounterId 就诊主键
     * @return 上下文快照
     */
    public InpatientEncounterContextQueryModel mustGetInpatientContextByEncounterId(Long encounterId) {
        return this.inpatientEncounterContextLoader.mustGetInpatientContextByEncounterId(encounterId);
    }

    /** 门诊就诊上下文分页列表。 */
    public Page<OutpatientEncounterContextQueryModel> pageOutpatientEncounterContexts(
        OutpatientEncounterPageQuery query) {
        return this.outpatientEncounterContextLoader.pageOutpatientEncounterContexts(query);
    }

    /** 门诊护士站患者队列分页。 */
    public Page<OutpatientEncounterContextQueryModel> pageNurseStationOutpatientQueue(AbstractPageQuery query) {
        return this.outpatientEncounterContextLoader.pageNurseStationOutpatientQueue(query);
    }

    /**
     * 按就诊主键加载门诊上下文（不存在时 empty）。
     *
     * @param encounterId 就诊主键
     * @return 上下文快照
     */
    public Optional<OutpatientEncounterContextQueryModel> getOutpatientContextByEncounterId(Long encounterId) {
        return this.outpatientEncounterContextLoader.getOutpatientContextByEncounterId(encounterId);
    }

    /**
     * 按顺序批量加载门诊上下文。
     *
     * @param encounterIdsOrdered 就诊主键列表
     * @return 上下文列表
     */
    public List<OutpatientEncounterContextQueryModel> listOutpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        return this.outpatientEncounterContextLoader.listOutpatientContextsByEncounterIdsOrdered(encounterIdsOrdered);
    }

    /**
     * 按就诊主键加载门诊上下文（不存在时抛业务异常）。
     *
     * @param encounterId 就诊主键
     * @return 上下文快照
     */
    public OutpatientEncounterContextQueryModel mustGetOutpatientContextByEncounterId(Long encounterId) {
        return this.outpatientEncounterContextLoader.mustGetOutpatientContextByEncounterId(encounterId);
    }

    /** 门诊挂号列表分页。 */
    public Page<ClinicRegisterQueryModel> pageClinicRegister(ClinicRegisterPageQuery query) {
        return this.clinicRegistrationLoader.pageClinicRegister(query);
    }

    /** 门诊挂号列表计数。 */
    public long countClinicRegister(ClinicRegisterPageQuery query) {
        return this.clinicRegistrationLoader.countClinicRegister(query);
    }

    /** 挂号单列表分页。 */
    public Page<RegistrationSlipQueryModel> pageRegistrationSlip(RegistrationSlipPageQuery query) {
        return this.clinicRegistrationLoader.pageRegistrationSlip(query);
    }

    /**
     * 按就诊主键加载挂号单快照（不存在时 empty）。
     *
     * @param encounterId 就诊主键
     * @return 挂号单读模型
     */
    public Optional<RegistrationSlipQueryModel> getRegistrationSlipByEncounterId(Long encounterId) {
        return this.clinicRegistrationLoader.getRegistrationSlipByEncounterId(encounterId);
    }
}
