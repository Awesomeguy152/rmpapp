# RMP App

Монорепозиторий учебного проекта «РМП», включающий:

- `MobileApp/` — Android-клиент на Jetpack Compose.
- `backend/` — серверная часть на Ktor с PostgreSQL.
- `tools/min-db/` — вспомогательный Kotlin-скрипт для первоначального развёртывания и наполнения БД.

## Быстрый старт

### MobileApp

```bash
cd MobileApp
./gradlew :app:assembleDebug
```

### Backend

```bash
cd backend
./gradlew build
```

Docker-compose, openapi спецификация и прочие артефакты находятся в соответствующих подпроектах.

### Минимальная БД

Скрипт в `tools/min-db/` поднимает подключение к PostgreSQL и наполняет его тестовыми данными.

```bash
cd tools/min-db
./gradlew run
```

Перед запуском убедитесь, что переменные подключения заданы в `DatabaseConfig.kt` либо экспортированы в окружение.
