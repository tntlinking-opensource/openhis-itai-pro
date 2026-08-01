<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';

defineOptions({
  name: 'TemperatureSheetBottomTimePicker',
});

const modelValue = defineModel<string>({ default: '' });

const visible = ref(false);
const draftHour = ref(0);
const draftMinute = ref(0);
const hourListRef = ref<HTMLElement | null>(null);
const minuteListRef = ref<HTMLElement | null>(null);

const hourOptions = Array.from({ length: 24 }, (_, hour) => hour);
const minuteOptions = Array.from({ length: 60 }, (_, minute) => minute);

const displayText = computed(() => {
  const text = modelValue.value?.trim();
  return text || '选择时间';
});

const draftTimeText = computed(
  () => `${String(draftHour.value).padStart(2, '0')}:${String(draftMinute.value).padStart(2, '0')}`,
);

/** 解析 HH:mm 为时、分 */
function parseHm(value: string): { hour: number; minute: number } {
  const match = /^(\d{1,2}):(\d{2})$/.exec((value ?? '').trim());
  if (!match) {
    const now = new Date();
    return { hour: now.getHours(), minute: now.getMinutes() };
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  return {
    hour: Number.isFinite(hour) && hour >= 0 && hour <= 23 ? hour : 0,
    minute: Number.isFinite(minute) && minute >= 0 && minute <= 59 ? minute : 0,
  };
}

/** 打开时同步草稿时分 */
function syncDraftFromModel(): void {
  const parsed = parseHm(modelValue.value);
  draftHour.value = parsed.hour;
  draftMinute.value = parsed.minute;
}

/** 滚动列表使当前项居中 */
async function scrollOptionIntoView(listEl: HTMLElement | null, value: number): Promise<void> {
  await nextTick();
  if (!listEl) {
    return;
  }
  const target = listEl.querySelector<HTMLElement>(`[data-value="${value}"]`);
  target?.scrollIntoView({ block: 'center', behavior: 'auto' });
}

/** 面板显隐变化：打开时定位到当前时分 */
async function handleVisibleChange(open: boolean): Promise<void> {
  visible.value = open;
  if (!open) {
    return;
  }
  syncDraftFromModel();
  await scrollOptionIntoView(hourListRef.value, draftHour.value);
  await scrollOptionIntoView(minuteListRef.value, draftMinute.value);
}

/** 确认并写回 */
function handleConfirm(): void {
  modelValue.value = draftTimeText.value;
  visible.value = false;
}

/** 取消关闭 */
function handleCancel(): void {
  visible.value = false;
}

watch(visible, async (open) => {
  if (!open) {
    return;
  }
  await scrollOptionIntoView(hourListRef.value, draftHour.value);
  await scrollOptionIntoView(minuteListRef.value, draftMinute.value);
});
</script>

<template>
  <ElPopover
    :hide-after="0"
    :persistent="true"
    :popper-options="{
      strategy: 'fixed',
      modifiers: [{ name: 'eventListeners', options: { scroll: false, resize: true } }],
    }"
    :visible="visible"
    :width="220"
    placement="bottom-start"
    popper-class="temperature-sheet-fill-popper temperature-sheet-time-field-popper"
    trigger="click"
    @update:visible="handleVisibleChange"
  >
    <template #reference>
      <button class="bottom-time-trigger" type="button" @click.stop>
        <span class="bottom-time-trigger-text" :class="{ 'is-placeholder': !modelValue?.trim() }">
          {{ displayText }}
        </span>
      </button>
    </template>

    <div class="time-field-panel" @click.stop>
      <div class="time-field-columns">
        <div ref="hourListRef" class="time-field-column">
          <button
            v-for="hour in hourOptions"
            :key="`h-${hour}`"
            class="time-field-option"
            :class="{ 'is-active': hour === draftHour }"
            :data-value="hour"
            type="button"
            @click="draftHour = hour"
          >
            {{ String(hour).padStart(2, '0') }}
          </button>
        </div>
        <div ref="minuteListRef" class="time-field-column">
          <button
            v-for="minute in minuteOptions"
            :key="`m-${minute}`"
            class="time-field-option"
            :class="{ 'is-active': minute === draftMinute }"
            :data-value="minute"
            type="button"
            @click="draftMinute = minute"
          >
            {{ String(minute).padStart(2, '0') }}
          </button>
        </div>
      </div>
      <div class="time-field-footer">
        <button class="time-field-action" type="button" @click="handleCancel">取消</button>
        <button class="time-field-action is-primary" type="button" @click="handleConfirm">
          确定
        </button>
      </div>
    </div>
  </ElPopover>
</template>

<style scoped>
.bottom-time-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 32px;
  margin: 0;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  cursor: pointer;
  box-sizing: border-box;
}
.bottom-time-trigger:hover {
  border-color: var(--el-color-primary);
}
.bottom-time-trigger-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.bottom-time-trigger-text.is-placeholder {
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}
.time-field-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.time-field-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.time-field-column {
  height: 180px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.time-field-option {
  display: block;
  width: 100%;
  margin: 0;
  padding: 8px 0;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--el-text-color-regular);
  text-align: center;
  cursor: pointer;
}
.time-field-option.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}
.time-field-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.time-field-action {
  margin: 0;
  padding: 2px 8px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}
.time-field-action.is-primary {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>
