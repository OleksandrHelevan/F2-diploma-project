# Bricklayers — навігація документацією

**Студент:** Гелеван Олександр Віталійович · **Керівник:** Валь Олександр Олександрович · **Спеціальність:** F2 Інженерія ПЗ

## Документи

| Документ | Опис |
| --- | --- |
| [Розділ 1 (Google Docs, Can comment)](ВСТАВИТИ_ПОСИЛАННЯ_НА_GOOGLE_DOCS) | Зведений робочий файл F2_Diploma_Chapter1_Гелеван |
| [0_intro.md](./0_intro.md) | Вступ: актуальність, об'єкт, предмет, мета, завдання |
| [1_analysis.md](./1_analysis.md) | Розділ 1: підходи, аналоги, As-Is, To-Be, Core Features, NFRs |
| [srs.md](./srs.md) | Software Requirements Specification за ISO/IEC/IEEE 29148: контекст, 30 функціональних вимог, NFRs Matrix, критерії верифікації, глосарій |
| [2_design.md](./2_design.md) | Розділ 2. Проєктування: C4 Model (Context, Container, Component), реєстр ADR |
| [adr/](./adr/README.md) | Architecture Decision Records: [ADR-001 — Apache Kafka](./adr/0001-choice-of-technology.md) |
| [3_implementation.md](./3_implementation.md) | Розділ 3. Реалізація та тестування (у розробці) |
| [4_conclusions.md](./4_conclusions.md) | Висновки (у розробці) |

## Архітектура: швидкий доступ

| Артефакт | Посилання |
| --- | --- |
| C4 Level 1 — System Context | [2_design.md#diagram-level1](./2_design.md#diagram-level1) |
| C4 Level 2 — Container | [2_design.md#diagram-level2](./2_design.md#diagram-level2) |
| C4 Level 3 — Component (attendance-service) | [2_design.md#diagram-level3](./2_design.md#diagram-level3) |
| Трасування вимог в архітектуру | [srs.md, розділ 6](./srs.md#6-трасування-вимог-в-архітектуру) |

## Стан календарного плану

| № | Етап | Статус |
| --- | --- | --- |
| 1 | Аналіз предметної області та аналогів, формування вимог (SLO) | Done |
| 2 | Обґрунтування архітектурного стилю та стеку, реєстр ADR | In Progress |
| 3 | Проєктування архітектури системи: C4 Model, UML-діаграми | In Progress |
| 4 | Модель даних PostgreSQL/PostGIS, специфікації API (OpenAPI, AsyncAPI) | To Do |

Повний календарний план: [README.md](../README.md#календарний-план).

## NFRs Matrix (скорочено)

Повна матриця з ідентифікаторами та критеріями верифікації — у [srs.md, розділ 4](./srs.md#4-non-functional-requirements-нефункціональні-вимоги).

| ID | Метрика | Цільове значення |
| --- | --- | --- |
| [SRS-NFR-PERF-01](./srs.md#srs-nfr-perf-01) | Latency p95, читання | ≤ 200 мс при 500 RPS |
| [SRS-NFR-PERF-02](./srs.md#srs-nfr-perf-02) | Latency p95, запис | ≤ 400 мс при 100 RPS |
| [SRS-NFR-PERF-03](./srs.md#srs-nfr-perf-03) | Пік check-in | 300 check-in за 5 хв: p95 ≤ 400 мс, помилки < 1 % |
| [SRS-NFR-SCAL-01](./srs.md#srs-nfr-scal-01) | Активні робочі сесії | ≥ 5000; HPA 2–10 реплік (CPU 70 %) |
| [SRS-NFR-SEC-01](./srs.md#srs-nfr-sec-01) | Автентифікація | OAuth 2.0 / OIDC, JWT RS256 (access 15 хв, refresh 7 діб з ротацією) |
| [SRS-NFR-SEC-03](./srs.md#srs-nfr-sec-03) | Rate limiting | 20 запитів/с на користувача, HTTP 429 |
| [SRS-NFR-SEC-05](./srs.md#srs-nfr-sec-05) | Контейнерні образи | 0 вразливостей HIGH/CRITICAL (Trivy) |
| [SRS-NFR-PRIV-02](./srs.md#srs-nfr-priv-02) | Геодані | Зберігання ≤ 90 діб; ЗУ «Про захист персональних даних», принципи GDPR |
| [SRS-NFR-REL-01](./srs.md#srs-nfr-rel-01) | Доступність / RPO / RTO | 99,5 % / ≤ 15 хв / ≤ 60 хв |
| [SRS-NFR-REL-06](./srs.md#srs-nfr-rel-06) | Відмовостійкість AI | Circuit Breaker: ≥ 50 % помилок у вікні 20 викликів, пауза 30 с |
| [SRS-NFR-QUAL-02](./srs.md#srs-nfr-qual-02) | Якість коду | ≥ 80 % покриття; SonarQube Quality Gate: Passed |
