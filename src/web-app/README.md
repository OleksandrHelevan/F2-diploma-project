# React + TypeScript + Vite

## Локальне демо чату

Із теки `src` запустіть усі сервіси:

```sh
docker compose up --build
```

Compose читає налаштування з `src/.env` (цей файл не комітиться). Приклади
значень наведено в `src/.env.example`; оскільки `.env` може вже містити локальні
секрети, переносьте відсутні параметри з прикладу вручну й не перезаписуйте
існуючий файл. Замініть `change-me` на власні паролі перед запуском.
Для чатів використовується наявний `MONGO_URI` із рядком підключення MongoDB
Atlas. У Atlas перевірте, що IP-адреса має доступ у Network Access, а користувач
із URI має права на вибрану базу даних.

Відкрийте `http://localhost:3000` у двох різних браузерах. У першому виберіть
«Прораб» і натисніть «Відкрити демо-чат», у другому виберіть «Будівельник» і
відкрийте той самий чат. Повідомлення, статус набору тексту й реакції оновлюються
через STOMP/WebSocket без перезавантаження сторінки. Роль зберігається окремо для
кожного браузерного сеансу.

Для завершення зупиніть compose-команду через `Ctrl+C` (або виконайте
`docker compose down` у теці `src`). Дані MongoDB залишаються у volume
`chats_db_data`.

`CHAT_DEMO_ENABLED=true` у `src/.env` вмикає демо-ідентичності через заголовки
запитів. За замовчуванням цей режим вимкнений: REST API і WebSocket чату
приймають лише Keycloak Bearer token. Демо-режим призначено тільки для
локального тестування; не вмикайте його у публічному чи production-середовищі.
Для запуску Compose обов’язково має бути заданий `MONGO_URI`; без нього Compose
завершиться з помилкою конфігурації замість спроби підключитися до localhost.

Keycloak є єдиним джерелом автентифікації, реєстрації, паролів і access token-ів.
PostgreSQL у User Service зберігає лише профіль користувача та `keycloak_id`;
пароль і його хеш у цій базі не зберігаються. Реєстрація та вхід відбуваються
на hosted login/register сторінках Keycloak. API `/api/v1/auth/login` і
`/api/v1/auth/register` у User Service видалені. Синхронізація Keycloak
користувачів створює локальні профілі.

`POST /api/v1/users` для SUPERADMIN створює користувача та його роль у Keycloak,
а потім локальний профіль із `keycloak_id`. Якщо збереження профілю не вдається,
створений Keycloak акаунт видаляється. Такий акаунт починається без пароля:
SUPERADMIN може задати його через `PUT /api/v1/admin/users/{id}/password`.
Секрет сервісної інтеграції `KEYCLOAK_ADMIN_CLIENT_SECRET` має збігатися з
клієнтом `user-sync-service` у файлі realm. Access token-и повинні містити
audiences `user-service` і `chat-service`.

Реєстрацію користувачів вмикає `registrationAllowed` у realm, тоді посилання
Register з'являється на сторінці входу Keycloak. Тема `bricklayers` уже
підключена з `src/keycloak/themes/bricklayers/login`: змінюйте
`resources/css/login.css` для оформлення, `theme.properties` для налаштувань;
для повної кастомізації можна додати шаблони FreeMarker, повідомлення та
зображення. Сторінки login і register використовують спільну login theme.

Якщо realm вже існував до змін, імпорт realm не перезаписує його. Оновіть
`bricklayers-web-app` redirect URI для Swagger, audience mappers,
самореєстрацію та login theme `bricklayers` через Keycloak Admin Console.

Після оновлення існуючої бази Liquibase видаляє застарілу колонку
`password_hash`. Для вже існуючих Keycloak користувачів синхронізація зв'яже
локальний профіль за username/email і запише `keycloak_id`.

Чат-сервіс також надає REST API під `/api/v1/chats` для перегляду й створення
чатів, історії повідомлень, надсилання/редагування/видалення повідомлень,
реакцій і статусів прочитання. WebSocket STOMP endpoint: `/ws`; клієнт
підписується на `/topic/chats/{chatId}` і надсилає подію набору тексту в
`/app/chats/{chatId}/typing`.

Усі три сервіси мають Swagger UI: User Service — `http://localhost:8081/swagger-ui.html`,
Chat Service — `http://localhost:8083/swagger-ui.html`, а документовані OIDC та
Admin REST endpoints Keycloak — `http://localhost:8085`. У Swagger UI сервісів
натисніть **Authorize**, увійдіть через Keycloak і використовуйте отриманий
Bearer token. У User Service `GET /api/v1/users/by-keycloak-id/{keycloakId}`
використовується Chat Service для зв'язування Keycloak `sub` з профілем;
внутрішній виклик передає той самий токен, що й запит клієнта.

Для Swagger натисніть **Authorize** і виконайте OAuth Authorization Code +
PKCE через Keycloak. Наявна у браузері Keycloak SSO-сесія буде повторно
використана. API отримує access token саме залогіненого користувача, тому
адміністративні операції User Service доступні лише ролі `SUPERADMIN`;
інші ролі отримують `403` там, де їм заборонено доступ. Swagger показує всі
операції, а авторизацію завжди перевіряє сам API.

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```
