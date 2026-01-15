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
# Обязательные
JWT_SECRET=your-super-secret-key-change-this
ADMIN_REGISTRATION_SECRET=your-admin-secret
DATABASE_URL=${{Postgres.DATABASE_URL}}

# OpenAI (для AI извлечения встреч)
OPENAI_API_KEY=sk-...

# ============ EMAIL (для сброса пароля) ============
# РЕКОМЕНДУЕТСЯ: Resend API (бесплатно 100 писем/день, мгновенная активация)
RESEND_API_KEY=re_xxxxxxxx...

# Альтернатива 1: Brevo API (бесплатно 300 писем/день, требует активации аккаунта)
# BREVO_API_KEY=xkeysib-xxxxxxxx...

# Альтернатива 2: SMTP (Gmail, Yandex, etc.)
# SMTP_HOST=smtp.gmail.com
# SMTP_PORT=587
# SMTP_USER=your-email@gmail.com
# SMTP_PASS=your-app-password
# SMTP_FROM=no-reply@yourdomain.com
# SMTP_STARTTLS=true

# Принудительно использовать SMTP (игнорировать API провайдеры)
# FORCE_SMTP=true

# Firebase Push Notifications (опционально)
# FIREBASE_SERVICE_ACCOUNT_PATH=/app/firebase-service-account.json
```

Railway автоматически подставит URL базы данных.

### 4.1 Настройка Resend (рекомендуется для email)

**Resend** - современный сервис отправки email (100 писем/день бесплатно, мгновенная активация).

1. Зарегистрируйтесь на [resend.com](https://resend.com/)
2. Перейдите в **API Keys** и создайте новый ключ
3. Скопируйте ключ и добавьте в Railway:
   ```
   RESEND_API_KEY=re_xxxxxxxxxxxxxxxx...
   ```

**Важно:** По умолчанию Resend использует `onboarding@resend.dev` как отправителя. Для своего домена нужно настроить DNS.

### 4.2 Альтернатива: Brevo API

**Brevo** (ранее Sendinblue) - бесплатно 300 писем/день, но требует активации аккаунта.

1. Зарегистрируйтесь на [brevo.com](https://www.brevo.com/)
2. Перейдите в **Settings** → **SMTP & API** → **API Keys**
3. Создайте API ключ и добавьте в Railway:
   ```
   BREVO_API_KEY=xkeysib-xxxxxxxxxxxxxxxx...
   ```
4. **Важно:** Обратитесь в поддержку Brevo для активации аккаунта!

### 4.3 Альтернатива: Gmail SMTP

Для Gmail используйте App Password:

1. Включите 2FA в Google аккаунте
2. Создайте App Password: https://myaccount.google.com/apppasswords
3. Добавьте в Railway:
   ```
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USER=your-email@gmail.com
   SMTP_PASS=xxxx-xxxx-xxxx-xxxx
   SMTP_STARTTLS=true
   FORCE_SMTP=true
   ```

### 4.4 Проверка работы email

После настройки проверьте работу email:

```bash
# Проверить конфигурацию
curl https://your-app.up.railway.app/api/auth/mail-config

# Отправить тестовое письмо
curl -X POST https://your-app.up.railway.app/api/auth/test-email \
  -H "Content-Type: application/json" \
  -d '{"email": "your-email@example.com"}'
```

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
