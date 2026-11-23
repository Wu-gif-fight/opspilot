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

rem Eval mode: run once and exit, no web server needed
set SPRING_MAIN_WEB_APPLICATION_TYPE=none
set RAG_EVAL_ENABLED=true
set RAG_EVAL_EXIT_AFTER_RUN=true

call mvn spring-boot:run -Dspring-boot.run.profiles=mysql

pause
