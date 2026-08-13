# NFC 隐藏信息查询系统

一套「安卓手机碰 NFC 卡 → 读 UID → 查服务器隐藏信息」的完整系统。含 **Node.js + Express + SQLite 服务器** 与 **Kotlin 安卓 App**，界面采用「间谍/特工」暗色荧光风格。

> ⚠️ 本应用仅用于游戏/娱乐用途，只读取卡片的 UID（唯一标识），不读取卡内余额、交易等任何敏感数据。

---

## 目录结构

```
nfcgame/
├── server/                 # 服务器端（Node.js + Express + SQLite + HTTPS）
│   ├── src/
│   │   ├── index.js        # 入口：HTTPS 启动（2999 端口）
│   │   ├── app.js          # Express 组装、限流、路由
│   │   ├── db.js           # 数据库层（better-sqlite3 CRUD）
│   │   ├── routes/info.js  # GET/POST/DELETE /api/info
│   │   └── middleware/     # 参数校验、统一响应
│   ├── scripts/
│   │   ├── init.sql        # 建表 SQL（等价迁移脚本）
│   │   └── generate-cert.sh# 自签名证书生成脚本
│   ├── deploy/
│   │   ├── deploy.sh       # 一键部署脚本
│   │   └── nfc-server.service # systemd 服务文件
│   ├── Dockerfile
│   ├── package.json
│   └── .env.example
├── android/                # 安卓端（Kotlin + ViewBinding + Retrofit）
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nfcgame/app/
│       │   ├── MainActivity.kt
│       │   ├── nfc/NfcHelper.kt         # NFC 读 UID
│       │   ├── network/                  # Retrofit + 证书固定
│       │   ├── ui/query/QueryFragment.kt # 查询模式
│       │   └── ui/enroll/EnrollFragment.kt # 录入模式
│       └── res/                          # 布局、主题、证书(raw/)
└── README.md
```

---

## 一、服务器端

### 1. 技术栈
- Node.js ≥ 18 + Express
- SQLite（`better-sqlite3`，同步 API，含 linux-x64 预编译二进制）
- 自签名 HTTPS 证书（openssl 生成）
- `express-rate-limit` 频率限制

### 2. 数据库表结构

```sql
CREATE TABLE IF NOT EXISTS cards (
  uid        TEXT PRIMARY KEY,              -- 卡片十六进制 UID（大写）
  title      TEXT NOT NULL DEFAULT '',
  content    TEXT NOT NULL DEFAULT '',
  image_url  TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);
```

### 3. API

| 方法 | 路径 | 说明 | 成功 | 失败 |
|---|---|---|---|---|
| GET | `/api/info?uid=XXXX` | 查询信息 | `{"code":200,"data":{title,content,image_url}}` | 未找到 `{"code":404,"message":"未找到该卡片对应的信息"}` |
| POST | `/api/info` | 新增/更新 | `{"code":200,"message":"保存成功"}` | 参数缺失 400 |
| DELETE | `/api/info?uid=XXXX` | 删除信息 | `{"code":200,"message":"删除成功"}` | 未找到 404 |
| POST | `/api/upload` | 上传图片（multipart，字段名 `file`） | `{"code":200,"data":{"url":"/uploads/xxx.png"}}` | 类型/大小不符 400 |
| GET | `/uploads/xxx.png` | 访问已上传图片 | 图片内容 | 404 |
| GET | `/health` | 健康检查 | `{"code":200,"status":"ok"}` | — |

**约束**：`uid` 仅允许 4~16 位十六进制；图片上传仅支持 jpg/png/gif/webp 且 ≤5MB；所有 API 限流每 IP 每分钟 60 次；响应不缓存。

### 4. 本地运行

```bash
cd server
cp .env.example .env          # 按需修改端口/证书路径

# 生成自签名证书（参数为服务器 IP）
bash scripts/generate-cert.sh 121.37.119.20

# 安装依赖并启动
npm install
npm start                     # 或 npm run dev（热重载）
```

验证：

```bash
# 查询（未找到，预期 404）
curl -k "https://127.0.0.1:2999/api/info?uid=TEST"

# 录入
curl -k -X POST "https://127.0.0.1:2999/api/info" \
  -H "Content-Type: application/json" \
  -d '{"uid":"04A1B2C3D4E5F6","title":"测试","content":"你好","image_url":""}'

# 再查询（预期 200）
curl -k "https://127.0.0.1:2999/api/info?uid=04A1B2C3D4E5F6"
```

