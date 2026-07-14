/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import com.openhis.health.domain.share.constants.TableNameConstants;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 收费明细查询入参命令（DM-ADM-011 §6.2）。
 *
 * <p>字段按查询场景选择性填充；读侧实现根据非空字段组合过滤条件。serviceIds 对应 adm_charge_item.service_id。</p>
 */
@Getter
@Setter
public class ChargeItemQueryCommand {

    /** 就诊标识（多数查询的基本过滤维度）。 */
    private Long encounterId;

    /**
     * 诊疗项目定义主键（可选；吸氧/心电累计用量见 {@link com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService#sumQuantityHoursByEncounterAndActivity}）。
     */
    private Long activityDefinitionId;

    /**
     * 发生日期（可选；非空时仅统计该自然日内的累计用量，用于吸氧/心电当日 Gate）。
     */
    private LocalDate occurrenceDate;

    /**
     * 就诊标识集合（批量按就诊加载明细；与 {@code encounterId} 互斥场景由调用方约定）。
     */
    private Collection<Long> encounterIds;

    /** 主键集合（批量主键读取）。 */
    private Collection<Long> ids;

    /** 上游业务主键集合（与 adm_charge_item.service_id 对应）。 */
    private Collection<Long> serviceIds;

    /**
     * 收费上下文枚举（可选）。
     *
     * <p>区分门诊/住院收费上下文；用于 queryCostDetails/queryInpatientCostDetails 等场景。</p>
     */
    private String contextEnum;

    /**
     * 收费状态枚举（可选）。
     *
     * <p>对齐 DM-ADM-011 §2.3 状态机定义；用于按结算状态过滤明细。</p>
     */
    private String statusEnum;

    /**
     * 账户标识（可选）。
     *
     * <p>用于按账户（自费/医保账户）聚合收费明细视图；listChargeItemsByPaymentBill 等场景。</p>
     */
    private Long accountId;

    /**
     * 排除的上游业务表锚点（可选，对应 {@code adm_charge_item.service_table}）。
     *
     * <p>非空且 trim 后非空时，查询在 SQL 层附加 {@code service_table != excludedServiceTable}，
     * 用于门诊收银待收费列表等场景排除挂号费锚点（{@link TableNameConstants#ADM_HEALTHCARE_SERVICE}）。</p>
     */
    private String excludedServiceTable;

    /**
     * 收费状态持久化码多值过滤（可选；对应 {@code adm_charge_item.status_enum}）。
     *
     * <p>非空时与 {@link #statusEnum} 互斥语义由查询服务消化：优先按集合 {@code IN} 过滤；
     * 未传集合时才回落单值 {@link #statusEnum}。</p>
     */
    private List<String> statusEnumIn;

    /**
     * 是否排除「半退保留链已再生」的源 {@code SETTLED} 行（BR-REF-020 / BR-OPC-010）。
     *
     * <p>为 {@code true} 时：若同就诊存在 {@code PLANNED} 或 {@code PENDING_SETTLEMENT} 且 {@code refund_id} 指向本行，则本行不出现在结果中。
     * 门诊收银待收列表、住院费用明细、医生站可退列表等读侧默认开启，避免同锚点双行。</p>
     */
    private Boolean excludeSupersededSettledLines;

    /**
     * 业务日期下界（闭区间；AS-IP-NUR-006 费用明细：划价取 {@code occurrence_time} 自然日，医嘱取净剩 Procedure 自然日）。
     */
    private LocalDate businessDateFrom;

    /**
     * 业务日期上界（闭区间；与 {@link #businessDateFrom} 成对使用）。
     */
    private LocalDate businessDateTo;
}
