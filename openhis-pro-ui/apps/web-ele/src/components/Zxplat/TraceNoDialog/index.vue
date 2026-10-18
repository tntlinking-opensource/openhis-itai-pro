<!--
  药品追溯码录入弹窗（扫码失败或未接扫码枪时可手工录入）。
-->
<script setup>
import { computed, ref, watch } from 'vue';

defineOptions({
  name: 'ZxplatTraceNoDialog',
});

const props = defineProps({
  ypName: {
    type: String,
    default: '',
  },
  rowData: {
    type: Object,
    default: () => ({}),
  },
  openDialog: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['submit', 'cancel']);

const displayName = computed(() => {
  return props.ypName || props.rowData?.itemName || props.rowData?.name || '';
});

const code = ref('');

watch(
  () => props.openDialog,
  (open) => {
    if (open) code.value = '';
  },
);

function handleConfirm() {
  emit('submit', code.value ?? '');
}

function handleClose() {
  emit('cancel');
}
</script>

<template>
  <el-dialog
    :model-value="openDialog"
    append-to-body
    title="药品追溯码"
    width="480px"
    @update:model-value="(v) => !v && handleClose()"
  >
    <p v-if="displayName" class="mb-2 text-sm text-[var(--el-text-color-secondary)]">
      {{ displayName }}
    </p>
    <el-input
      v-model="code"
      clearable
      placeholder="扫码或手工录入追溯码"
      @keyup.enter="handleConfirm"
    />
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>