### 5. 部署到云服务器（IP: 121.37.119.20，端口 2999）

**方式 A：Docker**

```bash
cd server
docker build -t nfc-info-server .
docker run -d --name nfc-server -p 2999:2999 \
  -v "$(pwd)/certs:/app/certs" \
  -v "$(pwd)/data:/app/data" \
  nfc-info-server
```

**方式 B：systemd（推荐生产）**

```bash
# 1. 上传并安装（服务器上执行）
mkdir -p /opt/nfc-game/server && cd /opt/nfc-game/server
# 上传 package.json / src / scripts（可用 deploy/deploy.sh 或手动 scp）
npm install --production
bash scripts/generate-cert.sh 121.37.119.20
cp .env.example .env

# 2. 注册服务
cp deploy/nfc-server.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now nfc-server

# 3. 验证
curl -k https://127.0.0.1:2999/health
```

**一键脚本**（本地执行，需可 ssh 到服务器）：

```bash
cd server
SERVER_IP=121.37.119.20 SERVER_USER=root bash deploy/deploy.sh
```

> 注意：若未开放 2999 端口，需在服务器防火墙/安全组放行（如 `firewall-cmd --add-port=2999/tcp --permanent && firewall-cmd --reload`）。当前阶段可先忽略。

---

## 二、安卓端

### 1. 环境要求
- Android Studio（Gradle 8.2，AGP 8.2.2，Kotlin 1.9.22）
- 最低 Android 8.0（API 26），targetSdk 34

### 2. 运行步骤

1. 用 Android Studio 打开 `android/` 目录，等待 Gradle 同步。
2. 连接支持 NFC 的真机（NFC 无法用模拟器测试），直接运行。
3. 打开 App → 首次弹隐私提示 → 点「我知道了」。
4. 触碰公交卡/NFC 卡，自动读取 UID 并查询。

### 3. 连接服务器配置

服务器地址在 `android/app/build.gradle` 中定义：

```gradle
buildConfigField "String", "SERVER_URL", "\"https://121.37.119.20:2999\""
```

修改 IP 后重新构建即可。

### 4. 证书固定（重要）

服务器使用自签名证书，安卓默认不信任。本项目通过「证书固定」解决：

- 将服务器的 `server/certs/cert.pem` 复制到 `android/app/src/main/res/raw/server_cert.pem`（仓库已内置一份与脚本生成一致的证书）。
- `network/HttpClient.kt` 会加载该证书作为唯一信任源。
- **若你重新生成了证书，务必同步替换 `res/raw/server_cert.pem`**，否则 App 会握手失败。

> 开发阶段若暂未放置证书，代码会回退为「信任所有证书」并在日志警告——仅用于联调，生产勿用。

### 5. 功能说明

- **查询模式**（默认）：提示「请触碰 NFC 卡片」→ 读 UID → 显示标题/内容/图片。
- **录入模式**（底部导航切换）：触碰读卡自动填 UID → 输入标题/内容 → 图片可**从相册选择自动上传**（也可手动填 URL）→ 保存到服务器。
- **兼容卡片**：MIFARE Classic（4 字节 UID）、Ultralight / DESFire（7 字节 UID）等常见 NFC-A/B/F/V 卡片。
- **隐私提示**：首次进入弹窗告知仅用于游戏用途。

---

## 三、常见问题

| 问题 | 解决 |
|---|---|
| 卡片靠近无反应 | 确认手机 NFC 已开启；公交卡读的是 UID 无需解锁卡片 |
| 查询返回「未找到」 | 该 UID 尚未录入，先在录入模式保存一条信息 |
| App 报 SSL 错误 | 证书与服务器不一致，重新同步 `res/raw/server_cert.pem` |
| 服务器启动报「未找到证书」 | 先运行 `generate-cert.sh` 生成证书 |
| 请求 429 | 触发限流（每 IP 每分钟 60 次），稍等重试 |
| 图片不显示 | 图片链接需为 **https** 地址（App 禁止明文 http），请确认服务器可访问该图 |
| Android Studio 提示缺少 gradle wrapper | 首次直接用 Android Studio「Open」打开 `android/` 目录即可自动同步；命令行构建需先确保 `gradle-wrapper.jar` 完整 |

---

## 四、安全说明

- 仅读取卡片 UID，不读取卡内数据；隐私提示已内置。
- 服务器仅存最小字段（uid/title/content/image_url），不存敏感信息。
- 凭证（服务器密码、`.env`）不入库；`.gitignore` 已排除证书与数据库文件。
