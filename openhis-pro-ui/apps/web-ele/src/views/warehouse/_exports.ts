/**
 * 仓库模块“唯一公共出口”（barrel）。
 *
 * @deprecated 整个 `views/warehouse` 目录已废弃，见同目录 DEPRECATED.md。
 * 新功能请使用 `views/inventory`。本 barrel 仅供 finance 等历史兼容。
 *
 * 约定：
 * - 其它业务模块（如 finance）如需复用仓库能力，只能从这里 import
 * - 禁止跨模块直接深路径引用 `views/warehouse/**` 内部实现文件
 */

export { default as WarehouseDrawDrawer } from './draw/detail/dialog.vue';
export { default as WarehouseInventoryDrawer } from './inventory/detail/dialog.vue';
export { default as WarehouseLossDrawer } from './loss/detail/dialog.vue';
export { default as WarehousePurchaseDrawer } from './purchase/detail/dialog.vue';
export { default as WarehouseTransferDrawer } from './transfer/detail/dialog.vue';
