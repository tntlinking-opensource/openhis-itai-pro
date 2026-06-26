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

package com.openhis.health.domain.administration.chargeitem.repository;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, JpaSpecificationExecutor<ChargeItem> {

    /**
     * 按主键集合批量查询收费明细。
     *
     * @param ids 主键集合
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByIdIn(Collection<Long> ids);

    /**
     * 按上游业务主键（service_id）集合查询关联明细（DM §3.1 service_table + service_id 锚点）。
     *
     * @param serviceIds 上游业务主键集合
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByServiceIdIn(Collection<Long> serviceIds);

    /**
     * 按就诊标识查询所有收费明细。
     *
     * @param encounterId 就诊标识
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterId(Long encounterId);

    /**
     * 按就诊标识集合批量查询收费明细（列表页一次加载多就诊下的明细，内存再按锚点过滤）。
     *
     * @param encounterIds 就诊主键集合
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdIn(Collection<Long> encounterIds);

    /**
     * 按就诊标识与收费上下文查询收费明细（DM-ADM-011 §6.2.4）。
     *
     * <p>区分门诊/住院等不同收费上下文。</p>
     *
     * @param encounterId 就诊标识
     * @param contextEnum 收费上下文枚举值
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndContextEnum(Long encounterId, String contextEnum);

    /**
     * 按就诊标识与状态查询收费明细（DM-ADM-011 §6.2.8 开票场景）。
     *
     * @param encounterId 就诊标识
     * @param statusEnum  状态枚举值
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndStatusEnum(Long encounterId, String statusEnum);

    /**
     * 按就诊标识与账户查询收费明细（DM-ADM-011 §6.2.7 结算单场景过渡方案）。
     *
     * <p>正式实现需通过支付结算单关联读模型完成（DM §6.2.7 BR-CHG-QRY-002）。</p>
     *
     * @param encounterId 就诊标识
     * @param accountId   账户标识
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndAccountId(Long encounterId, Long accountId);

    /**
     * 按就诊标识、收费上下文与状态复合查询（综合费用明细视图）。
     *
     * @param encounterId 就诊标识
     * @param contextEnum 收费上下文枚举值
     * @param statusEnum  状态枚举值
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndContextEnumAndStatusEnum(
        Long encounterId, String contextEnum, String statusEnum);

    /**
     * 批量就诊 + 状态集合 + 排除指定 {@code service_table}（门诊收银队列行挂载明细等，A.9.1）。
     *
     * @param encounterIds         就诊主键集合
     * @param statusEnums          收费状态持久化码集合
     * @param excludedServiceTable 排除的物理表名
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByEncounterIdInAndStatusEnumInAndServiceTableNot(
        Collection<Long> encounterIds, Collection<String> statusEnums, String excludedServiceTable);

    /**
     * 按就诊、上游业务锚点与状态精确查询收费明细（医嘱删行时清理草稿费用，AS-OP-DOR-003 IF-DOR-14）。
     *
     * @param encounterId 就诊标识
     * @param serviceTable 上游业务表名
     * @param serviceId 上游业务主键
     * @param statusEnum 收费状态码
     * @return 明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(
        Long encounterId, String serviceTable, Long serviceId, String statusEnum);

    /**
     * 按就诊、上游表名、多条上游主键与收费状态批量加载明细（门诊签发分段锚点多发放行，A.9.1）。
     *
     * @param encounterId  就诊标识
     * @param serviceTable 上游物理表名
     * @param serviceIds   上游主键集合
     * @param statusEnum   收费状态持久化码
     * @return 明细列表；任一为 {@code null} 或 {@code serviceIds} 为空时由调用方避免下发无效查询
     */
    List<ChargeItem> findAllByEncounterIdAndServiceTableAndServiceIdInAndStatusEnum(
        Long encounterId, String serviceTable, Collection<Long> serviceIds, String statusEnum);

    /**
     * 按就诊、上游表名与多条上游主键批量加载收费明细（取消划价费用 Gate，不限状态）。
     *
     * @param encounterId  就诊标识
     * @param serviceTable 上游物理表名
     * @param serviceIds   上游主键集合
     * @return 明细列表
     */
    List<ChargeItem> findAllByEncounterIdAndServiceTableAndServiceIdIn(
        Long encounterId, String serviceTable, Collection<Long> serviceIds);

    /**
     * 按就诊、发放表名、多条发放主键与收费状态批量加载明细。
     *
     * <p>用于门诊医生站签发/签退：{@code adm_charge_item.dispense_table} 与 {@code dispense_id} 与发放聚合对齐，
     * {@code encounter_id} 将结果集收敛到单次就诊，降低与其他就诊数据的混淆风险。</p>
     *
     * @param encounterId   就诊标识，与明细行就诊维度一致
     * @param dispenseTable 发放物理表名，与持久化列 {@code dispense_table} 一致
     * @param dispenseIds   发放主键集合；数量通常由单次医嘱 FIFO 分段条数限定，非全表候选集
     * @param statusEnum    收费状态持久化码
     * @return 匹配的收费明细行；无命中时返回空列表
     */
    List<ChargeItem> findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnum(
        Long encounterId, String dispenseTable, Collection<Long> dispenseIds, String statusEnum);

    /**
     * 按就诊、发放锚点与收费状态集合批量加载明细（门诊发药单金额回显等，状态多值一次 IN）。
     *
     * @param encounterId   就诊标识
     * @param dispenseTable 发放物理表名
     * @param dispenseIds   发放主键集合
     * @param statusEnums   收费状态持久化码集合（非空）
     * @return 明细列表；任一为 {@code null} 或集合为空时由调用方避免无效查询
     */
    List<ChargeItem> findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnumIn(
        Long encounterId, String dispenseTable, Collection<Long> dispenseIds, Collection<String> statusEnums);

    /**
     * 按半退保留链 {@code refund_id} 批量加载再生收费行（执行收口与读侧过滤）。
     *
     * @param refundIds 源已结算收费明细主键集合（对应 {@code adm_charge_item.refund_id}）
     * @return 再生行列表；{@code refundIds} 为空时返回空列表
     */
    List<ChargeItem> findAllByRefundIdIn(Collection<Long> refundIds);

    /**
     * 按执行记录主键集合批量查询收费明细（取消执行 Gate 与回退定位，DM-ADM-011 §6.2.10）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 收费明细列表；{@code procedureIds} 为空时返回空列表
     */
    List<ChargeItem> findAllByProcedureIdIn(Collection<Long> procedureIds);

    /**
     * 按执行记录、收费状态与生成来源复合查询（长期执行新建账单逻辑删除，DM-ADM-011 §6.1.5a）。
     *
     * @param procedureIds         执行记录主键集合
     * @param statusEnum           收费状态持久化码
     * @param generateSourceEnum   生成来源持久化码
     * @return 收费明细列表
     */
    List<ChargeItem> findAllByProcedureIdInAndStatusEnumAndGenerateSourceEnum(
        Collection<Long> procedureIds, String statusEnum, String generateSourceEnum);
}
