<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card class="page-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>个人信息</span>
              <el-button
                type="primary"
                link
                :loading="profileLoading"
                @click="loadProfile"
              >
                <el-icon><Refresh /></el-icon>刷新
              </el-button>
            </div>
          </template>

          <el-descriptions v-loading="profileLoading" :column="2" border>
            <el-descriptions-item label="用户名">
              {{ profile.username || auth.user?.username || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="姓名">
              {{ profile.realName || auth.user?.realName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag type="primary" size="small">
                {{ profile.roleLabel || auth.roleLabel || '-' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="性别">
              {{ profile.gender || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ profile.phone || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ profile.email || '-' }}
            </el-descriptions-item>

            <template v-if="isStudent">
              <el-descriptions-item label="学号">
                {{ profile.studentNo || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="班级">
                {{ profile.clazzName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="专业">
                {{ profile.majorName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="年级">
                {{ profile.gradeName || '-' }}
              </el-descriptions-item>
            </template>

            <template v-else-if="isTeacher">
              <el-descriptions-item label="工号">
                {{ profile.teacherNo || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="职称">
                {{ profile.title || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="所属院系" :span="2">
                {{ profile.department || '-' }}
              </el-descriptions-item>
            </template>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card class="page-card" shadow="never">
          <template #header>
            <span>邮箱设置</span>
          </template>

          <el-form
            ref="emailFormRef"
            :model="emailForm"
            :rules="emailRules"
            label-width="100px"
          >
            <el-form-item label="邮箱地址" prop="email">
              <el-input
                v-model="emailForm.email"
                placeholder="例如 student@school.edu.cn"
                clearable
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="emailLoading"
                @click="handleUpdateEmail"
              >
                保存邮箱
              </el-button>
              <el-button @click="resetEmailForm">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="mail-tip">
            邮箱用于接收<b>学业预警通知</b>等教务消息，请填写常用邮箱；
            为保证通知准确送达，同一邮箱不能绑定多个账号。
          </div>
        </el-card>

        <el-card class="page-card" shadow="never">
          <template #header>
            <span>修改密码</span>
          </template>

          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="100px"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwdForm.oldPassword"
                type="password"
                show-password
                placeholder="请输入原密码"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                show-password
                placeholder="请输入新密码（6~32 位）"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                placeholder="请再次输入新密码"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="pwdLoading"
                @click="handleChangePassword"
              >
                确认修改
              </el-button>
              <el-button @click="resetPwdForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, updateMyEmail } from '@/api/auth'
import { fetchStudentProfile } from '@/api/student'
import { fetchTeacherProfile } from '@/api/teacher'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const profile = ref({})
const profileLoading = ref(false)

const emailFormRef = ref(null)
const emailLoading = ref(false)
const emailForm = reactive({ email: '' })

const pwdFormRef = ref(null)
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const isStudent = computed(() => auth.isStudent)
const isTeacher = computed(() => auth.isTeacher)

/** 当前档案中的邮箱 */
const currentEmail = computed(() => profile.value.email || auth.user?.email || '')

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] },
    { max: 100, message: '邮箱长度不能超过 100 个字符', trigger: 'blur' }
  ]
}

function validateConfirm(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入新密码'))
  } else if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度为 6~32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirm, trigger: 'blur' }
  ]
}

async function loadProfile() {
  profileLoading.value = true
  try {
    // 按角色取更完整的 ProfileVO
    if (auth.isStudent) {
      const res = await fetchStudentProfile()
      profile.value = res?.data || {}
    } else if (auth.isTeacher) {
      const res = await fetchTeacherProfile()
      profile.value = res?.data || {}
    } else {
      await auth.fetchProfile()
      profile.value = auth.user || {}
    }
  } catch (e) {
    profile.value = auth.user || {}
  } finally {
    profileLoading.value = false
  }
  // 表单回填为当前邮箱，便于修改
  emailForm.email = currentEmail.value
}

function resetEmailForm() {
  emailForm.email = currentEmail.value
  emailFormRef.value?.clearValidate()
}

async function handleUpdateEmail() {
  if (!emailFormRef.value) return
  try {
    await emailFormRef.value.validate()
  } catch (e) {
    return
  }

  const next = emailForm.email.trim()
  if (next.toLowerCase() === String(currentEmail.value).toLowerCase()) {
    ElMessage.info('邮箱未发生变化')
    return
  }

  emailLoading.value = true
  try {
    const res = await updateMyEmail(next)
    // 接口返回最新 ProfileVO，直接用于刷新展示
    if (res?.data) {
      profile.value = res.data
    }
    await auth.fetchProfile()
    ElMessage.success('邮箱已更新')
  } catch (e) {
    // 拦截器已提示（如邮箱已被占用）
  } finally {
    emailLoading.value = false
  }
}

async function handleChangePassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }

  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    resetPwdForm()
    setTimeout(() => {
      auth.logout()
      router.replace('/login')
    }, 600)
  } catch (e) {
    // 拦截器已提示
  } finally {
    pwdLoading.value = false
  }
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

onMounted(loadProfile)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mail-tip {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.7;
  color: #909399;
}
</style>
