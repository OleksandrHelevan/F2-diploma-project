# Bricklayers

**Студент:** Гелеван Олександр Віталійович
**Науковий керівник:** Валь Олександр Олександрович
**Спеціальність:** F2 Інженерія програмного забезпечення
**Тема:** Прогресивний вебзастосунок (PWA) для управління будівельними проєктами, обліку матеріалів та контролю робочого часу

---

## ФІНАЛЬНА ПРОПОЗИЦІЯ (UA)

### Bricklayers — розподілена платформа управління будівельним проєктом із геопросторовою верифікацією присутності та інтелектуальним аналізом дефектів

#### 1. Проблематика

Повоєнне відновлення формує попит на перевірювану звітність про хід робіт і ресурси. Учасники (замовник, прораб, бригади, постачальники) працюють у непов'язаних каналах. Три класи проблем:

- Немає єдиного джерела істини щодо стану об'єкта.
- Верифікація присутності в умовах обмеженої спостережуваності.
- Попередня оцінка дефектів без постійного експерта.

Складність — не в окремих компонентах, а в консистентності стану об'єкта в розподіленій системі за умови конфіденційності геоданих.

#### 2. Ідея

Подієво-орієнтована мікросервісна платформа, клієнт — PWA. Сервіси: автентифікація/RBAC; об'єкти та бізнес-процеси; облік часу з геоверифікацією; аналіз дефектів (LLM); сповіщення. Взаємодія переважно асинхронна.

#### 3. Функціонал

- **RBAC:** 5 ролей, перевірка ownership на рівні API, автозакриття сесій при блокуванні акаунта.
- **Геоверифікація часу:** часово обмежена QR-сесія, перевірка координат проти геозони (PostGIS), розрахунок годин за інтервалом, інваріант однієї активної сесії.
- **Моніторинг реального часу:** позиції на OpenStreetMap, пороги (радіус, час нерухомості) конфігуровані на рівні об'єкта; спрацювання правила — тригер перевірки людиною.
- **Життєвий цикл заявки як Saga:** формування → погодження → замовлення → снапшот ціни → автоповернення при відхиленні >N% → приймання з розбіжностями; компенсуючі транзакції.
- **Завдання:** призначення на бригаду/виконавця, підтвердження закриття прорабом.
- **AI-аналіз дефектів:** Gemini під Circuit Breaker/retry/bulkhead, історія рекомендацій, graceful degradation.
- **Фото-звітність:** timestamped записи, хронологічна стрічка, підтвердження перегляду.

#### 4. Архітектура

Мікросервіси + API Gateway · Saga (оркестрація) · CQRS · Outbox + ідемпотентність + DLQ · Resilience4j · JWT з ротацією refresh + RBAC + audit trail · privacy by design для геоданих.

#### 5. Стек

| Категорія | Технології |
|---|---|
| Мови | Java 21, TypeScript |
| Backend | Spring Boot, Security, Data JPA, Cloud Gateway, Resilience4j |
| Frontend | React, Service Worker (PWA), Leaflet.js |
| Дані | PostgreSQL + PostGIS, Redis (сесії, GEO) |
| Месиджинг | Apache Kafka |
| AI | Gemini API |
| Інфраструктура | Docker, Kubernetes, Helm, GitHub Actions |
| Тести | JUnit 5, Mockito, Testcontainers, Pact, Jest, RTL, k6 |
| Observability | Prometheus, Grafana, OpenTelemetry, Jaeger, Loki |

#### 6. Результати

- Прототип у Kubernetes.
- Модель станів заявки з компенсуючими транзакціями.
- CI/CD з quality gates, що блокують збірку.
- Результати k6 для піку check-in із SLO.
- C4-діаграми, ADR, OpenAPI.

---

## FINAL PROPOSAL (EN)

### Bricklayers – A Distributed Construction Project Management Platform with Geospatial Attendance Verification and AI-Assisted Defect Analysis

#### 1. Problem

Post-war reconstruction demands verifiable reporting on progress and resources. Client, site manager, crews and suppliers operate over disconnected channels. Three problem classes:

- No single source of truth about site state.
- Attendance verification under limited observability.
- Preliminary defect assessment without a permanently available expert.

The complexity lies in keeping site state consistent across a distributed system while preserving geodata confidentiality.

#### 2. Concept

Event-driven microservice platform with a PWA client. Services: authentication/RBAC; sites and business processes; time tracking with geospatial verification; LLM-based defect analysis; notifications. Communication is predominantly asynchronous.

#### 3. Features

- **RBAC:** five roles, API-level ownership checks, automatic session termination on account suspension.
- **Geospatial time verification:** time-bounded QR session, coordinate check against the site geofence (PostGIS), hours computed from the check-in/check-out interval, single-active-session invariant.
- **Real-time monitoring:** positions on an OpenStreetMap layer; thresholds (radius, immobility duration) configurable per site; a triggered rule initiates human verification, not an automated decision.
- **Material request lifecycle as a Saga:** creation – client approval – supplier order – price snapshot – automatic return on deviation above the threshold – receipt with discrepancy recording; compensating transactions.
- **Tasks:** crew or individual assignment, closure confirmed by the site manager.
- **AI defect analysis:** Gemini behind Circuit Breaker, retry and bulkhead; recommendation history; graceful degradation.
- **Photo reporting:** timestamped entries, chronological feed, read acknowledgement.

#### 4. Architecture

Microservices + API Gateway · orchestrated Saga · CQRS · Transactional Outbox, idempotent consumers, DLQ · Resilience4j · JWT with refresh rotation, RBAC, audit trail · privacy by design for geodata.

#### 5. Stack

| Category | Technologies |
|---|---|
| Languages | Java 21, TypeScript |
| Backend | Spring Boot, Security, Data JPA, Cloud Gateway, Resilience4j |
| Frontend | React, Service Worker (PWA), Leaflet.js |
| Data | PostgreSQL + PostGIS, Redis (sessions, GEO) |
| Messaging | Apache Kafka |
| AI | Gemini API |
| Infrastructure | Docker, Kubernetes, Helm, GitHub Actions |
| Testing | JUnit 5, Mockito, Testcontainers, Pact, Jest, RTL, k6 |
| Observability | Prometheus, Grafana, OpenTelemetry, Jaeger, Loki |

#### 6. Deliverables

- Prototype deployed to Kubernetes.
- Material-request state model with compensating transactions.
- CI/CD with build-blocking quality gates.
- k6 results for the peak check-in scenario against SLOs.
- C4 diagrams, ADR registry, OpenAPI specs.

---

## AI Disclosure

**ШІ:** стилістичний аудит, перевірка відповідності F2, перелік архітектурних ускладнень, переклад EN.
**Автор:** предметна область, проблематика, рольова модель і повний набір user stories з критеріями прийняття, базовий стек, відбір і відхилення рекомендацій ШІ, остаточна редакція.
**Модель:** Claude (Anthropic)
**Ітерацій:** 2
**Дата:** вересень 2026