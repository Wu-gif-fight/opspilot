@echo off
chcp 65001 >nul
cd /d D:\OpsPilot-Java\OpsPilot

rem Load API keys from .env (gitignored)
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"

rem Models: DeepSeek chat + SiliconFlow embedding
set AI_PROVIDER=openai
set OPENAI_BASE_URL=https://api.deepseek.com
set OPENAI_MODEL=deepseek-chat
set EMBEDDING_PROVIDER=openai
set EMBEDDING_BASE_URL=https://api.siliconflow.cn
set EMBEDDING_MODEL=BAAI/bge-m3

rem Local services: Chroma(8000) + Redis(6379)
set CHROMA_BASE_URL=http://127.0.0.1:8000
set REDIS_HOST=127.0.0.1

rem Email via Mailpit (SMTP 1025, web panel 8025)
set MCP_EMAIL_MODE=smtp
set MAIL_HOST=127.0.0.1
set MAIL_PORT=1025
set MAIL_SMTP_AUTH=false
set ALERT_MAIL_RECIPIENTS=oncall@example.com

rem Local MySQL: activate the "mysql" profile via the Maven plugin
call mvn spring-boot:run -Dspring-boot.run.profiles=mysql

pause
