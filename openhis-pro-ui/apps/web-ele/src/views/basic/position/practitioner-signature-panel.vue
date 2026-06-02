<script lang="ts" setup>
import type { UploadProps } from 'element-plus';

import { ref, watch } from 'vue';

import { ElButton, ElMessage, ElUpload } from 'element-plus';

import { getPractitionerProfileApi, savePractitionerSignatureApi } from '#/api/basic/practitioner';

const props = defineProps<{
  practitionerId?: string;
  practitionerName?: string;
  busNo?: string;
}>();

const loading = ref(false);
const saving = ref(false);
const signaturePreview = ref('');

const MAX_FILE_BYTES = 200 * 1024;

async function loadProfile() {
  const pid = props.practitionerId?.trim();
  if (!pid) {
    signaturePreview.value = '';
    return;
  }
  loading.value = true;
  try {
    const res = await getPractitionerProfileApi(pid);
    const data = res.data;
    signaturePreview.value = data?.signatureImage?.trim() ?? '';
  } catch {
    signaturePreview.value = '';
  } finally {
    loading.value = false;
  }
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('读取图片失败'));
    reader.readAsDataURL(file);
  });
}

const beforeUpload: UploadProps['beforeUpload'] = async (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请上传 PNG、JPG 等图片格式');
    return false;
  }
  if (rawFile.size > MAX_FILE_BYTES) {
    ElMessage.warning('签名图片请小于 200KB');
    return false;
  }
  try {
    signaturePreview.value = await fileToDataUrl(rawFile);
  } catch {
    ElMessage.error('读取图片失败');
  }
  return false;
};

async function handleSave() {
  const pid = props.practitionerId?.trim();
  if (!pid) {
    ElMessage.warning('未绑定医务人员，无法保存签名');
    return;
  }
  saving.value = true;
  try {
    await savePractitionerSignatureApi({
      practitionerId: pid,
      signatureImage: signaturePreview.value.trim(),
    });
    ElMessage.success('医生签名已保存');
    await loadProfile();
  } finally {
    saving.value = false;
  }
}

async function handleClear() {
  signaturePreview.value = '';
  await handleSave();
}

watch(
  () => props.practitionerId,
  () => {
    loadProfile();
  },
  { immediate: true },
);
</script>

<template>
  <section
    v-loading="loading"
    class="practitioner-signature-panel border-border mb-3 rounded border bg-[var(--el-fill-color-blank)] p-3"
    aria-label="医务人员签名维护"
  >
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-medium">医生签名</h3>
        <p class="text-muted-foreground text-xs">
          保存至医务人员主档（adm_practitioner.signature），病历元数据可通过
          <code class="text-xs">user.signatureImage</code> 引用。
        </p>
      </div>
      <span v-if="practitionerName" class="text-muted-foreground text-xs">
        {{ practitionerName }}
        <template v-if="busNo">（{{ busNo }}）</template>
      </span>
    </div>

    <div v-if="!practitionerId" class="text-muted-foreground text-sm">
      该用户尚未建立医务人员主档，请先在用户/主数据环节完成绑定后再维护签名。
    </div>

    <template v-else>
      <div class="flex flex-wrap items-start gap-4">
        <div
          class="signature-preview-box flex h-20 min-w-[160px] items-center justify-center rounded border border-dashed bg-white px-2"
        >
          <img
            v-if="signaturePreview"
            :src="signaturePreview"
            alt="签名预览"
            class="max-h-16 max-w-[200px] object-contain"
          />
          <span v-else class="text-muted-foreground text-xs">暂无签名</span>
        </div>
        <div class="flex flex-col gap-2">
          <ElUpload
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp"
            :before-upload="beforeUpload"
          >
            <ElButton type="primary" plain>上传签名图</ElButton>
          </ElUpload>
          <div class="flex gap-2">
            <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
            <ElButton :disabled="!signaturePreview" @click="handleClear">清除</ElButton>
          </div>
          <p class="text-muted-foreground text-xs">建议透明底 PNG，宽 200～400px，小于 200KB。</p>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.signature-preview-box {
  border-color: var(--el-border-color);
}
</style>
