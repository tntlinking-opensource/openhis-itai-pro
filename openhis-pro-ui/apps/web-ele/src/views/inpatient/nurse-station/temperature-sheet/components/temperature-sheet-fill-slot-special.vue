<script setup lang="ts">
import type { TemperatureSheetFillRowDraft } from '../services/map-nurse-station-temperature-sheet';
import type { TemperatureSheetSpecialEventOption } from '../services/temperature-sheet-special-events';

import {
  isSpecialEventTimeRequired,
  SPECIAL_EVENT_ITEM_CODE,
} from '../services/temperature-sheet-detail-items';
import {
  handleSpecialEventPickerChange,
  resolveSpecialEventPickerOptions,
  resolveSpecialEventPickerSlots,
  resolveSpecialEventPickerValue,
  syncSpecialEventShortcutFields,
} from '../services/temperature-sheet-special-events';

defineOptions({
  name: 'TemperatureSheetFillSlotSpecial',
});

interface Props {
  /** 早于入院时间时锁定，避免写入无效测量 */
  disabled?: boolean;
  row: TemperatureSheetFillRowDraft;
  specialEventSelectOptions: TemperatureSheetSpecialEventOption[];
}

withDefaults(defineProps<Props>(), {
  disabled: false,
});
</script>

<template>
  <div class="fill-row-special" @click.stop>
    <div class="special-event-main-row">
      <ElCheckbox
        v-model="row.dischargeEnabled"
        class="special-cooling-checkbox"
        :disabled="disabled"
        label="出院"
        @change="syncSpecialEventShortcutFields(row)"
      />
      <ElTimePicker
        v-if="row.dischargeEnabled"
        v-model="row.specialEventTimesByCode[SPECIAL_EVENT_ITEM_CODE.DISCHARGE]"
        class="special-event-time-picker"
        :disabled="disabled"
        format="HH:mm"
        placeholder="时间"
        popper-class="temperature-sheet-fill-popper"
        style="width: 110px; --el-date-editor-width: 110px"
        teleported
        value-format="HH:mm"
        :clearable="false"
      />
      <ElCheckbox
        v-model="row.physicalCoolingEnabled"
        class="special-cooling-checkbox"
        :disabled="disabled"
        label="物理降温"
      />
      <ElInput
        v-if="row.physicalCoolingEnabled"
        v-model="row.cooledTemperature"
        class="cooled-temp-input"
        :disabled="disabled"
        placeholder="降温后"
      >
        <template #suffix>℃</template>
      </ElInput>
      <ElCheckbox
        v-model="row.medicationCoolingEnabled"
        class="special-cooling-checkbox"
        :disabled="disabled"
        label="药物降温"
        @change="syncSpecialEventShortcutFields(row)"
      />
      <ElInput
        v-if="row.medicationCoolingEnabled"
        v-model="row.medicationCooledTemperature"
        class="cooled-temp-input"
        :disabled="disabled"
        placeholder="降温后"
      >
        <template #suffix>℃</template>
      </ElInput>
      <ElCheckbox
        v-model="row.tempNotRisingEnabled"
        class="special-cooling-checkbox"
        :disabled="disabled"
        label="体温不升"
      />
      <template
        v-for="slotIndex in resolveSpecialEventPickerSlots(row)"
        :key="`${row.timeSlot}-special-${slotIndex}`"
      >
        <ElSelect
          class="special-event-select"
          clearable
          :disabled="disabled"
          :model-value="resolveSpecialEventPickerValue(row, slotIndex)"
          :popper-options="{ strategy: 'fixed' }"
          :teleported="true"
          placeholder="特殊事件"
          popper-class="temperature-sheet-fill-popper"
          @update:model-value="
            (value: string | undefined) => handleSpecialEventPickerChange(row, slotIndex, value)
          "
        >
          <ElOption
            v-for="option in resolveSpecialEventPickerOptions(
              row,
              slotIndex,
              specialEventSelectOptions,
            )"
            :key="`${row.timeSlot}-${slotIndex}-${option.value}`"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
        <ElTimePicker
          v-if="isSpecialEventTimeRequired(resolveSpecialEventPickerValue(row, slotIndex) ?? '')"
          v-model="
            row.specialEventTimesByCode[resolveSpecialEventPickerValue(row, slotIndex) ?? '']
          "
          class="special-event-time-picker"
          :disabled="disabled"
          format="HH:mm"
          placeholder="时间"
          popper-class="temperature-sheet-fill-popper"
          style="width: 110px; --el-date-editor-width: 110px"
          teleported
          value-format="HH:mm"
          :clearable="false"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.fill-row-special {
  min-width: 0;
}
.special-event-main-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
}
.special-cooling-checkbox {
  flex-shrink: 0;
  margin-right: 0;
}
.special-cooling-checkbox :deep(.el-checkbox__label) {
  padding-left: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.cooled-temp-input {
  width: 110px;
  flex-shrink: 0;
}
.cooled-temp-input :deep(.el-input__suffix) {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.special-event-select {
  width: 110px;
  flex-shrink: 0;
}
.special-event-select :deep(.el-select__wrapper) {
  min-width: 110px;
}
.special-event-time-picker {
  flex-shrink: 0;
}
</style>
