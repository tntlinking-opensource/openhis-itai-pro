/**
 * 业务单据打印是否将**检验**带子项展开为子项行（不打父项名）。
 *
 * 产品默认 false。国基 override 为 true。
 * 仅检验（类目 22/PROOF）；检查/治疗等仍打父项。
 *
 * 注意：本常量须以 `#/utils/print/activity-definition-children-print.config` 导入，
 * 相对路径不走院点 override。
 */
export const EXPAND_ACTIVITY_DEFINITION_CHILDREN_ON_BUSINESS_PRINT = false;
