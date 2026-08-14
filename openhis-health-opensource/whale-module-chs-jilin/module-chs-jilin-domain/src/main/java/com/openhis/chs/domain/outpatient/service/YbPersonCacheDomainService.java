/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.domain.outpatient.result.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.command.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.repository.YbPersonCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 1101 人员信息当日缓存 {@code yb_person_cache} */
@Service
@Slf4j
@RequiredArgsConstructor
public class YbPersonCacheDomainService {

    private static final String PERSON_CACHE_CERTNO_ALIAS_TYPE = "02";
    private static final ConcurrentHashMap<String, Object> PERSON_CACHE_UPSERT_LOCKS = new ConcurrentHashMap<>();

    private final YbPersonCacheRepository personCacheRepository;

    /**
     * 查找当日未过期的人员信息缓存（优先身份证号别名键）。
     *
     * @param command  1101 查询命令（含凭证类型与凭证号）
     * @param tenantId 租户 ID
     * @return 有效缓存行；未命中或已过期时返回空
     */
    public Optional<YbPersonCache> findValidPersonCache(YbPersonQueryCommand command, Long tenantId) {
        String certno = resolvePersonCacheCertno(command, null);
        // 卡串每次读卡可能变化；有身份证号时优先命中稳定别名键
        if (certno != null) {
            Optional<YbPersonCache> byCertno = findPersonCacheRow(
                command.getContractId(), PERSON_CACHE_CERTNO_ALIAS_TYPE, certno, tenantId);
            if (byCertno.isPresent() && isPersonCacheValid(byCertno.get())) {
                return byCertno;
            }
        }
        String mdtrtCertNo = trimToEmpty(command.getMdtrtCertNo());
        if (!isEmpty(mdtrtCertNo)) {
            Optional<YbPersonCache> byCredential = findPersonCacheRow(
                command.getContractId(),
                command.getMdtrtCertType(),
                mdtrtCertNo,
                tenantId);
            if (byCredential.isPresent() && isPersonCacheValid(byCredential.get())) {
                return byCredential;
            }
        }
        // Pre：仅有 03 卡串主行、无 02 别名时，按 snapshot.certno 回落（签发补 psn/参保地）
        if (certno != null && command.getContractId() != null && tenantId != null) {
            return findValidBySnapshotCertno(command.getContractId(), certno, tenantId);
        }
        return Optional.empty();
    }

    /**
     * 按契约 + 身份证号查找当日有效人员缓存（读卡后、尚未 2201 建档时供 5301 解析 psnNo）。
     *
     * @param contractId 费用性质契约主键
     * @param idCard     患者身份证号
     * @param tenantId   租户 ID
     * @return 有效缓存行；参数不全或未命中时返回空
     */
    public Optional<YbPersonCache> findValidByContractIdAndIdCard(
        Long contractId, String idCard, Long tenantId) {
        if (contractId == null || tenantId == null || isEmpty(idCard)) {
            return Optional.empty();
        }
        YbPersonQueryCommand command = new YbPersonQueryCommand();
        command.setContractId(contractId);
        command.setMdtrtCertType(PERSON_CACHE_CERTNO_ALIAS_TYPE);
        command.setMdtrtCertNo(idCard.trim());
        command.setCertno(idCard.trim());
        return findValidPersonCache(command, tenantId);
    }

    /**
     * 按契约 + 身份证号查找最新一条人员缓存（含已过期）。
     *
     * <p>优先身份证别名键（{@code mdtrt_cert_type=02}）；医生站险种回显不要求当日有效。</p>
     *
     * @param contractId 费用性质契约主键
     * @param idCard     患者身份证号
     * @param tenantId   租户 ID
     * @return 最新缓存行；参数不全或未命中时返回空
     */
    public Optional<YbPersonCache> findLatestByContractIdAndIdCard(
        Long contractId, String idCard, Long tenantId) {
        if (contractId == null || tenantId == null || isEmpty(idCard)) {
            return Optional.empty();
        }
        String certno = normalizePersonCacheCertno(idCard);
        if (certno == null) {
            certno = idCard.trim();
        }
        return personCacheRepository
            .findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalseOrderByCachedAtDesc(
                contractId, PERSON_CACHE_CERTNO_ALIAS_TYPE, certno, tenantId);
    }

