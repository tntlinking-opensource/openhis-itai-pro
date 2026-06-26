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

package com.openhis.health.application.basic.print.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.core.application.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PrintJson 序列化与 contentHash 计算。
 */
@Component
@RequiredArgsConstructor
public class PrintJsonSupport {

  /** DM-PRT-001 §3.2 默认空白版式。 */
  public static final String DEFAULT_BLANK_PRINT_JSON =
      "{\"schemaVersion\":1,\"engine\":\"Print\",\"panels\":[{\"index\":0,\"width\":80,\"height\":200,\"paperHeader\":49.5,\"paperFooter\":780,\"printElements\":[]}]}";

  private final ObjectMapper objectMapper;

  /**
   * 将请求 JSON 载荷转为存库字符串。
   *
   * @param value 对象或字符串
   * @return JSON 字符串；{@code null} 入参返回 {@code null}
   */
  public String toJsonString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return trimToNull(text);
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new ValidationException("JSON 格式无效");
    }
  }

  /**
   * 将库内 JSON 字符串解析为对象（出参 Object）。
   *
   * @param json 库内 JSON
   * @return 解析结果；空串返回 {@code null}
   */
  public Object parseJsonOrNull(String json) {
    String trimmed = trimToNull(json);
    if (trimmed == null) {
      return null;
    }
    try {
      return objectMapper.readValue(trimmed, Object.class);
    } catch (JsonProcessingException ex) {
      throw new ValidationException("JSON 格式无效");
    }
  }

  /**
   * 对 printData 计算 SHA-256（hex）。
   *
   * @param printData 打印数据载荷
   * @return 哈希 hex；入参空返回 {@code null}
   */
  public String sha256HexOrNull(Object printData) {
    String json = toJsonString(printData);
    if (json == null) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
