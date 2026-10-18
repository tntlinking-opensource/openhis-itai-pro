/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传工具类
 */
public class FileUploadUtils {
    /**
     * 默认大小 50M
     */
    public static final long DEFAULT_MAX_SIZE = 50 * 1024 * 1024L;

    /**
     * 默认的文件名最大长度 100
     */
    public static final int DEFAULT_FILE_NAME_LENGTH = 100;

    /**
     * 默认上传的地址
     */
    private static String defaultBaseDir = System.getProperty("user.dir") + "/uploads";

    public static void setDefaultBaseDir(String defaultBaseDir) {
        FileUploadUtils.defaultBaseDir = defaultBaseDir;
    }

    public static String getDefaultBaseDir() {
        return defaultBaseDir;
    }

    /**
     * 以默认配置进行文件上传
     *
     * @param file 上传的文件
     * @return 文件路径
     * @throws IOException 上传失败时抛出
     */
    public static String upload(MultipartFile file) throws IOException {
        return upload(getDefaultBaseDir(), file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    /**
     * 根据文件路径上传
     *
     * @param baseDir 相对应用的基目录
     * @param file 上传的文件
     * @return 文件路径
     * @throws IOException 上传失败时抛出
     */
    public static String upload(String baseDir, MultipartFile file) throws IOException {
        return upload(baseDir, file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    /**
     * 文件上传
     *
     * @param baseDir 相对应用的基目录
     * @param file 上传的文件
     * @param allowedExtension 允许的文件类型
     * @return 返回上传成功的文件路径
     * @throws IOException 上传失败时抛出
     */
    public static String upload(String baseDir, MultipartFile file, String[] allowedExtension) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.length() > DEFAULT_FILE_NAME_LENGTH) {
            throw new IOException("文件名过长或无效");
        }

        String extension = getExtension(file);
        if (!isAllowedExtension(extension, allowedExtension)) {
            throw new IOException("不允许的文件类型：" + extension);
        }

        long size = file.getSize();
        if (size > DEFAULT_MAX_SIZE) {
            throw new IOException("文件大小超过限制：" + (DEFAULT_MAX_SIZE / 1024 / 1024) + "MB");
        }

        String fileName = generateFileName(originalFilename);
        File destFile = getAbsoluteFile(baseDir, fileName);
        file.transferTo(destFile);

        return destFile.getAbsolutePath();
    }

    /**
     * 生成唯一文件名
     *
     * @param originalFilename 原始文件名
     * @return 生成的文件名
     */
    public static String generateFileName(String originalFilename) {
        String extension = getExtension(originalFilename);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return datePath + "/" + uuid + "." + extension;
    }

    /**
     * 获取绝对文件路径
     *
     * @param uploadDir 上传目录
     * @param fileName 文件名
     * @return 文件对象
     * @throws IOException IO 异常
     */
    public static File getAbsoluteFile(String uploadDir, String fileName) throws IOException {
        File desc = new File(uploadDir + File.separator + fileName);
        if (!desc.getParentFile().exists()) {
            desc.getParentFile().mkdirs();
        }
        return desc;
    }

    /**
     * 获取文件扩展名
     *
     * @param file 文件
     * @return 扩展名
     */
    public static String getExtension(MultipartFile file) {
        String extension = getExtension(file.getOriginalFilename());
        if (StringUtils.isEmpty(extension)) {
            String contentType = file.getContentType();
            if (contentType != null) {
                extension = MimeTypeUtils.getExtension(contentType);
            }
        }
        return extension != null ? extension : "";
    }

    /**
     * 从文件名获取扩展名
     *
     * @param filename 文件名
     * @return 扩展名
     */
    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 判断扩展名是否被允许
     *
     * @param extension 文件扩展名
     * @param allowedExtension 允许的扩展名列表
     * @return 是否允许
     */
    public static boolean isAllowedExtension(String extension, String[] allowedExtension) {
        if (allowedExtension == null || allowedExtension.length == 0) {
            return true;
        }
        for (String str : allowedExtension) {
            if (str.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查文件是否允许上传
     *
     * @param file 上传的文件
     * @param allowedExtension 允许的扩展名
     * @throws IOException 不允许时抛出
     */
    public static void assertAllowed(MultipartFile file, String[] allowedExtension) throws IOException {
        long size = file.getSize();
        if (DEFAULT_MAX_SIZE != -1 && size > DEFAULT_MAX_SIZE) {
            throw new IOException("文件大小超过限制：" + (DEFAULT_MAX_SIZE / 1024 / 1024) + "MB");
        }

        String extension = getExtension(file);
        if (!isAllowedExtension(extension, allowedExtension)) {
            throw new IOException("不允许的文件类型：" + extension);
        }
    }
}
