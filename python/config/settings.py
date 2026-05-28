"""应用配置管理，通过环境变量加载。"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM
    openai_api_key: str = ""
    openai_model: str = "gpt-4o"
    openai_base_url: str = ""  # 留空用 OpenAI，DeepSeek 填 https://api.deepseek.com
    minimax_api_key: str = ""
    minimax_model: str = "MiniMax-M2.7"

    # Database
    database_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5432/edu_agent"
    redis_url: str = "redis://localhost:6379/0"

    # Server
    api_port: int = 8000
    log_level: str = "INFO"

    model_config = {"env_file": "../.env", "env_file_encoding": "utf-8"}


settings = Settings()
