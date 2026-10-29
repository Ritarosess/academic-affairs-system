<template>
  <div class="register-page">
    <el-card class="register-card" shadow="always">
      <div class="register-head">
        <el-icon class="register-logo"><School /></el-icon>
        <h1 class="register-title">注册账号</h1>
        <p class="register-subtitle">提交后需等待管理员审核通过方可登录</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        size="default"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="账号类型" prop="role">
          <el-radio-group v-model="form.role" @change="onRoleChange">
            <el-radio-button value="STUDENT">学生</el-radio-button>
            <el-radio-button value="TEACHER">教师</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3~50 位，登录时使用" clearable />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6~32 位" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>

        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" clearable />
        </el-form-item>

        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" clearable />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            :placeholder="form.role === 'TEACHER' ? '必填，用于发送学籍预警通知' : '必填，用于接收学业预警通知'"
            clearable
          />
        </el-form-item>

        <template v-if="form.role === 'STUDENT'">
          <el-form-item label="学号" prop="studentNo">
            <el-input v-model="form.studentNo" placeholder="请输入学号" clearable />
          </el-form-item>
          <el-form-item label="班级" prop="clazzId">
            <el-select v-model="form.clazzId" placeholder="请选择班级" filterable clearable style="width: 100%">
              <el-option v-for="c in clazzs" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="工号" prop="teacherNo">
            <el-input v-model="form.teacherNo" placeholder="请输入工号" clearable />
          </el-form-item>
          <el-form-item label="职称" prop="title">
            <el-input v-model="form.title" placeholder="如 教授 / 副教授 / 讲师" clearable />
          </el-form-item>
          <el-form-item label="所属院系" prop="department">
            <el-input v-model="form.department" placeholder="请输入所属院系" clearable />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            type="primary"
            class="register-button"
            :loading="loading"
            @click="handleSubmit"
          >
            提交注册申请
          </el-button>
          <el-button @click="goLogin">返回登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRegisterClazzs, submitRegister } from '@/api/registration'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const clazzs = ref([])

const form = reactive({
  role: 'STUDENT',
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  gender: '男',
  phone: '',
  email: '',
  studentNo: '',
  clazzId: null,
  teacherNo: '',
  title: '',
  department: ''
})

function validateConfirm(rule, value, callback) {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3~50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6~32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' }
  ],
  teacherNo: [
    { required: true, message: '请输入工号', trigger: 'blur' }
  ]
}

async function loadClazzs() {
  try {
    const res = await fetchRegisterClazzs()
    clazzs.value = res.data || []
  } catch (e) {
    clazzs.value = []
  }
}

function onRoleChange() {
  // 切换角色后清除已产生的验证提示，避免残留错误
  formRef.value?.clearValidate()
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
    const payload = {
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      role: form.role,
      phone: form.phone.trim(),
      email: form.email.trim(),
      gender: form.gender
    }
    if (form.role === 'STUDENT') {
      payload.studentNo = form.studentNo.trim()
      payload.clazzId = form.clazzId
    } else {
      payload.teacherNo = form.teacherNo.trim()
      payload.title = form.title.trim()
      payload.department = form.department.trim()
    }
    await submitRegister(payload)
    ElMessage.success('注册申请已提交，请等待管理员审核')
    router.replace('/login')
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function goLogin() {
  router.replace('/login')
}

onMounted(loadClazzs)
</script>

<style scoped>
.register-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 0;
  background: linear-gradient(135deg, #1f4e9c 0%, #2f6fd0 50%, #6aa8f0 100%);
}

.register-card {
  width: 520px;
  padding: 8px 12px;
  border-radius: 10px;
}

.register-head {
  text-align: center;
  margin-bottom: 18px;
}

.register-logo {
  font-size: 40px;
  color: #2f6fd0;
}

.register-title {
  margin: 8px 0 2px;
  font-size: 22px;
  color: #1f2d3d;
}

.register-subtitle {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.register-button {
  width: 200px;
}
</style>
