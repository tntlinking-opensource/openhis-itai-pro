# DM-CHS-CAT-001 NationalCatalog · 国家目录

> **包**：`com.zxplat.plugin.chs.domain.catalog`  
> **DomainService**：`YbNationalCatalogDomainService` 等  
> **详细设计编号**：SDD-CHS-CAT-001

## 1. 概述

国家医保目录下载游标、院内对照匹配、上传记录。由 catalog DomainService 族维护；下载编排在 bridge Invoker。

## 2. 关键 DomainService

| 类型 | 说明 |
|------|------|
| `YbNationalCatalogDomainService` | 国家目录写 |
| `YbCatalogDownloadCursorDomainService` | 下载游标 |
| `YbInstitutionCatalogMatchDomainService` | 院内对照 |
| `YbCatalogUploadRecordDomainService` | 上传记录 |

## 3. 关联成稿

目录文件通知：成稿编号 **DM-CHS-CAT-005**（`notice` 包）。
