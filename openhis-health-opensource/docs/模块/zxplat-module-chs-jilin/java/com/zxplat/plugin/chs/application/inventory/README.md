# inventory 应用层

进销存上传、初始化、比对、重试（3501–3507、3508–3513 查询族）。

**文档**：`docs/模块/zxplat-module-chs-jilin/AppService/AS-CHS-INV-001-进销存运维.md` · `AppService-SDD/AS-CHS-INV-001-进销存运维-SDD.md`

## 包结构（P14 ✅）

```text
inventory/
  service/InsuranceInventoryAppService   # 仅注入 domain inventory.access 端口
```

出站 Invoker / Workflow 已迁至 `bridge/inventory/`；application **禁止** `com.zxplat.plugin.chs.gateway.*` import（ArchTest 守护）。

## domain access 端口

| 端口 | 职责 |
|------|------|
| `InventoryUploadAccess` | 3501–3506 上报 |
| `InventoryInitializeAccess` | 3501+3502 批初始化 |
| `InventoryInitializeFillAccess` | 初始化配对填充（HIS 库存） |
| `Inv3507Invoker` | 3507 删除 |
| `Inv35xxQueryInvoker` | 3508–3513 平台查询 |
| `InventoryCompareAccess` | 本地 vs 平台差异比对 |
| `InventoryRetryAccess` | 失败重试 |

## 调用链

```text
InsuranceInventoryAppService
  → domain inventory.access 端口
      → bridge/inventory workflow + invoker（运行时 starter 装配）
```
