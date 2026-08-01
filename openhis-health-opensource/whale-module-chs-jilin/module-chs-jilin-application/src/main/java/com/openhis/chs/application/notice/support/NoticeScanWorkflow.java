/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.support;

import com.openhis.chs.application.notice.support.CanonicalPackParser.CanonicalPackParseResult;
import com.openhis.chs.application.notice.support.dto.NoticePreviewContext;
import com.openhis.chs.application.notice.support.dto.NoticeScanCachedItem;
import com.openhis.chs.application.notice.support.dto.NoticeScanJob;
import com.openhis.chs.application.notice.support.dto.NoticeScanSession;
import com.openhis.chs.domain.notice.command.PersistNoticeLineCommand;
import com.openhis.chs.domain.notice.entity.YbCatalogNoticeDoc;
import com.openhis.chs.domain.notice.repository.YbCatalogNoticeDocRepository;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 标准目录扫描工作流：扫描 root 一级子目录、去重、缓存 scanToken；支持异步扫描进度。
 */
@Component
@RequiredArgsConstructor
public class NoticeScanWorkflow {

    private static final String STATUS_NEW = "NEW";
    private static final String STATUS_DUPLICATE = "DUPLICATE";
    private static final String STATUS_FAILED = "FAILED";
    private static final int SAMPLE_LIMIT = 20;
    private static final Duration SCAN_TTL = Duration.ofMinutes(30);

