# Env var doc generator for Spring Boot

Инструмент для поддержки: на вход git-репозиторий Spring Boot, на выход — список переменных окружения, найденных в `application*.yml`, их значения по умолчанию и места использования в Java-коде.

## Установка
```bash
python -m venv .venv
.venv\Scripts\activate  # Windows
pip install -r requirements.txt
```

## Быстрый старт
```bash
python -m envdoc.cli --repo https://github.com/example/project.git --branch main --output-format text
```
- `--repo` — git URL проекта. Можно вместо этого указать `--local-path /path/to/repo`.
- `--branch` — опционально ветка/тег.
- `--output-format` — `json` (по умолчанию) или человекочитаемый `text`.
- `--output path/to/file` — записать отчёт в файл.
- `--keep-clone` — оставить скачанную копию на диске (путь выводится в stdout).

## Как работает
1. Клонирует репозиторий (или использует локальный путь).
2. Ищет `application*.yml` и парсит плейсхолдеры `${ENV_VAR:default}`.
3. Для каждой переменной собирает:
   - имя переменной окружения;
   - путь в YAML (property path);
   - значение по умолчанию (если задано);
   - предполагаемый тип (по значению по умолчанию);
   - краткое описание (на основе property path);
   - места использования в коде (`.java`), по совпадению имени переменной или property path.

## Ограничения и оговорки
- Тип выводится эвристически: число → integer/float, `true/false` → boolean, иначе string.
- Описание формируется автоматически, без анализа комментариев.
- Поиск использования в коде — по вхождению строки; добавлен простой разбор `@ConfigurationProperties` (prefix + поля класса → поиск по имени поля и геттерам), но для сложных случаев может понадобиться ручная проверка.

## Тестовый пример
В `tests/resources/sample-app` лежит небольшой Spring Boot проект с переменными:
- `application.yml` содержит `SERVER_PORT`, `APP_FEATURE_*`, `DB_*`.
- Использование в коде: `@Value("${server.port}")`, `System.getenv("DB_PASSWORD")`, поля/gетеры `FeatureProperties` (под `@ConfigurationProperties`).

## Тесты
```bash
python -m pytest
```

## Отправка промпта в GigaChat
Добавлен простой клиент `envdoc.gigachat.GigaChatClient`:
```python
from envdoc.gigachat import GigaChatClient

client = GigaChatClient()  # использует GIGACHAT_TOKEN и GIGACHAT_BASE_URL из окружения
resp = client.send_prompt("Привет! Расскажи о проекте.")
print(resp)
```
Требуемые переменные окружения:
- `GIGACHAT_TOKEN` — bearer-токен.
- `GIGACHAT_BASE_URL` — при необходимости свой endpoint (по умолчанию `https://gigachat.devices.sberbank.ru/api/v1/chat/completions`).

### Генерация doc-env.md через GigaChat
Есть сервис для сборки промпта на основе результата `render_report`:
```python
from envdoc.docgen import build_doc_prompt, generate_doc_env_md
from envdoc.gigachat import GigaChatClient
from envdoc.service import analyze_repository

repo_path = ...
env_vars = analyze_repository(repo_path)
prompt = build_doc_prompt(env_vars, service_name="demo-service")

# Отправка в GigaChat и получение Markdown doc-env.md
client = GigaChatClient()
doc_md = generate_doc_env_md(env_vars, client, service_name="demo-service")
print(doc_md)
```
Промпт просит вернуть содержимое файла `doc-env.md` (Markdown) с краткой справкой для сопровождения: название переменной, дефолт, тип, описание, property path и места использования в коде.

## Примеры запуска

- Быстрый анализ удалённого репозитория и вывод в текстовом виде:
  ```bash
  python -m envdoc.cli --repo https://github.com/example/project.git --branch main --output-format text
  ```

- Анализ локальной копии и сохранение отчёта в файл:
  ```bash
  python -m envdoc.cli --local-path /path/to/repo --output-format json --output report.json
  ```

- Анализ + генерация `doc-env.md` через GigaChat одним запуском CLI:
  ```bash
  GIGACHAT_TOKEN=XXX python -m envdoc.cli ^
    --repo https://github.com/example/project.git ^
    --branch main ^
    --output-format json ^
    --generate-doc ^
    --doc-output doc-env.md ^
    --service-name my-service ^
    --gigachat-model GigaChat
  ```
  Требуется `GIGACHAT_TOKEN` в окружении (опционально `GIGACHAT_BASE_URL`, `--gigachat-model`).

- Сценарный пример генерации документации через GigaChat в коде (после анализа):
  ```bash
  python - <<'PY'
  from envdoc.service import analyze_repository
  from envdoc.docgen import generate_doc_env_md
  from envdoc.gigachat import GigaChatClient
  from pathlib import Path

  repo_path = Path("/path/to/repo")
  env_vars = analyze_repository(repo_path)
  client = GigaChatClient()  # требует GIGACHAT_TOKEN в окружении
  doc_md = generate_doc_env_md(env_vars, client, service_name="demo-service")
  Path("doc-env.md").write_text(doc_md, encoding="utf-8")
  print("doc-env.md saved")
  PY
  ```

### Самоподписанные сертификаты для GigaChat
Клиент поддерживает настройки через переменные окружения:
- `GIGACHAT_CA_BUNDLE` — путь к кастомному CA (передается в `requests` как `verify="..."`).
- `GIGACHAT_VERIFY` — `false`/`0` для отключения проверки сертификата (используйте только если доверяете соединению).
