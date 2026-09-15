# FanVerify_tool

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)

> **⚠️ 免责声明**: 这是一个由 AI 生成的 FanVerify™ Minecraft 插件模板。**不保证稳定性**，请自行二次开发。MIT 许可证。

基于 FanVerify™ 开放平台的 Minecraft 玩家身份验证插件，支持二维码扫码和密码验证。

## ✨ 功能特性

- 🔐 **双重验证机制**: 支持 UID+pass_code 绑定和快速验证
- 📱 **二维码登录**: 游戏内地图显示二维码，玩家扫码即可验证
- 🔄 **OTP 实时轮询**: 自动检测玩家验证状态，无需手动输入
- 🌍 **登录点系统**: 未验证玩家传送到指定安全区域
- 🚫 **玩家限制**: 验证前冻结移动、聊天、命令等操作
- ⚡ **Folia 兼容**: 完美支持 Paper/Folia 双端运行
- 💾 **SQLite 数据库**: 本地存储玩家数据，支持自动迁移修复
- 🎯 **位置记忆**: 验证成功后传送回上次下线位置

## 🚀 快速开始

### 环境要求

- Java 17+
- Paper/Folia 1.20.1
- [PacketEvents](https://github.com/retrooper/packetevents) v2.4.0+
- FanVerify™ 开发者令牌 ([官网获取](https://fanverify.cn))

### 安装步骤

1. **下载插件**
   ```bash
   # 克隆仓库
   git clone https://github.com/xiaofanforfabric/FanVerify_tool.git
   cd FanVerify_tool
   
   # 构建插件
   ./gradlew build
   ```

2. **安装依赖**
   - 将 [PacketEvents](https://modrinth.com/plugin/packetevents) 放入 `plugins/` 目录

3. **配置插件**
   - 将生成的 `FanVerify-1.0.0.jar` 放入服务器 `plugins/` 目录
   - 启动服务器生成配置文件
   - 编辑 `plugins/FanVerify/config.yml`:
   
   ```yaml
   # 从 FanVerify 开发者平台获取
   dev_access_token: "your_access_token_here"
   
   # OTP 超时时间(秒)
   timeout: 120
   
   # 登录验证点坐标
   login_point:
     world: "world"
     x: 0.0
     y: 64.0
     z: 0.0
     yaw: 0.0
     pitch: 0.0
   ```

4. **重启服务器**

## 📖 使用说明

### 玩家命令

| 命令 | 说明 | 用法 |
|------|------|------|
| `/bind <uid> <pass_code>` | 绑定 FanVerify 账户 | 新玩家首次使用 |
| `/p <pass_code>` | 快速验证 | 已绑定玩家登录验证 |

### 管理员命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/ubind <playername> [-y]` | `fanverify.admin` | 解绑玩家账户 |

### 验证流程

**新玩家**: 传送到登录点 → 使用 `/bind <uid> <pass_code>` 绑定 → 验证成功

**老玩家**: 传送到登录点 → 查看地图二维码扫码 **或** 使用 `/p <pass_code>` → 传送回上次位置

**验证期间限制**: 禁止移动、聊天、打开背包、执行其他命令

## 🛠️ 技术架构

### 核心依赖

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("dev.folia:folia-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.4.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
}
```

### API 接口

本插件集成 FanVerify™ 开放平台以下接口:

| 接口 | 说明 |
|------|------|
| `/openapi/otp` | 申请 OTP 验证码 |
| `/openapi/genqrcode` | 生成二维码图片 |
| `/openapi/seeotp` | 轮询 OTP 验证状态 |
| `/openapi/verify` | 用户身份验证 |

### 数据库结构

使用 SQLite 存储玩家数据:

```sql
CREATE TABLE userdata (
    playername TEXT NOT NULL,
    uuid TEXT PRIMARY KEY NOT NULL,
    fanverify_uid TEXT,
    pass_level INTEGER DEFAULT 0,
    ip TEXT,
    last_login_time INTEGER,
    last_login_world TEXT,
    last_login_pos TEXT
)
```

## 🔧 开发指南

### 构建项目

```bash
# 开发环境构建
./gradlew build

# 运行测试服务器
./gradlew runServer

# 清理构建文件
./gradlew clean
```

### 项目结构

```

```bash
./gradlew build       # 构建
./gradlew runServer   # 测试服务器oginListener.java # 事件监听
├── manager/
│   └── PlayerStateManager.java # 状态管理
└── utils/                      # 工具类
    ├── QRCodeMapRenderer.java
    └── SchedulerUtil.java
```

### 二次开发建议

由于这是 AI 生成的模板，建议进行以下优化:

1. **配置增强**
   - 将 API 基础 URL 移至配置文件
   - 添加多语言支持
   - 自定义提示消息

2. **功能扩展**
   - 添加 Redis 支持实现多服同步
   - 集成 Velocity/BungeeCord 实现跨服验证
   - 添加验证日志和统计

3. **性能优化**
   - 实现数据库连接池
   - 优化 OTP 轮询频率
   - 添加缓存层

4. **安全加固**
   - 添加速率限制防止暴力破解
   - 增强日志记录
   - 实现 IP 白名单功能

## 📝 API 使用示例

### 获取 FanVerifyAPI 实例
- 配置 API URL、多语言支持
- Redis 多服同步、跨服验证
- 数据库连接池、缓存优化
- 速率限制、IP 白名单
## 🤝 贡献指南
⚠️ 已知问题

- Folia 传送需延迟 1 tick
- 需稳定网络访问 FanVerify API
- SQLite 不支持分布式 persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🔗 相关链接

- [FanVerify™ 官网](https://fanverify.cn)
- [Paper 文档](https://docs.papermc.io/)
- [Folia 文档](https://papermc.io/software/folia)
- [Packe

欢迎 Issue 和 PR！Fork → 创建分支 → 提交 → PR
**⚠️ 再次提醒**: 本项目为 AI 生成的模板代码，仅供学习和二次开发使用。生产环境使用前请进行充分测试！
PacketEvents](https://github.com/retrooper/packetevents)
- [Issues](https://github.com/xiaofanforfabric/FanVerify_tool/issues)

---

**⚠️ 提醒**: AI 生成模板，生产环境请