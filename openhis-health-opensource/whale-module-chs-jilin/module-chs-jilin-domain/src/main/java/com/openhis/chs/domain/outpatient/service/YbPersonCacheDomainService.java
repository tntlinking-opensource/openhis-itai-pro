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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
        if ("01".equals(trimToEmpty(command.getMdtrtCertType())) && certno != null) {
            Optional<YbPersonCache> byCertno = findPersonCacheRow(
                command.getContractId(), PERSON_CACHE_CERTNO_ALIAS_TYPE, certno, tenantId);
            if (byCertno.isPresent() && isPersonCacheValid(byCertno.get())) {
                return byCertno;
            }
        }
        Optional<YbPersonCache> byCredential = findPersonCacheRow(
            command.getContractId(),
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            tenantId);
        if (byCredential.isPresent() && isPersonCacheValid(byCredential.get())) {
            return byCredential;
        }
        return Optional.empty();
    }

    /**
     * 将 1101 查询结果 upsert 至当日人员信息缓存（含身份证号别名键）。
     *
     * @param command      1101 查询命令
     * @param result       平台返回的人员信息
     * @param snapshotJson 原始应答 JSON 快照
     * @param tenantId     租户 ID
     */
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
