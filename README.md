# EnvDoc Agent

AI-агент для автоматического анализа переменных окружения в Java/Spring Boot проектах и генерации документации.

## Возможности

- Клонирование репозиториев из BitBucket, GitHub, GitLab
- Анализ исходного кода на предмет переменных окружения:
  - `application.yml` / `application.properties`
  - Аннотации `@Value("${VAR}")`
  - `@ConfigurationProperties`
  - `System.getenv()`
  - `System.getProperty()`
  - Spring `Environment` API
- Поддержка плейсхолдеров в стиле `db.url`, `my-key`, а также `UPPER_SNAKE`
- Анализ использования каждой переменной в коде
- Генерация документации с использованием GigaChat AI
- Вывод в Markdown и Confluence
- REST API и CLI интерфейс

## Требования

- Java 17+
- Maven 3.8+
- (Опционально) GigaChat API credentials для AI-генерации

## Быстрый старт

### Сборка

```bash
cd env-doc-agent
./mvnw clean package -DskipTests
```

### Веб-интерфейс

После запуска приложения интерфейс доступен по адресу:

```
http://localhost:8080/
```

В интерфейсе доступны поля:
- Bitbucket Url
- repositoryUrl
- branch

Нажмите кнопку **Сгенерировать** — приложение выполнит синхронный анализ и вернёт Markdown-документацию, которая будет показана на экране.

### REST API режим

```bash
# Запуск сервера
java -jar target/env-doc-agent-1.0.0-SNAPSHOT.jar

# Запуск анализа
curl -X POST http://localhost:8080/api/v1/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryUrl": "https://github.com/user/repo.git",
    "branch": "main",
    "outputFormats": ["MARKDOWN"]
  }'

# Проверка статуса
curl http://localhost:8080/api/v1/analyze/{jobId}

# Скачивание результата
curl -O http://localhost:8080/api/v1/download/{jobId}/repo_ENV_VARIABLES.md
```

### CLI режим

```bash
# Базовое использование
java -jar target/env-doc-agent-1.0.0-SNAPSHOT.jar \
  --repo=https://github.com/user/repo.git \
  --output=./docs/ENV_VARIABLES.md

# С указанием ветки
java -jar target/env-doc-agent-1.0.0-SNAPSHOT.jar \
  --repo=https://github.com/user/repo.git \
  --branch=develop \
  --output=./docs/ENV_VARIABLES.md

# С публикацией в Confluence
java -jar target/env-doc-agent-1.0.0-SNAPSHOT.jar \
  --repo=https://github.com/user/repo.git \
  --confluence-space=DEVOPS \
  --confluence-parent=123456
```

## Конфигурация

### Переменные окружения

```bash
# BitBucket/GitHub/GitLab
export BITBUCKET_TOKEN=your_token
export BITBUCKET_USERNAME=your_username

# GigaChat (для AI-генерации)
export GIGACHAT_CREDENTIALS=your_base64_credentials
export GIGACHAT_SCOPE=GIGACHAT_API_PERS  # или GIGACHAT_API_CORP

# Confluence (опционально)
export CONFLUENCE_ENABLED=true
export CONFLUENCE_URL=https://confluence.company.com
export CONFLUENCE_USERNAME=user@company.com
export CONFLUENCE_TOKEN=your_token
export CONFLUENCE_SPACE=DOCS
```

### application.yml

```yaml
app:
  temp-dir: /tmp/env-doc-agent

bitbucket:
  token: ${BITBUCKET_TOKEN}
  username: ${BITBUCKET_USERNAME}

gigachat:
  api-url: https://gigachat.devices.sberbank.ru/api/v1
  auth-url: https://ngw.devices.sberbank.ru:9443/api/v2/oauth
  credentials: ${GIGACHAT_CREDENTIALS}
  scope: GIGACHAT_API_PERS
  model: GigaChat-Pro
  temperature: 0.3
  max-tokens: 4096
  timeout-seconds: 120
  verify-ssl: false

confluence:
  enabled: ${CONFLUENCE_ENABLED:false}
  base-url: ${CONFLUENCE_URL}
  username: ${CONFLUENCE_USERNAME}
  token: ${CONFLUENCE_TOKEN}

output:
  markdown:
    enabled: true
    path: ./output
```

