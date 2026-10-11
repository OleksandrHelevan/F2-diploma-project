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

`CHAT_DEMO_ENABLED=true` у compose вмикає демо-ідентичності через заголовки
запитів. Це призначено лише для локального тестування; не вмикайте цей режим у
публічному чи production-середовищі.
Для запуску Compose обов’язково має бути заданий `MONGO_URI`; без нього Compose
завершиться з помилкою конфігурації замість спроби підключитися до localhost.

User Service використовує локальну PostgreSQL базу для профілів, паролів і
ролей; Keycloak-синхронізація вимкнена. Паролі зберігаються лише як BCrypt
хеші. Налаштуйте `JWT_SECRET` у `src/.env` як випадковий секрет щонайменше
32 символів; ним User Service підписує access tokens. Згенерувати секрет можна
командою `openssl rand -hex 32`.

У Postman реєструйте нового BUILDER через `POST /api/v1/auth/register`, а потім
отримуйте локальний JWT через `POST /api/v1/auth/login`. Використовуйте поле
`access_token` як Bearer-токен для `GET /api/v1/users/me`. Старі Keycloak JWT
після переходу на локальну автентифікацію більше не приймаються.

Для першого локального входу SUPERADMIN скопіюйте username відповідного
SUPERADMIN-профілю з локальної таблиці `users` у
`LOCAL_AUTH_BOOTSTRAP_SUPERADMIN_USERNAME`, задайте одноразовий
`LOCAL_AUTH_BOOTSTRAP_SUPERADMIN_PASSWORD` у `src/.env` і перебудуйте
`user-service`. Пароль буде записаний як BCrypt хеш лише якщо профіль ще не має
пароля; після першого запуску видаліть bootstrap пароль із `.env`. Увійшовши
SUPERADMIN-ом, задайте пароль наявному локальному користувачу через
`PUT /api/v1/admin/users/{id}/password` з JSON `{"password":"..."}`. Публічна
реєстрація не може змінити пароль уже наявного профілю.

Чат-сервіс також надає REST API під `/api/v1/chats` для перегляду й створення
чатів, історії повідомлень, надсилання/редагування/видалення повідомлень,
реакцій і статусів прочитання. WebSocket STOMP endpoint: `/ws`; клієнт
підписується на `/topic/chats/{chatId}` і надсилає подію набору тексту в
`/app/chats/{chatId}/typing`.

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
