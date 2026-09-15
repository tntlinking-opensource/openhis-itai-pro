# ⚠️ 本目录已废弃

**默认勿使用。** 仓库根目录 `.cursorignore` 已排除本目录；需要时请 @ 具体文件。

- **新开发**：`src/views/inventory/**`
- **API**：`src/api/inventory/**`（进货/出库工作台适配已迁至 `api/inventory/warehouse/*-workbench.ts`；其余 `api/warehouse/*` 仅服务本目录冻结页）。`api/index.ts` **不再** barrel 再导出 `warehouse/*`，冻结页请直接 `#/api/warehouse/...`。
- **拆解示例**：`src/inventory-workbench-kit/**`

## 已迁至 inventory（兼容壳已删除）

| 原目录                  | 现实现                                    |
| ----------------------- | ----------------------------------------- | ---- |
| `purchase-inbound/`     | `inventory/warehouse/purchase-inbound/`   |
| `stock-out/`            | `inventory/warehouse/stock-out/`          |
| `inventory-stock-list/` | `inventory/stock-list/`                   |
| `gain-loss/`            | `inventory/warehouse/gain-loss/`          |
| `return-to-supplier/`   | `inventory/warehouse/return-to-supplier/` |
| `transfer/`             | `inventory/warehouse/transfer-out         | in/` |

菜单种子已指向 inventory；pro 发版后勿再恢复壳页。

## 仍在本目录的老页（冻结，不扩展）

请领、采购、盘点、损溢、领用、单据审批等（未迁完的整页实现）。

## 外部仍引用本目录（删目录前需先迁）

- `src/adapter/vxe-table.ts` → `views/warehouse/util.ts`
- `src/components/warehouse/warehouse-pharmacy-stock-order-table.vue` → `purchase-return/detail/...`
