"""
Генерация промпта для GigaChat на основе отчёта render_report.
"""

from typing import Dict

from .gigachat import GigaChatClient
from .models import EnvVarInfo
from .service import render_report


def build_doc_prompt(env_vars: Dict[str, EnvVarInfo], service_name: str = "java-сервис") -> str:
    """
    Собрать промпт для GigaChat, чтобы получить Markdown-файл doc-env.md
    с краткой справкой по переменным окружения.
    """
    report_json = render_report(env_vars, output_format="json")
    return f"""
Ты — инженер сопровождения. На основе входных данных с переменными окружения подготовь файл doc-env.md.
Опиши, какие переменные окружения можно задать для {service_name}, кратко поясни их смысл и значения по умолчанию.

Требования к ответу:
- верни только содержимое doc-env.md в Markdown;
- сделай блок с кратким описанием сервиса и назначения переменных;
- добавь таблицу со столбцами: Имя переменной, Значение по умолчанию, Тип, Описание, Где указана (property path), Использование в коде (пути/файлы).
- если значение по умолчанию не задано — укажи «нет».
- не добавляй лишний текст вне Markdown.

Входные данные (JSON из render_report):
```json
{report_json}
```
"""


def generate_doc_env_md(
    env_vars: Dict[str, EnvVarInfo],
    client: GigaChatClient,
    service_name: str = "java-сервис",
    model: str = "GigaChat",
    **kwargs,
) -> str:
    """
    Отправить промпт в GigaChat и вернуть содержимое doc-env.md (ожидается в content первого сообщения).
    kwargs можно использовать для передачи temperature, max_tokens и т.п.
    """
    prompt = build_doc_prompt(env_vars, service_name=service_name)
    response = client.send_prompt(prompt, model=model, **kwargs)
    # Ожидаем формат, совместимый с Chat Completions
    try:
        return response["choices"][0]["message"]["content"]
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"Unexpected GigaChat response format: {response}") from exc