    private final CanonicalPackParser canonicalPackParser;
    private final LocalCatalogNoticePackStore localCatalogNoticePackStore;
    private final YbCatalogNoticeDocRepository docRepository;
    private final TenantContext tenantContext;
    private final ConcurrentHashMap<String, NoticeScanSession> scanCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NoticeScanJob> scanJobs = new ConcurrentHashMap<>();
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chs-notice-scan");
        t.setDaemon(true);
        return t;
    });

    /**
     * 同步扫描（兼容旧 IF）；内部与异步任务共用同一解析逻辑。
     *
     * @param scanRootOverride 请求级根目录；空则用配置
     * @param contractId       契约主键（可空）
     * @return 扫描会话
     */
    public NoticeScanSession scanCanonicalDir(String scanRootOverride, Long contractId) {
        return runScan(scanRootOverride, contractId, TenantUtils.getCurrentTenantId(), null);
    }

    /**
     * 启动异步扫描，立即返回 jobId 供轮询。
     *
     * @param scanRootOverride 请求级根目录；空则用配置
     * @param contractId       契约主键（可空）
     * @return 任务 ID
     */
    public String startAsyncScan(String scanRootOverride, Long contractId) {
        // 提交前校验根目录，避免任务立刻 FAILED 且前端只看到旋转
        localCatalogNoticePackStore.resolveCanonicalRoot(scanRootOverride);
        Long tenantId = TenantUtils.getCurrentTenantId();

        String jobId = UUID.randomUUID().toString().replace("-", "");
        NoticeScanJob job = new NoticeScanJob();
        job.setJobId(jobId);
        job.setStatus(NoticeScanJob.STATUS_PENDING);
        job.setMessage("排队中");
        scanJobs.put(jobId, job);

        scanExecutor.execute(() -> {
            tenantContext.setCurrentTenantId(tenantId);
            try {
                job.setStatus(NoticeScanJob.STATUS_RUNNING);
                job.setMessage("扫描中");
                NoticeScanSession session = runScan(scanRootOverride, contractId, tenantId, job);
                job.setSession(session);
                job.setStatus(NoticeScanJob.STATUS_SUCCEEDED);
                job.setCurrentPackDir(null);
                job.setMessage("扫描完成");
            } catch (BusinessException ex) {
                job.setStatus(NoticeScanJob.STATUS_FAILED);
                job.setMessage(ex.getMessage());
            } catch (Exception ex) {
                job.setStatus(NoticeScanJob.STATUS_FAILED);
                job.setMessage("扫描失败：" + ex.getMessage());
            } finally {
                tenantContext.clear();
            }
        });
        return jobId;
    }

    /**
     * 按任务 ID 取进度快照；不存在则抛业务异常。
     *
     * @param jobId 任务 ID
     * @return 任务
     */
    public NoticeScanJob getScanJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "jobId 不能为空");
        }
        NoticeScanJob job = scanJobs.get(jobId.trim());
        if (job == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描任务不存在或已过期，请重新扫描");
        }
        return job;
    }

    /**
     * 按令牌取扫描会话；过期或不存在抛 {@code NOTICE_PREVIEW_EXPIRED}。
     *
     * @param scanToken 扫描令牌
     * @return 会话
     */
    public NoticeScanSession loadScan(String scanToken) {
        if (scanToken == null || scanToken.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描令牌无效或已过期，请重新扫描");
        }
        evictExpired();
        NoticeScanSession session = scanCache.get(scanToken);
        if (session == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描令牌无效或已过期，请重新扫描");
        }
        if (session.getExpireAt() == null || Instant.now().isAfter(session.getExpireAt())) {
            scanCache.remove(scanToken);
            throw new BusinessException(ChsErrorCodes.NOTICE_PREVIEW_EXPIRED, "扫描令牌已过期，请重新扫描");
        }
        return session;
    }

    /**
     * 从会话解析待入库目标列表。
     * <p>packDirs 为空 = 全部 NEW；force 时允许勾选 DUPLICATE。</p>
     *
     * @param session  会话
     * @param packDirs 请求勾选的子目录
     * @param force    是否允许重复重导
     * @return 待处理缓存项（含将跳过项，由调用方按 status 区分）
     */
    public List<NoticeScanCachedItem> resolveImportTargets(
            NoticeScanSession session,
            List<String> packDirs,
            boolean force) {
        List<NoticeScanCachedItem> targets = new ArrayList<>();
        boolean selectAllNew = packDirs == null || packDirs.isEmpty();
        if (selectAllNew) {
            for (NoticeScanCachedItem item : session.getItemsByPackDir().values()) {
                if (STATUS_NEW.equals(item.getStatus())) {
                    targets.add(item);
                }
            }
            return targets;
        }
        for (String packDir : packDirs) {
            if (packDir == null || packDir.isBlank()) {
                continue;
            }
            NoticeScanCachedItem item = session.getItemsByPackDir().get(packDir.trim());
            if (item == null) {
                NoticeScanCachedItem missing = new NoticeScanCachedItem();
                missing.setPackDir(packDir.trim());
                missing.setStatus(STATUS_FAILED);
                missing.setMessage("扫描结果中无此子目录");
                targets.add(missing);
                continue;
            }
            targets.add(item);
            // force 仅放行 DUPLICATE；FAILED/未知态由 AppService 记 SKIPPED/FAILED
            if (STATUS_DUPLICATE.equals(item.getStatus()) && !force) {
                continue;
            }
        }
        return targets;
    }

    /**
     * 按扫描根目录 + 子目录名重新完整解析（含 zip 与全量明细），供确认入库使用。
     *
     * @param scanRoot    扫描根绝对路径
     * @param packDirName 一级子目录名
     * @return 入库用预览上下文
     */
    public NoticePreviewContext rematerializePack(String scanRoot, String packDirName) {
        if (scanRoot == null || scanRoot.isBlank() || packDirName == null || packDirName.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "无法定位标准包目录，请重新扫描");
        }
        Path dir = Paths.get(scanRoot).resolve(packDirName).normalize();
        CanonicalPackParseResult parsed = canonicalPackParser.parseDirectory(dir, true);
        return toPreviewContext(parsed);
    }

    private NoticeScanSession runScan(
            String scanRootOverride,
            Long contractId,
            Long tenantId,
            NoticeScanJob progress) {
        Path root = localCatalogNoticePackStore.resolveCanonicalRoot(scanRootOverride);

        NoticeScanSession session = new NoticeScanSession();
        session.setScanToken(UUID.randomUUID().toString().replace("-", ""));
        session.setExpireAt(Instant.now().plus(SCAN_TTL));
        session.setScanRoot(root.toString());
        session.setContractId(contractId);

        List<Path> packDirs = listFirstLevelDirectories(root);
        packDirs.sort(Comparator.comparing(p -> p.getFileName().toString()));
        if (progress != null) {
            progress.setTotal(packDirs.size());
            progress.setProcessed(0);
        }

        int processed = 0;
        for (Path packDir : packDirs) {
            String packDirName = packDir.getFileName().toString();
            if (progress != null) {
                progress.setCurrentPackDir(packDirName);
            }
            NoticeScanCachedItem item = new NoticeScanCachedItem();
            item.setPackDir(packDirName);
            try {
                // 扫描不打 zip，避免 70+ 包同时驻留数百 MB
                CanonicalPackParseResult parsed = canonicalPackParser.parseDirectory(packDir, false);
                NoticePreviewContext preview = toPreviewContext(parsed);
                // 会话只保留摘要与样例行；确认入库时按盘再完整解析
                preview.setPackBytes(null);
                preview.setLines(new ArrayList<>(preview.getSampleLines()));
                item.setPreview(preview);
                Optional<YbCatalogNoticeDoc> existing = docRepository
                    .findByTenantIdAndSourceFileHashAndIsDeletedFalse(tenantId, preview.getSourceFileHash());
                if (existing.isPresent()) {
                    item.setStatus(STATUS_DUPLICATE);
                    item.setExistingDocId(existing.get().getId());
                    item.setMessage("相同内容已入库，docId=" + existing.get().getId());
                } else {
                    item.setStatus(STATUS_NEW);
                    item.setMessage(null);
                }
            } catch (BusinessException ex) {
                item.setStatus(STATUS_FAILED);
                item.setMessage(ex.getMessage());
            } catch (Exception ex) {
                item.setStatus(STATUS_FAILED);
                item.setMessage("解析失败：" + ex.getMessage());
            }
            session.getItemsByPackDir().put(packDirName, item);
            processed++;
            if (progress != null) {
                progress.setProcessed(processed);
            }
        }

        evictExpired();
        scanCache.put(session.getScanToken(), session);
        return session;
    }

    private NoticePreviewContext toPreviewContext(CanonicalPackParseResult parsed) {
        NoticePreviewContext ctx = new NoticePreviewContext();
        ctx.setExpireAt(Instant.now().plus(SCAN_TTL));
        ctx.setPackBytes(parsed.getPackBytes());
        ctx.setPackFileName(parsed.getPackFileName());
        String contentHash = parsed.getContentHash();
        if (contentHash == null || contentHash.isBlank()) {
            contentHash = sha256Hex(parsed.getPackBytes());
        }
        ctx.setSourceFileHash(contentHash);
        ctx.setDocKind(parsed.getDocKind());
        ctx.setCategory(parsed.getCategory());
        String title = parsed.getTitle();
        if (title == null || title.isBlank()) {
            title = parsed.getSourceFilename();
        }
        if (title == null || title.isBlank()) {
            title = parsed.getPackFileName();
        }
        ctx.setTitle(title);
        ctx.setSourceFilename(parsed.getSourceFilename());
        ctx.setNoticeDate(parsed.getNoticeDate());
        ctx.setEffectiveDate(parsed.getEffectiveDate());
        ctx.setParseStatus(parsed.getParseStatus());
        ctx.setSheetSummaryJson(parsed.getSheetSummaryJson());
        ctx.setWarnings(new ArrayList<>(parsed.getWarnings()));
        ctx.setAddCount(parsed.getAddCount());
        ctx.setStopCount(parsed.getStopCount());
        ctx.setModifyCount(parsed.getModifyCount());
        ctx.setSetPayClassCount(parsed.getSetPayClassCount());
        ctx.setLines(parsed.getLines());
        int lineCount = 0;
        if (parsed.getLines() != null) {
            lineCount = parsed.getLines().size();
        }
        ctx.setLineCount(lineCount);

        List<PersistNoticeLineCommand> samples = new ArrayList<>();
        int limit = SAMPLE_LIMIT;
        if (parsed.getLines().size() < limit) {
            limit = parsed.getLines().size();
        }
        for (int i = 0; i < limit; i++) {
            samples.add(parsed.getLines().get(i));
        }
        ctx.setSampleLines(samples);
        return ctx;
    }

    private List<Path> listFirstLevelDirectories(Path root) {
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    dirs.add(child);
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "无法读取扫描根目录：" + ex.getMessage(),
                ex);
        }
        return dirs;
    }

    private void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, NoticeScanSession>> it = scanCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, NoticeScanSession> entry = it.next();
            NoticeScanSession session = entry.getValue();
            if (session.getExpireAt() != null && now.isAfter(session.getExpireAt())) {
                it.remove();
            }
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "无法计算 SHA-256", ex);
        }
    }
}
