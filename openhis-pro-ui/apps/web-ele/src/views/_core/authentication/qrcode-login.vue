<script lang="ts" setup>
import { AuthenticationQrCodeLogin } from '@zxplat/common-ui';
import { LOGIN_PATH } from '@zxplat/constants';

defineOptions({ name: 'QrCodeLogin' });
</script>

<template>
  <AuthenticationQrCodeLogin :login-path="LOGIN_PATH" />
</template>
