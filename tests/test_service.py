import json
import logging
from pathlib import Path

from envdoc.service import analyze_repository

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")


def log_env_vars(env_vars):
    # Логируем всё, что собралось по переменным окружения
    logging.info("Detected env vars:\n%s", json.dumps([v.as_dict() for v in env_vars.values()], indent=2))


def test_sample_app_env_vars_detected():
    repo_path = Path(__file__).parent / "resources" / "sample-app"
    env_vars = analyze_repository(repo_path)
    log_env_vars(env_vars)

    # Проверяем, что все ожидаемые переменные найдены
    expected_vars = {
        "SERVER_PORT",
        "APP_FEATURE_ENABLED",
        "APP_URL",
        "APP_TIMEOUT",
        "DB_URL",
        "DB_USER",
        "DB_PASSWORD",
        "DB_POOL_SIZE",
    }
    assert expected_vars.issubset(env_vars.keys())

    # Дефолты и типы
    assert env_vars["SERVER_PORT"].default_value == "8080"
    assert env_vars["APP_TIMEOUT"].default_value == "5000"
    assert env_vars["APP_TIMEOUT"].inferred_type == "integer"

    # Usage via @Value on property path
    assert any("server.port" in usage.context for usage in env_vars["SERVER_PORT"].code_usages)

    # Usage via ConfigurationProperties mapping (getter)
    assert any(
        "getTimeoutMs" in usage.context or "timeoutMs" in usage.context
        for usage in env_vars["APP_TIMEOUT"].code_usages
    )

    # Direct System.getenv usage
    assert any("DB_PASSWORD" in usage.context for usage in env_vars["DB_PASSWORD"].code_usages)


def test_datasource_values_found():
    repo_path = Path(__file__).parent / "resources" / "sample-app"
    env_vars = analyze_repository(repo_path)
    log_env_vars(env_vars)

    # Ищем использование datasource значений в коде
    assert any("datasourceUrl" in u.context or "spring.datasource.url" in u.context for u in env_vars["DB_URL"].code_usages)
    assert any("username" in u.context or "spring.datasource.username" in u.context for u in env_vars["DB_USER"].code_usages)
    assert any("DB_POOL_SIZE" in u.context for u in env_vars["DB_POOL_SIZE"].code_usages)
