<template>
  <div class="notfound-page">
    <el-result title="404" sub-title="抱歉，您访问的页面不存在">
      <template #icon>
        <el-icon class="notfound-icon"><WarningFilled /></el-icon>
      </template>
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { homeOfRole } from '@/stores/auth'
import { getAuthToken, getAuthUser } from '@/utils/authStorage'

const router = useRouter()

function goHome() {
  if (getAuthToken()) {
    router.replace(homeOfRole(getAuthUser()?.role))
  } else {
    router.replace('/login')
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    goHome()
  }
}
</script>

<style scoped>
.notfound-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f0f2f5;
}

.notfound-icon {
  font-size: 80px;
  color: #e6a23c;
}
</style>
