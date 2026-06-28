OpenHIS 前端便携包（解压即用）
================================

1. 解压 openhis-web-deploy.zip 到任意目录（路径勿含中文或空格为佳）
2. 确保后端 health-app 已启动（默认 http://127.0.0.1:8081）
3. 双击 start.bat 启动 Nginx
4. 浏览器访问 http://localhost:8080/
5. 停止服务：双击 stop.bat

配置
----
编辑 config.env 后重新运行 start.bat：

  LISTEN_PORT=8080        # 前端监听端口
  BACKEND_HOST=127.0.0.1  # 后端地址
  BACKEND_PORT=8081       # 后端端口

目录说明
--------
  html/     前端静态文件（发版时可只替换此目录）
  conf/     Nginx 配置（由 start.ps1 根据 template 生成）
  logs/     运行日志
  nginx.exe 内置 Nginx（Windows x64）

注意
----
- 本包仅适用于 Windows；Linux 请用 dist/ + 系统 Nginx（见 whale-doc）
- 首次启动若端口被占用，请修改 config.env 中的 LISTEN_PORT
- 修改 html/ 后无需重启 Nginx（静态文件）；改 config.env 须 stop 后再 start
