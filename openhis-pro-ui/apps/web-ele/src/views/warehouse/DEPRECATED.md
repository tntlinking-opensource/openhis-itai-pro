# ⚠️ 本目录已废弃

**默认勿使用。** 仓库根目录 `.cursorignore` 已排除本目录；需要时请 @ 具体文件。

- **新开发**：`src/views/inventory/**`
- **API**：`src/api/inventory/**`（门面 `api/warehouse/*` 逐步停用）
- **拆解示例**：`src/inventory-workbench-kit/**`

## 已迁至 inventory（本目录仅兼容壳）

| 本目录                  | 新实现                                  |
| ----------------------- | --------------------------------------- |
| `purchase-inbound/`     | `inventory/warehouse/purchase-inbound/` |
| `stock-out/`            | `inventory/warehouse/stock-out/`        |
| `inventory-stock-list/` | `inventory/stock-list/`                 |

## 仍在本目录的老页（冻结，不扩展）

调拨、请领、采购、盘点、损溢、领用、单据审批等。

## 外部仍引用本目录（删目录前需先迁）

- `src/adapter/vxe-table.ts` → `views/warehouse/util.ts`
- `src/views/finance/approval/components/warehouse-adapter.vue` → `_exports.ts`
- `src/components/warehouse/warehouse-pharmacy-stock-order-table.vue` → `purchase-return/detail/...`
