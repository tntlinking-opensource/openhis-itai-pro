<script setup lang="ts">
import { FILLABLE_SHORTCUT_HINTS } from '../fillable-shortcut-hints';

defineOptions({ name: 'EmrFillableShortcutRail' });
</script>

<template>
  <aside class="emr-fillable-shortcut-rail" role="status" aria-label="填写快捷键提示">
    <div class="emr-fillable-shortcut-rail__title">填写提示</div>
    <ul class="emr-fillable-shortcut-rail__list">
      <li
        v-for="(item, index) in FILLABLE_SHORTCUT_HINTS"
        :key="`${item.key}-${index}`"
        class="emr-fillable-shortcut-rail__line"
      >
        <span class="emr-fillable-shortcut-rail__key">{{ item.key }}</span>
        <span class="emr-fillable-shortcut-rail__text">{{ item.text }}</span>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.emr-fillable-shortcut-rail {
  box-sizing: border-box;
  width: max-content;
  max-width: 188px;
  padding: 0;
  pointer-events: none;
}

.emr-fillable-shortcut-rail__title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.3;
  color: #6b7280;
}

.emr-fillable-shortcut-rail__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.emr-fillable-shortcut-rail__line {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.emr-fillable-shortcut-rail__line + .emr-fillable-shortcut-rail__line {
  margin-top: 4px;
}

.emr-fillable-shortcut-rail__key {
  flex-shrink: 0;
  min-width: 68px;
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}

.emr-fillable-shortcut-rail__text {
  font-size: 12px;
  line-height: 1.45;
  color: #6b7280;
}
</style>
