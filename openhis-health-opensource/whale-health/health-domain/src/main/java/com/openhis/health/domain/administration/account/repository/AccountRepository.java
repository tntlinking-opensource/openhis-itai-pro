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

package com.openhis.health.domain.administration.account.repository;

import com.openhis.health.domain.administration.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 就诊账户仓储：按就诊与类型定位账户，支撑计费入账与默认账户解析。
 */
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
    /**
     * 按主键加载账户。
     *
     * @param id 账户主键
     * @return 存在时的账户
     */
    Optional<Account> findById(Long id);

    /**
     * 列出某就诊下的全部账户（多类型并存场景）。
     *
     * @param encounterId 就诊主键
     * @return 账户列表
     */
    List<Account> findAllByEncounterId(Long encounterId);

    /**
     * 解析就诊下某类型的默认账户（用于自动扣费入口）。
     *
     * @param encounterId 就诊主键
     * @param typeCode    账户类型编码
     * @return 默认账户或空
     */
    Optional<Account> findFirstByEncounterIdAndTypeCodeAndDefaultFlagTrue(Long encounterId, String typeCode);

    /**
     * 解析就诊下某类型的默认且有效账户（{@code status_enum} 须为有效态 code）。
     *
     * @param encounterId 就诊主键
     * @param typeCode    账户类型编码
     * @param statusEnum  主状态 code（如 {@link com.openhis.health.domain.share.enums.PublicationStatus#ACTIVE}）
     * @return 默认有效账户或空
     */
    Optional<Account> findFirstByEncounterIdAndTypeCodeAndDefaultFlagTrueAndStatusEnum(
        Long encounterId, String typeCode, String statusEnum);

    /**
     * 按患者、就诊与类型唯一定位账户（常用于幂等创建前探测）。
     *
     * @param patientId   患者主键
     * @param encounterId 就诊主键
     * @param typeCode    账户类型编码
     * @return 匹配账户或空
     */
    Optional<Account> findFirstByPatientIdAndEncounterIdAndTypeCode(Long patientId, Long encounterId, String typeCode);

    /**
     * 列出某就诊下指定主状态的有效账户行。
     *
     * @param encounterId 就诊主键
     * @param statusEnum  主状态 code
     * @return 账户列表
     */
    List<Account> findAllByEncounterIdAndStatusEnum(Long encounterId, String statusEnum);

    /**
     * 按就诊主键集合与账户类型批量加载账户（预交金在院队列余额组装，避免 N+1）。
     *
     * @param encounterIds 就诊主键集合
     * @param typeCode     账户类型编码
     * @return 匹配账户列表
     */
    List<Account> findByEncounterIdInAndTypeCode(Collection<Long> encounterIds, String typeCode);
}
