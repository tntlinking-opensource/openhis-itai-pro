package com.openhis.whale.report.application.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

public final class QrCodeImageFactory {

    private static final int MIN_PX = 64;
    private static final int MAX_PX = 1024;

    private QrCodeImageFactory() {
    }

    public static byte[] encodePng(String payload, int sizePx) {
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException("RPT_QR_PAYLOAD_EMPTY", "二维码内容为空");
        }
        int px = Math.max(MIN_PX, Math.min(MAX_PX, sizePx));
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            var matrix = new QRCodeWriter().encode(payload.trim(), BarcodeFormat.QR_CODE, px, px, hints);
            BufferedImage image = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < px; y++) {
                for (int x = 0; x < px; x++) {
                    int rgb;
                    if (matrix.get(x, y)) {
                        rgb = 0x000000;
                    } else {
                        rgb = 0xFFFFFF;
                    }
                    image.setRGB(x, y, rgb);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("RPT_QR_ENCODE_FAILED", "二维码生成失败: " + e.getMessage(), e);
        }
    }
}
