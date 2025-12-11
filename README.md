# RMP App

Монорепозиторий учебного проекта «РМП», включающий:

- `MobileApp/` — Android-клиент на Jetpack Compose.
- `backend/` — серверная часть на Ktor с PostgreSQL.

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
