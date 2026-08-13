# ============================================================
# NFC 隐藏信息系统 - 服务器镜像
# 构建：docker build -t nfc-info-server .
# 运行：docker run -d -p 2999:2999 -v $(pwd)/certs:/app/certs -v $(pwd)/data:/app/data nfc-info-server
# ============================================================
FROM node:20-alpine

WORKDIR /app

# 先复制依赖清单以利用 Docker 层缓存
COPY package*.json ./
RUN npm install --production

# 复制源码
COPY src ./src
COPY scripts ./scripts

# 创建数据目录
RUN mkdir -p /app/data

EXPOSE 2999

CMD ["node", "src/index.js"]
