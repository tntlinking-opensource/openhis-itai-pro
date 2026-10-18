# SDD-CHS-INV-001 InvUpload · 进销存上传详细设计

> **成稿编号**：DM-CHS-INV-001  
> **源码**：`com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadDomainService`  
> **表**：`yb_inv_upload` / `yb_inv_upload_trace`

## 1. 边界

医保进销存（350x）上传主记录与明细轨迹由 domain 落库；  
出站报文在 gateway scheme，编排在 bridge `InventoryUploadWorkflow`。

## 4. 写方法

以 `YbInvUploadDomainService` 公共写方法为准（见 domain 源码）。  
application 只读/运维查询，不直接调平台。

## 5. 开关

`zxplat.modules.chs.enabled=true`；进销存细项见 `zxplat.chs.inventory.*`。
