import {
  buildSingleDayPickerShortcuts,
  DATE_PICKER_SINGLE_DAY_SHORTCUTS_POPPER_CLASS,
} from '#/utils/format/date-picker-shortcuts';

/**
 * 单日查询日期框：上一天/下一天（底部两端 + link 主色）。
 * 通过 shortcut.onClick 直接改值，不走 EP value→pick，面板保持打开。
 */
export function useSingleDayDatePickerShortcuts(
  getCurrent: () => Date | null | string | undefined,
  onSelectDay: (ymd: string) => void,
) {
  const dateShortcuts = buildSingleDayPickerShortcuts(getCurrent, onSelectDay);

  return {
    dateShortcuts,
    singleDayPopperClass: DATE_PICKER_SINGLE_DAY_SHORTCUTS_POPPER_CLASS,
  };
}
