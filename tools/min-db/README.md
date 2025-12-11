# RMP Minimal Database

Эта утилита разворачивает минимальную базу данных Postgres с помощью Exposed DAO и заполняет её демонстрационными чатами.

## Быстрый старт

1. Убедитесь, что Postgres доступен на `backend-postgres-1:5432` с пользователем/паролем `app/app` (или скорректируйте `DatabaseConfig.kt`).
2. Запустите демонстрационный сидер (Gradle или IDE):
   ```bash
   ./gradlew :RMP_min_db-main:run
   ```
3. Приложение создаст **ту же схему**, что использует Ktor-сервер (`users`, `conversations`, `messages`, `message_attachments`, `conversation_read_markers`) и наполнит её тестовыми данными из `TestData.insertTestData()`.

## Состав данных

- Пользователи: 5 человек (Anna, Olga, Vlad, Maxim, Andrey) с полями `display_name`, `role`, `password_hash`.
- Диалоги: групповой "RMP" и приватный "Приватный чат 1" (тип `GROUP`/`DIRECT`, `direct_key`).
- Сообщения: история переписки + отметки чтения (`conversation_read_markers`).

## Настройка и интеграция

- `DatabaseConfig.init()` использует те же таблицы, что `backend/src/main/kotlin/com/example/schema/ChatSchema.kt`, поэтому данные полностью совместимы.
- Чтобы повторно использовать сиды в основном сервисе, вынесите общие структуры в модуль и вызывайте `TestData.insertTestData()` из бэкенда при старте (или подключите `SampleDataSeeder` сюда).
- При необходимости обновляйте `TestData.kt` синхронно с `SampleDataSeeder.kt`, чтобы фронт и бэк всегда видели один набор тестовых пользователей/чатов.

## Лицензия

MIT