    /**
     * 按契约 + 身份证号查找当日未过期的读卡主凭证缓存。
     *
     * <p>优先 {@code 01}/{@code 03}（电子凭证/社保卡）；若当日仅以身份证读卡则允许 {@code 02}。
     * 避免优先命中仅作别名键的 {@code 02} 行。按 {@code cached_at} 倒序。</p>
     *
     * @param contractId 费用性质契约主键
     * @param idCard     患者身份证号（匹配 snapshot.certno）
     * @param tenantId   租户 ID
     * @return 读卡主凭证缓存行；参数不全或未命中时返回空
     */
    public Optional<YbPersonCache> findValidPrimaryReadCertByContractIdAndIdCard(
        Long contractId, String idCard, Long tenantId) {
        if (contractId == null || tenantId == null || isEmpty(idCard)) {
            return Optional.empty();
        }
        String normalized = normalizePersonCacheCertno(idCard);
        if (normalized == null) {
            return Optional.empty();
        }
        List<YbPersonCache> rows = personCacheRepository
            .findByContractIdAndTenantIdAndIsDeletedFalseAndExpireAtAfterOrderByCachedAtDesc(
                contractId, tenantId, LocalDateTime.now());
        YbPersonCache fallback02 = null;
        for (YbPersonCache row : rows) {
            if (row == null || !isPersonCacheValid(row)) {
                continue;
            }
            if (isEmpty(row.getMdtrtCertNo()) || isEmpty(row.getSnapshotJson())) {
                continue;
            }
            if (!row.getSnapshotJson().toUpperCase().contains(normalized)) {
                continue;
            }
            String type = trimToEmpty(row.getMdtrtCertType());
            if ("01".equals(type) || "03".equals(type)) {
                return Optional.of(row);
            }
            if ("02".equals(type) && fallback02 == null) {
                fallback02 = row;
            }
        }
        return Optional.ofNullable(fallback02);
    }

    /**
     * @deprecated 请用 {@link #findValidPrimaryReadCertByContractIdAndIdCard}；保留兼容旧调用。
     */
    @Deprecated
    public Optional<YbPersonCache> findValidElectronicCertByContractIdAndIdCard(
        Long contractId, String idCard, Long tenantId) {
        return findValidPrimaryReadCertByContractIdAndIdCard(contractId, idCard, tenantId)
            .filter(row -> "01".equals(trimToEmpty(row.getMdtrtCertType())));
    }

