<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-head">
        <el-icon class="login-logo"><School /></el-icon>
        <h1 class="login-title">高校教务系统</h1>
        <p class="login-subtitle">University Academic Affairs System</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            clearable
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleSubmit"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-tip">
        还没有账号？
        <el-link type="primary" underline="never" @click="goRegister">立即注册</el-link>
      </div>

      <el-alert type="info" :closable="false" class="demo-alert">
        <template #title>
          <div class="demo-title">演示账号（密码均为 123456）</div>
        </template>
        <ul class="demo-list">
          <li><span>管理员</span><code>admin</code></li>
          <li><span>教师</span><code>t001</code></li>
          <li><span>学生</span><code>2024001</code></li>
        </ul>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore, homeOfRole } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度为 2~32 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6~32 个字符', trigger: 'blur' }
  ]
}

function goRegister() {
  router.push('/register')
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  loading.value = true
  try {
    const data = await auth.login({
      username: form.username.trim(),
      password: form.password
    })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    const target =
      typeof redirect === 'string' && redirect.startsWith('/')
        ? redirect
        : homeOfRole(data?.user?.role)
    router.replace(target)
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: linear-gradient(135deg, #1f4e9c 0%, #2f6fd0 50%, #6aa8f0 100%);
}

.login-card {
  width: 420px;
  padding: 8px 12px;
  border-radius: 10px;
}

.login-head {
  text-align: center;
  margin-bottom: 18px;
}

.login-logo {
  font-size: 40px;
  color: #2f6fd0;
}

.login-title {
  margin: 8px 0 2px;
  font-size: 22px;
  color: #1f2d3d;
}

.login-subtitle {
  margin: 0;
  font-size: 12px;
  color: #909399;
  letter-spacing: 0.5px;
}

.login-button {
  width: 100%;
}

.demo-alert {
  margin-top: 4px;
}

.demo-title {
  font-weight: 600;
}

.demo-list {
  margin: 6px 0 0;
  padding-left: 18px;
  line-height: 1.9;
  font-size: 13px;
}

.demo-list span {
  display: inline-block;
  width: 52px;
  color: #606266;
}

.demo-list code {
  background: #f4f4f5;
  padding: 1px 6px;
  border-radius: 3px;
  color: #409eff;
}

.register-tip {
  margin-top: 8px;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>
