# Розділ 2. Проєктування

Розділ формалізує архітектуру системи Bricklayers на основі контракту вимог [srs.md](./srs.md). Архітектура описана за методологією C4 Model (рівні Context, Container, Component) у форматі Diagrams-as-Code (Mermaid.js), архітектурні компроміси зафіксовано в реєстрі [ADR](./adr/README.md).

**Нотація.** Діаграми побудовано засобами Mermaid `flowchart` з кольоровою нотацією C4: темно-синій — [Person], синій — [Software System] / [Container], блакитний — [Component], жовтий — доменне ядро, помаранчевий — компоненти захисту, сірий — [External System]. Кожен зв'язок підписано технологією або протоколом у квадратних дужках. Експериментальний синтаксис `C4Context`/`C4Container` у Mermaid не застосовано, оскільки він не має автоматичного розміщення елементів і накладає підписи зв'язків (див. AI Insights до ЛР№5).

## 2.1. Контекстне та контейнерне моделювання системи (C4 Model)

<a id="diagram-level1"></a>

### 2.1.1. System Context Diagram (C4 Level 1)

```mermaid
flowchart TB
    subgraph actors[" "]
        direction LR
        admin["<b>Суперадміністратор</b><br/>[Person]<br/>Акаунти, ролі, об'єкти, геозони"]
        customer["<b>Замовник</b><br/>[Person]<br/>Погодження заявок, перегляд прогресу"]
        foreman["<b>Прораб</b><br/>[Person]<br/>QR-сесії, заявки, завдання, звіти"]
        worker["<b>Будівельник</b><br/>[Person]<br/>Check-in/check-out, дефекти"]
        supplier["<b>Постачальник</b><br/>[Person]<br/>Підтвердження замовлення і ціни"]
    end

    bricklayers["<b>Bricklayers</b><br/>[Software System]<br/>Розподілена платформа управління будівельним проєктом:<br/>PWA-клієнт, API Gateway, мікросервіси, Apache Kafka"]

    subgraph externals[" "]
        direction LR
        gemini["<b>Google Gemini API</b><br/>[External System]<br/>LLM-оцінка дефектів"]
        osm["<b>OpenStreetMap Tiles</b><br/>[External System]<br/>Картографічна підкладка"]
        push["<b>Web Push Service</b><br/>[External System]<br/>FCM, Mozilla Autopush"]
        smtp["<b>SMTP Relay</b><br/>[External System]<br/>Запрошення, скидання пароля"]
    end

    admin -->|"Адміністрування<br/>[HTTPS / REST, JWT]"| bricklayers
    customer -->|"Погодження, стрічка<br/>[HTTPS / REST, JWT]"| bricklayers
    foreman -->|"QR-сесії, заявки, звіти<br/>[HTTPS / REST, JWT]"| bricklayers
    worker -->|"Check-in, координати<br/>[HTTPS / REST, JWT]"| bricklayers
    supplier -->|"Підтвердження ціни<br/>[HTTPS / REST, JWT]"| bricklayers

    bricklayers -->|"Оцінка дефекту без ПДн<br/>[HTTPS / REST, API key]"| gemini
    bricklayers -->|"Тайли з браузера<br/>[HTTPS / XYZ tiles]"| osm
    bricklayers -->|"Сповіщення<br/>[HTTPS / Web Push, VAPID]"| push
    bricklayers -->|"Листи<br/>[SMTP / STARTTLS :587]"| smtp

    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef system fill:#1168bd,stroke:#0b4884,color:#ffffff
    classDef ext fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    classDef frame fill:none,stroke:none
    class admin,customer,foreman,worker,supplier person
    class bricklayers system
    class gemini,osm,push,smtp ext
    class actors,externals frame
```