    /**
     * 在未过期缓存中按 snapshot 含身份证号回落命中（兼容仅写了 03 主行的场景）。
     *
     * @param contractId 契约 ID
     * @param idCard     规范化前的身份证号
     * @param tenantId   租户
     * @return 命中行
     */
    private Optional<YbPersonCache> findValidBySnapshotCertno(
        Long contractId, String idCard, Long tenantId) {
        String normalized = normalizePersonCacheCertno(idCard);
        if (normalized == null) {
            return Optional.empty();
        }
        List<YbPersonCache> rows = personCacheRepository
            .findByContractIdAndTenantIdAndIsDeletedFalseAndExpireAtAfterOrderByCachedAtDesc(
                contractId, tenantId, LocalDateTime.now());
        for (YbPersonCache row : rows) {
            if (row == null || isEmpty(row.getSnapshotJson())) {
                continue;
            }
            if (row.getSnapshotJson().toUpperCase().contains(normalized)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /**
     * 将 1101 查询结果 upsert 至当日人员信息缓存（含身份证号别名键）。
     *
     * <p>使用 {@code REQUIRES_NEW}：{@code getPersonInfo} 等查询名方法由框架开只读事务，
     * 强制刷新仍须写缓存；独立可写事务避免「只读事务中 INSERT」。</p>
     *
     * @param command      1101 查询命令
     * @param result       平台返回的人员信息
     * @param snapshotJson 原始应答 JSON 快照
     * @param tenantId     租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFrom1101(
        YbPersonQueryCommand command,
        YbPersonInfoResult result,
        String snapshotJson,
        Long tenantId) {
        upsertPersonCacheRow(
            command.getContractId(),
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            tenantId,
            result,
            snapshotJson);
        String certno = resolvePersonCacheCertno(command, result);
        if (certno != null && !isCertnoPrimaryCacheKey(command.getMdtrtCertType(), command.getMdtrtCertNo(), certno)) {
            upsertPersonCacheRow(
                command.getContractId(),
                PERSON_CACHE_CERTNO_ALIAS_TYPE,
                certno,
                tenantId,
                result,
                snapshotJson);
        }
    }

    /**
     * 按契约、凭证类型与凭证号查找缓存行。
     *
     * @param contractId    契约 ID
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param tenantId      租户 ID
     * @return 缓存行
     */
    private Optional<YbPersonCache> findPersonCacheRow(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId) {
        return personCacheRepository.findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalse(
            contractId, mdtrtCertType, mdtrtCertNo, tenantId);
    }

    /**
     * 判断缓存行是否在有效期内。
     *
     * @param row 人员信息缓存行
     * @return 未过期返回 {@code true}
     */
    private boolean isPersonCacheValid(YbPersonCache row) {
        return row.getExpireAt() != null && row.getExpireAt().isAfter(LocalDateTime.now());
    }

    /**
     * 带租户级锁的缓存 upsert（唯一键冲突时重试一次）。
     *
     * @param contractId    契约 ID
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param tenantId      租户 ID
     * @param result        平台返回的人员信息
     * @param snapshotJson  原始应答 JSON 快照
     */
    private void upsertPersonCacheRow(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId,
        YbPersonInfoResult result,
        String snapshotJson) {
        String lockKey = contractId + "\0" + mdtrtCertType + "\0" + mdtrtCertNo + "\0" + tenantId;
        Object lock = PERSON_CACHE_UPSERT_LOCKS.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                upsertPersonCacheRowInternal(contractId, mdtrtCertType, mdtrtCertNo, tenantId, result, snapshotJson);
            } catch (DataIntegrityViolationException ex) {
                if (!isPersonCacheCertDuplicate(ex)) {
                    throw ex;
                }
                log.warn("yb_person_cache upsert conflict, retrying: contractId={}, type={}, tenantId={}",
                    contractId, mdtrtCertType, tenantId);
                upsertPersonCacheRowInternal(contractId, mdtrtCertType, mdtrtCertNo, tenantId, result, snapshotJson);
            } finally {
                PERSON_CACHE_UPSERT_LOCKS.remove(lockKey, lock);
            }
        }
    }

    /**
     * 执行缓存行 upsert 并刷新当日过期时间。
     *
     * @param contractId    契约 ID
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param tenantId      租户 ID
     * @param result        平台返回的人员信息
     * @param snapshotJson  原始应答 JSON 快照
     */
    private void upsertPersonCacheRowInternal(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId,
        YbPersonInfoResult result,
        String snapshotJson) {
        Optional<YbPersonCache> existing = findPersonCacheRow(contractId, mdtrtCertType, mdtrtCertNo, tenantId);
        YbPersonCache row = existing.orElseGet(YbPersonCache::new);
        row.setContractId(contractId);
        row.setMdtrtCertType(mdtrtCertType);
        row.setMdtrtCertNo(mdtrtCertNo);
        row.setPsnNo(result.getPsnNo());
        row.setInsuplcAdmdvs(result.getInsuplcAdmdvs());
        row.setInsutype(result.getInsutype());
        row.setBalc(result.getBalc());
        row.setSnapshotJson(snapshotJson);
        LocalDateTime now = LocalDateTime.now();
        row.setCachedAt(now);
        row.setExpireAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(23, 59, 59)));
        row.setTenantId(tenantId);
        personCacheRepository.saveAndFlush(row);
    }

    /**
     * 判断异常是否为人员缓存凭证唯一键冲突。
     *
     * @param ex 数据完整性异常
     * @return 唯一键冲突返回 {@code true}
     */
    private boolean isPersonCacheCertDuplicate(DataIntegrityViolationException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_yb_person_cache_cert")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 从命令或查询结果解析身份证号（用于别名缓存键）。
     *
     * @param command 1101 查询命令
     * @param result  平台返回的人员信息（可空）
     * @return 规范化后的身份证号；无法解析时返回 {@code null}
     */
    private String resolvePersonCacheCertno(YbPersonQueryCommand command, YbPersonInfoResult result) {
        String certno = normalizePersonCacheCertno(command.getCertno());
        if (certno != null) {
            return certno;
        }
        if (result != null) {
            return normalizePersonCacheCertno(result.getCertno());
        }
        return null;
    }

    /**
     * 规范化身份证号（去空白并转大写）。
     *
     * @param certno 原始身份证号
     * @return 规范化后的身份证号；空值返回 {@code null}
     */
    private String normalizePersonCacheCertno(String certno) {
        if (isEmpty(certno)) {
            return null;
        }
        return certno.trim().toUpperCase();
    }

    /**
     * 判断身份证号别名键是否与主凭证键重复（避免双写相同行）。
     *
     * @param mdtrtCertType 就诊凭证类型
     * @param mdtrtCertNo   就诊凭证号
     * @param certno        身份证号
     * @return 主键与别名键相同时返回 {@code true}
     */
    private boolean isCertnoPrimaryCacheKey(String mdtrtCertType, String mdtrtCertNo, String certno) {
        return PERSON_CACHE_CERTNO_ALIAS_TYPE.equals(trimToEmpty(mdtrtCertType))
            && certno.equals(normalizePersonCacheCertno(mdtrtCertNo));
    }
}
