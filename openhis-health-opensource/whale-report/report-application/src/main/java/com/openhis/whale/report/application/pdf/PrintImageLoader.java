package com.openhis.whale.report.application.pdf;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 HTTP(S) URL 或 data URI / Base64 加载打印用图片字节。
 */
public final class PrintImageLoader {

    private static final Pattern DATA_URI =
            Pattern.compile("^data:image/[^;]+;base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private PrintImageLoader() {
    }

    public static byte[] loadBytes(String source) throws IOException, InterruptedException {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String trimmed = source.trim();
        Matcher dataUri = DATA_URI.matcher(trimmed);
        if (dataUri.matches()) {
            return Base64.getDecoder().decode(dataUri.group(1).trim());
        }
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimmed))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                byte[] body = response.body();
                return body != null && body.length > 0 ? body : null;
            }
            return null;
        }
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