**Актори.** Суперадміністратор, замовник, прораб, будівельник і постачальник взаємодіють із системою через PWA-клієнт за протоколом HTTPS / REST з access-токеном JWT ([SRS-FUN-01](./srs.md#srs-fun-01), [SRS-FUN-02](./srs.md#srs-fun-02)). Повноваження ролей наведено в [SRS, розділ 2.2](./srs.md#22-користувачі).

**Зовнішні системи.**

| Система | Протокол | Призначення | Вимоги |
| --- | --- | --- | --- |
| Google Gemini API | HTTPS / REST, API key | Консультативна оцінка знеособленого опису дефекту | [SRS-FUN-24](./srs.md#srs-fun-24) – [SRS-FUN-26](./srs.md#srs-fun-26), [SRS-NFR-REL-06](./srs.md#srs-nfr-rel-06) |
| OpenStreetMap Tiles | HTTPS / XYZ tiles | Картографічна підкладка; запити надходять безпосередньо з браузера | [SRS-FUN-12](./srs.md#srs-fun-12), [SRS-CON-03](./srs.md#srs-con-03) |
| Web Push Service | HTTPS / Web Push (RFC 8030), VAPID (RFC 8292) | Доставка push-сповіщень у браузер | [SRS-FUN-29](./srs.md#srs-fun-29) |
| SMTP Relay | SMTP / STARTTLS, порт 587 | Листи-запрошення та скидання пароля від Identity Provider | [SRS-FUN-01](./srs.md#srs-fun-01) |

<a id="diagram-level2"></a>

### 2.1.2. Container Diagram (C4 Level 2)

```mermaid
flowchart TB
    users["<b>Користувачі платформи</b><br/>[Person × 5 ролей]<br/>Суперадміністратор, замовник,<br/>прораб, будівельник, постачальник"]

    subgraph bricklayers["Bricklayers [Software System] — Kubernetes cluster"]
        pwa["<b>PWA Client</b><br/>[Container: React 18, TypeScript,<br/>Service Worker, Leaflet]<br/>UI, офлайн-кеш, Background Sync"]
        ingress["<b>Ingress Controller</b><br/>[Container: NGINX Ingress]<br/>TLS-термінація, роздача статики PWA"]
        gateway["<b>API Gateway</b><br/>[Container: Spring Cloud Gateway]<br/>Валідація JWT, маршрутизація,<br/>rate limiting, CORS"]
        idp["<b>Identity Provider</b><br/>[Container: Keycloak 26]<br/>OAuth 2.0 / OIDC, JWT RS256"]

        project["<b>project-service</b><br/>[Container: Spring Boot]<br/>Об'єкти, геозони,<br/>призначення, ownership"]
        attendance["<b>attendance-service</b><br/>[Container: Spring Boot]<br/>QR-сесії, check-in/out,<br/>геопотік, аномалії"]
        order["<b>order-service</b><br/>[Container: Spring Boot]<br/>Заявки, Saga-оркестратор,<br/>снапшот ціни"]
        task["<b>task-service</b><br/>[Container: Spring Boot]<br/>Завдання, статуси"]
        report["<b>report-service</b><br/>[Container: Spring Boot]<br/>Записи прогресу, стрічка"]
        ai["<b>ai-service</b><br/>[Container: Spring Boot,<br/>Resilience4j]<br/>Дефекти, LLM-оцінка"]
        notify["<b>notification-service</b><br/>[Container: Spring Boot]<br/>Web Push, підписки"]

        kafka[["<b>Apache Kafka 3.8</b><br/>[Container: KRaft, 3 брокери]<br/>Доменні події, retry/DLQ"]]
        pg[("<b>PostgreSQL 16 + PostGIS</b><br/>[Container: primary + replica]<br/>Окрема БД на сервіс")]
        redis[("<b>Redis 7</b><br/>[Container]<br/>Сесії, GEO, rate limit")]
        minio[("<b>Object Storage</b><br/>[Container: MinIO, S3 API]<br/>Фото прогресу і дефектів")]

        otel["<b>OTel Collector</b><br/>[Container]<br/>Траси, метрики, логи"]
        obsb["<b>Observability Backend</b><br/>[Container: Prometheus,<br/>Loki, Jaeger, Grafana]"]
    end

    gemini["<b>Google Gemini API</b><br/>[External System]"]
    osm["<b>OpenStreetMap Tiles</b><br/>[External System]"]
    push["<b>Web Push Service</b><br/>[External System]"]
    smtp["<b>SMTP Relay</b><br/>[External System]"]

    users -->|"UI у браузері [HTTPS]"| pwa
    pwa -->|"API, OIDC login<br/>[HTTPS / REST, TLS 1.3]"| ingress
    pwa -->|"Тайли [HTTPS]"| osm
    ingress -->|"/api/** [HTTP/1.1]"| gateway
    ingress -->|"/auth/** [HTTP/1.1, OIDC]"| idp
    gateway -->|"JWKS [HTTP, RFC 7517]"| idp
    gateway -->|"Token bucket<br/>[RESP / TCP 6379]"| redis

    gateway -->|"[REST / HTTP/1.1, JSON]"| attendance
    gateway --> project & order & task & report & ai & notify

    project -->|"Admin REST API [HTTP]"| idp
    attendance -->|"Сесії, GEO<br/>[RESP / TCP 6379]"| redis

    attendance -->|"[SQL / JDBC, TCP 5432]"| pg
    project & order & task & report & ai & notify --> pg
    idp -->|"[SQL / JDBC]"| pg

    order <-->|"Outbox → події<br/>[Kafka protocol / TCP 9092]"| kafka
    project & attendance & task & report & ai & notify <--> kafka

    report -->|"Presigned URL<br/>[S3 API / HTTPS]"| minio
    ai -->|"generateContent<br/>[HTTPS / REST]"| gemini
    notify -->|"[HTTPS / Web Push, VAPID]"| push
    idp -->|"[SMTP / STARTTLS :587]"| smtp

    gateway -.->|"усі контейнери<br/>[OTLP / gRPC :4317]"| otel
    otel -.->|"[Remote write, OTLP/HTTP]"| obsb

    classDef person fill:#08427b,stroke:#052e56,color:#ffffff
    classDef container fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef store fill:#2f6fad,stroke:#1f4f7f,color:#ffffff
    classDef ext fill:#8a8a8a,stroke:#6b6b6b,color:#ffffff
    class users person
    class pwa,ingress,gateway,idp,project,attendance,order,task,report,ai,notify,otel,obsb container
    class kafka,pg,redis,minio store
    class gemini,osm,push,smtp ext
    style bricklayers fill:#f4f8fc,stroke:#1168bd,stroke-dasharray:6 4
```

**Контейнери.**

| Контейнер | Технологія | Відповідальність | Вимоги / рішення |
| --- | --- | --- | --- |
| PWA Client | React 18, TypeScript, Service Worker, Leaflet | UI, кешування app shell, офлайн-буфер (Background Sync), Web Geolocation API | [SRS-FUN-30](./srs.md#srs-fun-30), [SRS-NFR-PERF-07](./srs.md#srs-nfr-perf-07) |
| Ingress Controller | NGINX Ingress | TLS-термінація, HSTS, роздача статичних ресурсів PWA, маршрутизація `/api/**` і `/auth/**` | [SRS-NFR-SEC-02](./srs.md#srs-nfr-sec-02) |
| API Gateway | Spring Cloud Gateway | Валідація JWT за JWKS, грубозерниста RBAC-перевірка, rate limiting (Redis token bucket), CORS, проброс `traceparent` | [SRS-FUN-02](./srs.md#srs-fun-02), [SRS-NFR-SEC-03](./srs.md#srs-nfr-sec-03) |
| Identity Provider | Keycloak 26 | OAuth 2.0 / OIDC, видача JWT RS256, ролі, ротація refresh-токенів, Argon2id | [SRS-FUN-01](./srs.md#srs-fun-01), [SRS-NFR-SEC-01](./srs.md#srs-nfr-sec-01) |
| project-service | Spring Boot 3.4, Java 21 | Об'єкти, геозони, призначення користувачів, перевірка ownership, блокування акаунтів через Keycloak Admin REST API, audit trail | [SRS-FUN-03](./srs.md#srs-fun-03) – [SRS-FUN-05](./srs.md#srs-fun-05) |
| attendance-service | Spring Boot 3.4, Java 21 | QR-сесії, check-in/check-out, геопотік, детекція аномалій | [SRS-FUN-06](./srs.md#srs-fun-06) – [SRS-FUN-14](./srs.md#srs-fun-14); [C4 Level 3](#diagram-level3) |
| order-service | Spring Boot 3.4, Java 21 | Заявки, Saga-оркестратор як внутрішній компонент, снапшот ціни, компенсуючі транзакції | [SRS-FUN-15](./srs.md#srs-fun-15) – [SRS-FUN-21](./srs.md#srs-fun-21) |
| task-service | Spring Boot 3.4, Java 21 | Завдання, призначення, підтвердження закриття | [SRS-FUN-22](./srs.md#srs-fun-22), [SRS-FUN-23](./srs.md#srs-fun-23) |
| report-service | Spring Boot 3.4, Java 21 | Записи прогресу, хронологічна стрічка, факт перегляду, presigned URL для фото | [SRS-FUN-27](./srs.md#srs-fun-27), [SRS-FUN-28](./srs.md#srs-fun-28) |
| ai-service | Spring Boot 3.4, Resilience4j | Звіти про дефекти, знеособлення тексту, виклик Gemini API, історія рекомендацій | [SRS-FUN-24](./srs.md#srs-fun-24) – [SRS-FUN-26](./srs.md#srs-fun-26) |
| notification-service | Spring Boot 3.4 | Споживання доменних подій, підписки Web Push, надсилання сповіщень | [SRS-FUN-29](./srs.md#srs-fun-29), [SRS-NFR-PERF-06](./srs.md#srs-nfr-perf-06) |
| Apache Kafka | Kafka 3.8, KRaft, 3 брокери | Доменні події, retry- і DLQ-топіки | [SRS-NFR-REL-03](./srs.md#srs-nfr-rel-03), [SRS-NFR-REL-04](./srs.md#srs-nfr-rel-04); [ADR-001](./adr/0001-choice-of-technology.md) |
| PostgreSQL + PostGIS | PostgreSQL 16, PostGIS 3.4, primary + streaming replica | Окрема логічна БД на кожен сервіс і Keycloak; геопросторові запити | [SRS-FUN-07](./srs.md#srs-fun-07), [SRS-NFR-REL-02](./srs.md#srs-nfr-rel-02) |
| Redis | Redis 7 | Кеш активних сесій, GEO-позиції, rate-limit buckets | [SRS-FUN-11](./srs.md#srs-fun-11), [SRS-NFR-PERF-05](./srs.md#srs-nfr-perf-05) |
| Object Storage | MinIO (S3 API) | Зображення записів прогресу та дефектів | [SRS-FUN-27](./srs.md#srs-fun-27) |
| OTel Collector | OpenTelemetry Collector | Прийом трас, метрик і логів від усіх контейнерів | [SRS-NFR-OBS-01](./srs.md#srs-nfr-obs-01) – [SRS-NFR-OBS-03](./srs.md#srs-nfr-obs-03) |
| Observability Backend | Prometheus, Loki, Jaeger, Grafana | Зберігання телеметрії, дашборди, алерти | [SRS-NFR-OBS-04](./srs.md#srs-nfr-obs-04) |

**Зв'язки та протоколи.**

| Від → До | Протокол | Призначення |
| --- | --- | --- |
| PWA → Ingress | HTTPS / REST, TLS 1.3 | API-виклики та OIDC-логін (Authorization Code + PKCE) |
| PWA → OpenStreetMap Tiles | HTTPS | Завантаження тайлів карти |
| Ingress → API Gateway | HTTP/1.1 | Маршрут `/api/**` усередині кластера |
| Ingress → Identity Provider | HTTP/1.1, OIDC | Маршрут `/auth/**` |
| API Gateway → Identity Provider | HTTP, JWKS (RFC 7517) | Отримання та кешування публічних ключів підпису JWT |
| API Gateway → Redis | RESP / TCP 6379 | Лічильники token bucket для rate limiting |
| API Gateway → доменні сервіси | REST / HTTP/1.1, JSON | Проксування запитів із заголовками `Authorization` і `traceparent` |
| project-service → Identity Provider | HTTP / REST (Keycloak Admin API) | Блокування користувача, завершення сесій |
| attendance-service → Redis | RESP / TCP 6379 | Кеш активних сесій, `GEOADD`/`GEOSEARCH` |
| Доменні сервіси, Keycloak → PostgreSQL | SQL / JDBC, TCP 5432 | Транзакційні дані; таблиці outbox та оброблених подій |
| Доменні сервіси ↔ Apache Kafka | Kafka protocol / TCP 9092 | Публікація подій з outbox, споживання подій інших сервісів |
| report-service → Object Storage | S3 API / HTTPS | Генерація presigned URL; PWA завантажує файли напряму |
| ai-service → Google Gemini API | HTTPS / REST | Виклик `generateContent` під Circuit Breaker, Retry, Bulkhead |
| notification-service → Web Push Service | HTTPS / Web Push, VAPID | Надсилання зашифрованих push-повідомлень |
| Identity Provider → SMTP Relay | SMTP / STARTTLS, 587 | Листи-запрошення та скидання пароля |
| Усі контейнери → OTel Collector | OTLP / gRPC, 4317 | Траси, метрики, логи |
| OTel Collector → Observability Backend | Prometheus remote write, OTLP / HTTP | Експорт телеметрії |

Синхронні виклики між доменними сервісами відсутні: дані інших доменів (геозони, призначення, блокування користувачів) надходять через події Apache Kafka та зберігаються в локальних проєкціях, що відповідає вибору брокера в [ADR-001](./adr/0001-choice-of-technology.md). Мережеві зв'язки, не наведені в таблиці, забороняються політиками NetworkPolicy ([SRS-NFR-SEC-08](./srs.md#srs-nfr-sec-08)).

## 2.2. Компонентне моделювання критичного сервісу системи

### 2.2.1. Обґрунтування вибору сервісу

Для декомпозиції обрано **attendance-service**, оскільки він:

- несе найвище навантаження системи: піковий check-in ([SRS-NFR-PERF-03](./srs.md#srs-nfr-perf-03)) та безперервний потік геокоординат ≥ 100 запитів/с ([SRS-NFR-PERF-04](./srs.md#srs-nfr-perf-04));
- містить ключові інваріанти домену: геопросторова верифікація ([SRS-FUN-07](./srs.md#srs-fun-07)) та єдина активна сесія ([SRS-FUN-08](./srs.md#srs-fun-08));
- обробляє персональні геодані з вимогами комплаєнсу ([SRS-NFR-PRIV-01](./srs.md#srs-nfr-priv-01) – [SRS-NFR-PRIV-05](./srs.md#srs-nfr-priv-05));
- одночасно використовує PostgreSQL/PostGIS, Redis та Apache Kafka.

<a id="diagram-level3"></a>

### 2.2.2. Діаграма компонентів attendance-service (C4 Level 3)

```mermaid
flowchart TB
    gw["<b>API Gateway</b><br/>[Container: Spring Cloud Gateway]"]
    kin[["<b>Apache Kafka</b><br/>[Container]<br/>site.events, identity.events"]]

    subgraph svc["attendance-service [Container: Spring Boot 3.4, Java 21]"]
        subgraph inbound["Inbound layer (adapters)"]
            sec["<b>SecurityFilterChain</b><br/>[Spring Security<br/>OAuth2 Resource Server]<br/>Валідація JWT RS256"]
            guard["<b>SiteAccessGuard</b><br/>[@PreAuthorize +<br/>PermissionEvaluator]<br/>RBAC + ownership"]
            val["<b>RequestValidator</b><br/>[Jakarta Bean Validation]<br/>DTO, WGS 84, accuracy"]
            qrc["<b>QrSessionController</b><br/>[@RestController]"]
            cic["<b>AttendanceController</b><br/>[@RestController]<br/>check-in / check-out"]
            loc["<b>LocationController</b><br/>[@RestController]<br/>пакет координат"]
            cons["<b>DomainEventConsumer</b><br/>[@KafkaListener]<br/>ідемпотентний"]
            err["<b>GlobalExceptionHandler</b><br/>[@RestControllerAdvice]<br/>RFC 9457 Problem Details"]
        end

        subgraph app["Application layer (use cases)"]
            qrs["<b>QrSessionUseCase</b><br/>[Service]<br/>Видача токена з TTL"]
            cis["<b>CheckInUseCase</b><br/>[Service, @Transactional]<br/>Інваріант 1 активної сесії"]
            los["<b>LocationIngestionUseCase</b><br/>[Service]"]
            ano["<b>AnomalyDetectionJob</b><br/>[@Scheduled, ShedLock]<br/>Правила аномалій"]
            prj["<b>SiteProjectionUpdater</b><br/>[Service]<br/>Локальна копія геозон"]
        end

        subgraph domain["Domain core (без залежностей від фреймворку)"]
            geo["<b>GeofencePolicy</b><br/>[Domain Service]<br/>Допуск точності GPS"]
            wtc["<b>WorkTimeCalculator</b><br/>[Domain Service]"]
            rules["<b>AnomalyRules</b><br/>[Domain Service]<br/>out-of-zone, immobility"]
            ports["<b>Outbound Ports</b><br/>[Interfaces]<br/>AttendanceRepository, GeofenceQuery,<br/>ActiveSessionCache, LocationStore,<br/>DomainEventPublisher, ProcessedEventStore"]
        end

        subgraph outbound["Outbound layer (adapters)"]
            jpa["<b>JpaAttendanceAdapter</b><br/>[Spring Data JPA]"]
            pgis["<b>PostGisGeofenceAdapter</b><br/>[JdbcTemplate, ST_Covers]"]
            rcache["<b>RedisSessionCacheAdapter</b><br/>[Spring Data Redis]"]
            rgeo["<b>RedisGeoAdapter</b><br/>[GEOADD / GEOSEARCH]"]
            outw["<b>OutboxEventAdapter</b><br/>[JPA, та сама транзакція]"]
            relay["<b>OutboxRelay</b><br/>[@Scheduled + KafkaTemplate]<br/>idempotent producer"]
        end

        tel["<b>Telemetry</b><br/>[Micrometer, OTel Java agent]<br/>метрики, trace-id у логах"]
    end

    pg[("<b>PostgreSQL + PostGIS</b><br/>[Container]<br/>attendance_db")]
    redis[("<b>Redis 7</b><br/>[Container]")]
    kout[["<b>Apache Kafka</b><br/>[Container]<br/>attendance.events"]]
    otel["<b>OTel Collector</b><br/>[Container]"]

    gw -->|"[REST / HTTP/1.1, Bearer JWT]"| sec
    sec -->|"SecurityContext"| guard
    guard -->|"Авторизований запит"| val
    val --> qrc & cic & loc
    qrc -->|"Виклик інтерфейсу (DI)"| qrs
    cic -->|"Виклик інтерфейсу (DI)"| cis
    loc -->|"Виклик інтерфейсу (DI)"| los
    kin -->|"[Kafka protocol / TCP 9092]"| cons
    cons -->|"Виклик інтерфейсу (DI)"| prj

    qrs & cis & los & ano & prj -->|"Використовує"| ports
    cis -->|"Виклик"| geo & wtc
    ano -->|"Виклик"| rules
    qrc & cic & loc -.->|"Виключення"| err

    ports -.->|"Реалізується адаптерами (DIP)"| jpa & pgis & rcache & rgeo & outw

    jpa -->|"[SQL / JDBC]"| pg
    pgis -->|"[SQL / JDBC]"| pg
    outw -->|"INSERT outbox [SQL / JDBC]"| pg
    outw -->|"Polling, FOR UPDATE SKIP LOCKED"| relay
    relay -->|"[Kafka protocol / TCP 9092]"| kout
    rcache -->|"[RESP / TCP 6379]"| redis
    rgeo -->|"[RESP / TCP 6379]"| redis
    tel -.->|"[OTLP / gRPC :4317]"| otel

    classDef ext fill:#438dd5,stroke:#2e6295,color:#ffffff
    classDef comp fill:#85bbf0,stroke:#5d82a8,color:#000000
    classDef core fill:#f5d76e,stroke:#b39b3c,color:#000000
    classDef guardc fill:#e9967a,stroke:#a0634f,color:#000000
    class gw,kin,pg,redis,kout,otel ext
    class qrc,cic,loc,cons,qrs,cis,los,ano,prj,jpa,pgis,rcache,rgeo,outw,relay,tel comp
    class geo,wtc,rules,ports core
    class sec,guard,val,err guardc
    style svc fill:#f4f8fc,stroke:#1168bd,stroke-dasharray:6 4
```

### 2.2.3. Опис компонентів

Сервіс побудовано за принципами Clean Architecture (Ports and Adapters): залежності спрямовані до доменного ядра, доменне ядро не залежить від Spring, JPA, Redis чи Kafka. Зв'язування реалізацій з інтерфейсами виконує Spring IoC-контейнер (Dependency Injection через конструктор).

**Вхідний шар (Inbound adapters)**

| Компонент | Технологія | Роль | Взаємодія |
| --- | --- | --- | --- |
| SecurityFilterChain | Spring Security OAuth2 Resource Server | Повторна валідація підпису та строку дії JWT за JWKS (zero trust після API Gateway) | Servlet Filter, формує `SecurityContext` |
| SiteAccessGuard | `@PreAuthorize`, `PermissionEvaluator` | Перевірка ролі та належності користувача до об'єкта за локальною проєкцією призначень ([SRS-FUN-02](./srs.md#srs-fun-02), [SRS-FUN-03](./srs.md#srs-fun-03)) | Spring AOP-проксі перед методом контролера |
| RequestValidator | Jakarta Bean Validation | Валідація DTO: діапазони WGS 84, точність ≤ 50 м, розмір пакета координат ≤ 10 точок | `@Valid` на параметрах контролера |
| QrSessionController | `@RestController` | `POST /sites/{id}/qr-sessions` ([SRS-FUN-06](./srs.md#srs-fun-06)) | Викликає `QrSessionUseCase` через інтерфейс |
| AttendanceController | `@RestController` | `POST /attendance/check-in`, `POST /attendance/check-out` ([SRS-FUN-07](./srs.md#srs-fun-07) – [SRS-FUN-09](./srs.md#srs-fun-09)) | Викликає `CheckInUseCase` через інтерфейс |
| LocationController | `@RestController` | `POST /attendance/{sessionId}/locations` — пакет координат ([SRS-FUN-11](./srs.md#srs-fun-11)) | Викликає `LocationIngestionUseCase` |
| DomainEventConsumer | `@KafkaListener` | Споживання `SiteGeofenceUpdated`, `UserAssigned`, `UserBlocked`; дедуплікація за `eventId` ([SRS-NFR-REL-05](./srs.md#srs-nfr-rel-05)) | Викликає `SiteProjectionUpdater` |
| GlobalExceptionHandler | `@RestControllerAdvice` | Перетворення доменних виключень на RFC 9457 Problem Details (400, 403, 409, 422), без витоку стек-трейсів | Spring MVC exception resolution |

**Прикладний шар (Use cases)**

| Компонент | Роль |
| --- | --- |
| QrSessionUseCase | Видача підписаного токена QR-сесії з TTL 5–60 хв. |
| CheckInUseCase | Транзакційний сценарій check-in/check-out: перевірка токена, виклик `GeofencePolicy`, створення сесії, запис події в outbox в одній транзакції. |
| LocationIngestionUseCase | Запис поточної позиції в Redis GEO та сирих координат у PostgreSQL зі строком зберігання 90 діб ([SRS-NFR-PRIV-02](./srs.md#srs-nfr-priv-02)). |
| AnomalyDetectionJob | Періодичне (60 с) застосування `AnomalyRules` до активних сесій; ShedLock гарантує виконання на одній репліці; результат — подія `AnomalyDetected` ([SRS-FUN-13](./srs.md#srs-fun-13)). |
| SiteProjectionUpdater | Оновлення локальної копії геозон, призначень і блокувань; при `UserBlocked` закриває активну сесію ([SRS-FUN-04](./srs.md#srs-fun-04)). |

**Доменне ядро**

| Компонент | Роль |
| --- | --- |
| GeofencePolicy | Правило належності точки геозоні з урахуванням точності GPS; делегує геометричну перевірку порту `GeofenceQuery`. |
| WorkTimeCalculator | Обчислення тривалості сесії та автозакриття через 12 год ([SRS-FUN-10](./srs.md#srs-fun-10)). |
| AnomalyRules | Правила «вихід за геозону» та «нерухомість понад поріг» з порогами об'єкта. |
| Outbound Ports | Інтерфейси `AttendanceRepository`, `GeofenceQuery`, `ActiveSessionCache`, `LocationStore`, `DomainEventPublisher`, `ProcessedEventStore`, визначені в ядрі (Dependency Inversion Principle). |

**Вихідний шар (Outbound adapters)**

| Компонент | Технологія | Роль |
| --- | --- | --- |
| JpaAttendanceAdapter | Spring Data JPA | Реалізація `AttendanceRepository`; частковий унікальний індекс `UNIQUE (user_id) WHERE status = 'ACTIVE'` забезпечує [SRS-FUN-08](./srs.md#srs-fun-08). |
| PostGisGeofenceAdapter | JdbcTemplate, PostGIS | Реалізація `GeofenceQuery` через `ST_Covers(geofence, ST_Buffer(point::geography, accuracy))`. |
| RedisSessionCacheAdapter | Spring Data Redis | Реалізація `ActiveSessionCache` (TTL = залишок сесії), [SRS-NFR-PERF-05](./srs.md#srs-nfr-perf-05). |
| RedisGeoAdapter | Redis `GEOADD`/`GEOSEARCH` | Реалізація `LocationStore` для поточних позицій ([SRS-FUN-12](./srs.md#srs-fun-12)). |
| OutboxEventAdapter | JPA | Реалізація `DomainEventPublisher`: запис події в таблицю `outbox` у транзакції use case. |
| OutboxRelay | `@Scheduled`, KafkaTemplate | Вибірка записів outbox (`FOR UPDATE SKIP LOCKED`), публікація в `attendance.events` ідемпотентним producer з `acks=all` ([ADR-001](./adr/0001-choice-of-technology.md)). |
| Telemetry | Micrometer, OpenTelemetry Java agent | RED-метрики, `trace_id` у логах, експорт OTLP ([SRS-NFR-OBS-01](./srs.md#srs-nfr-obs-01) – [SRS-NFR-OBS-03](./srs.md#srs-nfr-obs-03)). |

### 2.2.4. Проходження запиту check-in крізь компоненти

1. API Gateway проксує `POST /attendance/check-in` з JWT; `SecurityFilterChain` валідує токен.
2. `SiteAccessGuard` перевіряє роль «Будівельник» і призначення на об'єкт; `RequestValidator` валідує координати й точність.
3. `AttendanceController` викликає `CheckInUseCase` через інтерфейс.
4. `CheckInUseCase` у межах однієї транзакції PostgreSQL: перевіряє токен QR-сесії, викликає `GeofencePolicy` (→ `GeofenceQuery` → PostGIS), створює запис сесії (→ `AttendanceRepository`) і записує подію `CheckedIn` (→ `DomainEventPublisher` → таблиця outbox).
5. Порушення унікального індексу перетворюється на `ActiveSessionExistsException`, яке `GlobalExceptionHandler` повертає як HTTP 409.
6. Після commit сесія кешується в Redis; `OutboxRelay` публікує подію в Apache Kafka, звідки її споживає notification-service.

## 2.3. Документування архітектурних рішень (Architecture Decision Records)

| ID | Рішення | Статус | Дата |
| --- | --- | --- | --- |
| [ADR-001](./adr/0001-choice-of-technology.md) | Вибір брокера повідомлень для доменних подій: Apache Kafka | Approved | 10.11.2026 |

Повний реєстр рішень: [adr/README.md](./adr/README.md).
