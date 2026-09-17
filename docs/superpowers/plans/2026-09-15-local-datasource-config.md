# 本地数据库敏感配置隔离实施计划

> **供执行代理使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐项执行；步骤采用复选框记录进度。

**目标：** 将本机 MySQL 连接信息从可提交配置中移出，保证真实账号和密码不会被 Git 跟踪，同时保留可复现的本地配置示例。

**架构：** 提交通用的 `application.yaml`，让应用默认启用 `local` Profile。真实的本机数据源配置放入被 Git 忽略的 `application-local.yaml`；`application-local.yaml.example` 使用无敏感信息的示例值，供新开发环境复制。

**技术栈：** Spring Boot、YAML、Git。

---

## 文件结构

- 修改：`.gitignore`：忽略本机专用数据源配置。
- 修改：`src/main/resources/application.yaml`：保留应用通用配置并设置默认 Profile。
- 创建：`src/main/resources/application-local.yaml`：存放仅本机使用的 MySQL 配置，不加入 Git。
- 创建：`src/main/resources/application-local.yaml.example`：提供无敏感信息的配置模板，可加入 Git。

### 任务 1：隔离本机 MySQL 凭据

**文件：**

- 修改：`.gitignore`
- 修改：`src/main/resources/application.yaml`
- 创建：`src/main/resources/application-local.yaml`
- 创建：`src/main/resources/application-local.yaml.example`

- [ ] **步骤 1：在 `.gitignore` 末尾加入本机配置忽略规则。**

```gitignore
# 本机数据库配置，禁止提交
/src/main/resources/application-local.yaml
```

- [ ] **步骤 2：将 `src/main/resources/application.yaml` 改为不含数据源账号和密码的通用配置。**

```yaml
spring:
  application:
    name: short-link
  profiles:
    default: local
```

- [ ] **步骤 3：创建被忽略的 `src/main/resources/application-local.yaml`，并只在此文件填入真实本机账号和密码。**

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/short_link?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: 在此填写本机 MySQL 账号
    password: 在此填写本机 MySQL 密码
```

- [ ] **步骤 4：创建可提交的 `src/main/resources/application-local.yaml.example`。**

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/short_link?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: your_mysql_username
    password: your_mysql_password
```

- [ ] **步骤 5：验证真实本机配置被忽略，且示例文件和通用配置可被 Git 识别。**

运行：

```powershell
git check-ignore -v -- src/main/resources/application-local.yaml
git status --short
```

预期：第一条命令显示 `.gitignore` 中的忽略规则；第二条命令不显示 `application-local.yaml`，但显示 `.gitignore`、`application.yaml` 与 `application-local.yaml.example`。

- [ ] **步骤 6：在本机 `application-local.yaml` 中填写真实 MySQL 账号和密码，并启动应用验证配置生效。**

运行：

```powershell
.\mvnw.cmd spring-boot:run
```

预期：Spring Boot 使用 `local` Profile 启动；若 MySQL 数据库或账号尚未创建，控制台会报告连接失败，此时先创建数据库和账号，再次启动。

- [ ] **步骤 7：只提交可公开的配置文件。**

```powershell
git add .gitignore src/main/resources/application.yaml src/main/resources/application-local.yaml.example
git commit -m "config: 隔离本地数据库配置"
```

预期：提交中不包含 `application-local.yaml`，Git 历史中也不包含真实 MySQL 密码。
