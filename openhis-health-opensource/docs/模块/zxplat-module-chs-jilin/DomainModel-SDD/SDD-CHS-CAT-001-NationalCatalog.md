# SDD-CHS-CAT-001 NationalCatalog · 国家目录详细设计

> **成稿编号**：DM-CHS-CAT-001  
> **源码包**：`com.zxplat.plugin.chs.domain.catalog`  
> **模块**：`module-chs-jilin-domain`

## 1. 边界

国家医保目录（下载游标、院内对照、上传记录）的写入口集中在 `catalog.service.*DomainService`。  
ZIP 下载、报文组装、平台出站在 **gateway scheme + bridge Invoker**；application **禁止** import gateway / bridge。

## 2. DomainService 族

| 类型 | 职责 |
|------|------|
| `YbNationalCatalogDomainService` | 国家目录写 |
| `YbCatalogDownloadCursorDomainService` | 下载游标 |
| `YbInstitutionCatalogMatchDomainService` | 院内对照匹配 |
| `YbCatalogUploadRecordDomainService` | 上传记录 |

## 3. 层约束

| 层 | 允许 | 禁止 |
|----|------|------|
| domain | 实体 / Command / DomainService | 调平台 HTTP |
| bridge | Invoker、下载编排 | 依赖 application |
| application | AppService UI | import gateway / bridge |

## 4. 关联能力

目录**文件通知**另见成稿编号 **DM-CHS-CAT-005**（`notice` DomainService 族），本 SDD 不展开。