## API Reference

### POST /api/v1/analyze

Запуск анализа репозитория.

**Request:**
```json
{
  "repositoryUrl": "https://github.com/user/repo.git",
  "branch": "main",
  "bitbucketToken": "optional_token",
  "outputFormats": ["MARKDOWN", "CONFLUENCE"],
  "confluenceConfig": {
    "spaceKey": "DEVOPS",
    "parentPageId": "123456",
    "pageTitle": "Optional custom title"
  }
}
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "message": "Analysis started"
}
```

### GET /api/v1/analyze/{jobId}

Получение статуса и результатов анализа.

**Response (в процессе):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "progress": 65,
  "currentStep": "Generating documentation with GigaChat"
}
```

**Response (завершено):**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "progress": 100,
  "result": {
    "projectName": "my-service",
    "totalVariables": 15,
    "requiredVariables": 8,
    "optionalVariables": 7,
    "variables": [...],
    "markdownUrl": "/api/v1/download/550e8400.../ENV_VARIABLES.md",
    "confluencePageUrl": "https://confluence.company.com/pages/123456"
  }
}
```

### GET /api/v1/download/{jobId}/{filename}

Скачивание сгенерированного Markdown файла.

## Поддерживаемые паттерны

| Паттерн | Пример | Источник |
|---------|--------|----------|
| `@Value("${...}")` | `@Value("${DB_HOST}")` | Spring аннотация |
| `@ConfigurationProperties` | Бинды на класс | Spring Boot |
| `${VAR}` в YAML | `url: ${DATABASE_URL}` | application.yml |
| `${db.url}` в YAML | `url: ${db.url}` | application.yml |
| `System.getenv()` | `System.getenv("API_KEY")` | Java API |
| `System.getProperty()` | `System.getProperty("config.path")` | JVM properties |
| `Environment.getProperty()` | Spring Environment | Spring API |

## Пример сгенерированной документации

```markdown
# Переменные окружения для проекта my-service

## Сводная таблица

| Переменная | Тип | Обязательная | По умолчанию | Категория | Источник | Описание |
|------------|-----|--------------|-------------|-----------|----------|----------|
| DATABASE_URL | string | Да | - | Database | APPLICATION_YAML | URL подключения к БД |
| API_KEY | secret | Да | - | Security | SPRING_VALUE | Ключ API внешнего сервиса |

---

## Детальное описание

### DATABASE_URL

| Параметр | Значение |
|----------|----------|
| Тип | `string` |
| Обязательная | Да |
| По умолчанию | - |
| Пример | `jdbc:postgresql://db:5432/myapp` |

#### Где определена
- **Файл:** `src/main/resources/application.yml:12`
- **Тип:** APPLICATION_YAML

#### Где используется
| Класс | Метод | Назначение |
|-------|-------|------------|
| `DataSourceConfig` | `dataSource()` | DATABASE_CONNECTION |

---

## Разработка

### Запуск тестов

```bash
mvn test
```

### Сборка без тестов

```bash
mvn clean package -DskipTests
```

## Архитектура

```
┌─────────────────────────────────────────────────────────────┐
│                     EnvDocAgent (Spring Boot)                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │ BitBucket    │───>│ EnvVar       │───>│ Usage        │   │
│  │ Service      │    │ Extractor    │    │ Analyzer     │   │
│  └──────────────┘    └──────────────┘    └──────┬───────┘   │
│                                                  │           │
│                                                  ▼           │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │ Confluence   │<───│ GigaChat     │<───│ Document     │   │
│  │ Publisher    │    │ Service      │    │ Generator    │   │
│  └──────────────┘    └──────────────┘    └──────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```
