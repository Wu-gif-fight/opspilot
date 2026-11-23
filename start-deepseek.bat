@echo off
chcp 65001 >nul
cd /d D:\OpsPilot-Java\OpsPilot

rem Load API keys from .env (gitignored)
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"

set AI_PROVIDER=openai
set OPENAI_BASE_URL=https://api.deepseek.com
set OPENAI_MODEL=deepseek-chat
set EMBEDDING_PROVIDER=openai
set EMBEDDING_BASE_URL=https://api.siliconflow.cn
set EMBEDDING_MODEL=BAAI/bge-m3
set USE_CHROMA=true
set MCP_EMAIL_MODE=log

call mvn spring-boot:run
pause
