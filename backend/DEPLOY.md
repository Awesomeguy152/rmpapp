# Деплой на Railway (бесплатно)

## Шаги:

### 1. Создай аккаунт

Зайди на [railway.app](https://railway.app) и зарегистрируйся через GitHub.

### 2. Создай новый проект

1. Нажми **"New Project"**
2. Выбери **"Deploy from GitHub repo"**
3. Выбери репозиторий `rmpapp`
4. В настройках укажи **Root Directory**: `рмп бэк/kt-backend/backend`

### 3. Добавь PostgreSQL

1. В проекте нажми **"+ New"** → **"Database"** → **"PostgreSQL"**
2. Railway автоматически создаст переменные окружения

### 4. Настрой переменные окружения

В настройках сервиса добавь:

```
JWT_SECRET=your-super-secret-key-change-this
ADMIN_REGISTRATION_SECRET=your-admin-secret
DATABASE_URL=${{Postgres.DATABASE_URL}}
```

Railway автоматически подставит URL базы данных.

### 5. Обнови Database.kt для Railway

В файле `Database.kt` нужно парсить `DATABASE_URL`:

```kotlin
// Railway предоставляет DATABASE_URL в формате:
// postgresql://user:password@host:port/database

val databaseUrl = System.getenv("DATABASE_URL")
if (databaseUrl != null) {
    // Парсинг Railway DATABASE_URL
    val uri = URI(databaseUrl)
    val (user, password) = uri.userInfo.split(":")
    val jdbcUrl = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"
    // Используй эти переменные для подключения
}
```

### 6. Деплой!

Railway автоматически задеплоит при пуше в репозиторий.

---

## После деплоя:

1. Railway даст URL типа: `https://your-app.up.railway.app`
2. Обнови `BASE_URL` в мобильном приложении:

```kotlin
// MobileApp/app/build.gradle.kts
release {
    buildConfigField("String", "BASE_URL", "\"https://your-app.up.railway.app\"")
}
```

---

## Альтернативы:

### Render.com

1. Зайди на [render.com](https://render.com)
2. New → Web Service → Connect GitHub
3. New → PostgreSQL (бесплатно 90 дней)

### Fly.io

```bash
# Установи flyctl
brew install flyctl

# Залогинься
fly auth login

# Создай приложение
fly launch

# Создай PostgreSQL
fly postgres create

# Деплой
fly deploy
```

---

## Примечания:

- Бесплатные тиры засыпают при неактивности (первый запрос медленный)
- Для production рекомендуется платный план (~$5-10/месяц)
