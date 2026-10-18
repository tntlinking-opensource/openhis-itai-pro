/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.history.support;

import com.zxplat.domain.share.constants.PeisExamKindConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 套餐快照 JSON 读写辅助（轻量字符串，避免编排层强绑 ObjectMapper）。
 */
public final class PeisPackageSnapshotSupport {

    private static final Pattern EXAM_KIND = Pattern.compile("\"examKind\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PACKAGE_NAME = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private PeisPackageSnapshotSupport() {
    }

    public static String build(
            Long packageActivityId, List<Long> childActivityIds, String name, String examKind) {
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"packageActivityId\":");
        sb.append(packageActivityId);
        sb.append(",\"childActivityIds\":[");
        if (childActivityIds != null) {
            for (int i = 0; i < childActivityIds.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(childActivityIds.get(i));
            }
        }
        sb.append("],\"name\":\"");
        if (name != null) {
            sb.append(name.replace("\\", "\\\\").replace("\"", "\\\""));
        }
        sb.append('"');
        String kind = PeisExamKindConstants.normalizeOrNull(examKind);
        if (kind != null) {
            sb.append(",\"examKind\":\"").append(kind).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    public static String extractExamKind(String packageSnapshotJson) {
        if (packageSnapshotJson == null || packageSnapshotJson.isBlank()) {
            return null;
        }
        Matcher m = EXAM_KIND.matcher(packageSnapshotJson);
        if (!m.find()) {
            return null;
        }
        return PeisExamKindConstants.normalizeOrNull(m.group(1));
    }

    public static String extractPackageName(String packageSnapshotJson) {
        if (packageSnapshotJson == null || packageSnapshotJson.isBlank()) {
            return null;
        }
        Matcher m = PACKAGE_NAME.matcher(packageSnapshotJson);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

  /**
   * 从套餐快照解析建单时写入的套餐子项 activityId 列表。
   *
   * @param packageSnapshotJson 快照 JSON
   * @return 子项 activityId；无快照时返回空列表
   */
    public static List<Long> extractChildActivityIds(String packageSnapshotJson) {
        if (packageSnapshotJson == null || packageSnapshotJson.isBlank()) {
            return List.of();
        }
        int start = packageSnapshotJson.indexOf("\"childActivityIds\"");
        if (start < 0) {
            return List.of();
        }
        int bracketStart = packageSnapshotJson.indexOf('[', start);
        int bracketEnd = packageSnapshotJson.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) {
            return List.of();
        }
        String body = packageSnapshotJson.substring(bracketStart + 1, bracketEnd).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : body.split(",")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
                // 脏快照行跳过
            }
        }
        return ids;
    }
}
