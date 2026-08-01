/*

 * Copyright © 2026 OpenHIS Contributors

 * SPDX-License-Identifier: LGPL-3.0-or-later

 */

package com.openhis.wi.bridge.context;



import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;



import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;

import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;



import java.util.Comparator;

import java.util.List;



/**

 * bridge 侧：就诊主诊断 → 国家医保病种编码（{@code cln_condition_definition.yb_no}）。

 *

 * <p>直读 {@code adm_encounter_diagnosis.condition_definition_id}，不经 {@code cln_condition}。

 * 供 {@link ChsWorkInjurySettleContextLoader} 填充

 * {@link com.openhis.wi.domain.settle.command.WiSettleContext#admDiagCode}。</p>

 */

public final class InsuranceDiagnosisCodeSupport {



    private InsuranceDiagnosisCodeSupport() {

    }



    /**

     * 主诊断编码与名称。

     *

     * @param code 国家医保病种编码（yb_no 优先）

     * @param name 病种名称

     */

    public record MainDiagnosis(String code, String name) {

    }



    /**

     * 解析就诊主诊断的国家编码与名称。

     *

     * @param encounterId                     就诊 id

     * @param encounterQueryService           就诊查询

     * @param conditionDefinitionQueryService 病种定义（yb_no）

     * @return 主诊断；无诊断时 code/name 均为 null

     */

    public static MainDiagnosis resolveMainDiagnosis(

        Long encounterId,

        EncounterQueryService encounterQueryService,

        ConditionDefinitionQueryService conditionDefinitionQueryService) {

        Long definitionId = resolvePrimaryDefinitionId(encounterId, encounterQueryService);

        if (definitionId == null) {

            return new MainDiagnosis(null, null);

        }

        String code = resolveNationalDiagCodeForDefinitionId(definitionId, conditionDefinitionQueryService);

        String name = resolveDefinitionNameForDefinitionId(definitionId, conditionDefinitionQueryService, null);

        return new MainDiagnosis(code, name);

    }



    /**

     * 按病种定义 id 解析国家医保编码。

     *

     * @param definitionId                    病种定义 id

     * @param conditionDefinitionQueryService 病种定义查询

     * @return yb_no 优先，否则 condition_code；无则 null

     */

    public static String resolveNationalDiagCodeForDefinitionId(

        Long definitionId,

        ConditionDefinitionQueryService conditionDefinitionQueryService) {

        if (definitionId == null || conditionDefinitionQueryService == null) {

            return null;

        }

        List<ConditionDefinitionBatchItemQueryModel> batch =

            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));

        if (batch.isEmpty()) {

            return null;

        }

        ConditionDefinitionBatchItemQueryModel item = batch.get(0);

        return resolveNationalDiagCode(item.getYbNo(), item.getConditionCode());

    }



    /**

     * 按病种定义 id 解析病种名称。

     *

     * @param definitionId                    病种定义 id

     * @param conditionDefinitionQueryService 病种定义查询

     * @param fallbackDescription             无名称时的回退文案

     * @return 病种名称或 fallback

     */

    public static String resolveDefinitionNameForDefinitionId(

        Long definitionId,

        ConditionDefinitionQueryService conditionDefinitionQueryService,

        String fallbackDescription) {

        if (definitionId == null || conditionDefinitionQueryService == null) {

            return fallbackDescription;

        }

        List<ConditionDefinitionBatchItemQueryModel> batch =

            conditionDefinitionQueryService.getConditionDefinitionListByIds(List.of(definitionId));

        if (!batch.isEmpty() && isNotEmpty(batch.get(0).getName())) {

            return batch.get(0).getName().trim();

        }

        return fallbackDescription;

    }



    /**

     * 解析就诊主诊断对应的病种定义 id（直读就诊诊断行上的 conditionDefinitionId）。

     *

     * <p>优先 primaryFlag=true；否则取 rank 最小；再否则首条。</p>

     *

     * @param encounterId           就诊 id

     * @param encounterQueryService 就诊诊断关联

     * @return 主诊断 definitionId；无诊断时 null

     */

    public static Long resolvePrimaryDefinitionId(

        Long encounterId,

        EncounterQueryService encounterQueryService) {

        List<EncounterDiagnosisQueryModel> associations =

            encounterQueryService.listEncounterDiagnoses(encounterId);

        if (associations.isEmpty()) {

            return null;

        }

        EncounterDiagnosisQueryModel chosen = null;

        for (EncounterDiagnosisQueryModel row : associations) {

            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {

                chosen = row;

                break;

            }

        }

        if (chosen == null) {

            associations.sort(Comparator.comparing(

                EncounterDiagnosisQueryModel::getRank,

                Comparator.nullsLast(Comparator.naturalOrder())));

            chosen = associations.get(0);

        }

        return chosen.getConditionDefinitionId();

    }



    /**

     * yb_no 与 condition_code 择优为国家医保编码。

     *

     * @param ybNo          国家医保编码

     * @param conditionCode 院内/ICD 编码

     * @return 非空 trim 后的编码；均空则 null

     */

    public static String resolveNationalDiagCode(String ybNo, String conditionCode) {

        if (isNotEmpty(ybNo)) {

            return ybNo.trim();

        }

        if (isNotEmpty(conditionCode)) {

            return conditionCode.trim();

        }

        return null;

    }

}


