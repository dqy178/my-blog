FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
# 用 BuildKit 缓存挂载缓存 npm 缓存：改代码重建时不必重新下载依赖
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]