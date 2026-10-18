# DM-CHS-INV-001 InvUpload · 进销存上传

> **表名**：`yb_inv_upload` / `yb_inv_upload_trace`  
> **包**：`com.zxplat.plugin.chs.domain.inventory`  
> **DomainService**：`YbInvUploadDomainService`  
> **详细设计编号**：SDD-CHS-INV-001

## 1. 概述

医保进销存（350x）上传主记录与明细轨迹。bridge 侧 InventoryUploadWorkflow 编排；domain 负责落库与本地查询。

## 2. 关键写方法

见同目录类型卡 `inventory/service/YbInvUploadDomainService.md` 与 domain 源码。

## 3. 应用服务编号

运维 UI：AS-CHS-INV-001。
