# DVO-L4-3C8B52
# Bricklayers — Kubernetes (лабораторна №3)

У каталозі [`k8s/`](./k8s/) розміщено окремі маніфести для розгортання Bricklayers у namespace `bricklayers`: web-app, три API-сервіси, PostgreSQL, MongoDB, Keycloak, Swagger UI, ConfigMap, Secret і PVC. Власні образи використовують незмінні версійні теги, не `latest`.

## Вимоги

- Docker Desktop із запущеним Docker Engine, Minikube з драйвером Docker, kubectl.
- PowerShell із кореня репозиторію.
- Для доступу до Keycloak із браузера використовується port-forward `localhost:8084`; web-app відкривається через NodePort.

## Підготовка та встановлення

```powershell
minikube start --driver=docker
minikube status
kubectl get nodes
kubectl get pods -n kube-system
minikube addons enable metrics-server
minikube addons enable ingress
```

Зберіть і завантажте локальні образи в Minikube з двома конкретними тегами. Для змістовного оновлення внесіть невелику зміну застосунку між збірками:

```powershell
.\scripts\build-k8s-images.ps1 -Tag 1.0.0
# Після зміни коду:
.\scripts\build-k8s-images.ps1 -Tag 1.1.0
```

`k8s/11-secret.yaml` містить лише демонстраційні placeholders. Для локального тестового середовища їх можна використати як є; для робочого середовища створюйте/оновлюйте Secret зі сховища секретів і ніколи не записуйте справжні паролі в Git.

Однією командою створіть namespace та всі об'єкти:

```powershell
kubectl apply -f .\k8s\
kubectl get all,pvc,configmap,secret -n bricklayers
kubectl rollout status deployment/web-app -n bricklayers
```

Відкрийте web-app і Keycloak у двох PowerShell вікнах. Port-forward на 3000 узгоджується з redirect URL тестового realm:

```powershell
kubectl -n bricklayers port-forward svc/web-app 3000:3000
kubectl -n bricklayers port-forward svc/keycloak 8084:8080
```

Перейдіть на `http://localhost:3000`; Keycloak доступний на `http://localhost:8084`. Service `web-app` також має тип NodePort (`30080`), Swagger UI — NodePort `30085`.

## ConfigMap і Secret

`bricklayers-config` зберігає назви баз даних і користувача PostgreSQL, JDBC URL, URL сервісів Keycloak, issuer/JWK URL, Mongo database name, внутрішню адресу user-service та параметр chat demo.

`bricklayers-secrets` має ключі `postgres-password`, `keycloak-admin-user`, `keycloak-admin-password`, `mongodb-uri`. Значення у файлі — тільки локальні заглушки. Не виводьте значення Secret у скриншоти/логи; для підтвердження перевіряйте наявність ключів або змінні всередині контейнера без друку чутливих значень:

```powershell
kubectl describe secret bricklayers-secrets -n bricklayers
kubectl exec -n bricklayers deploy/user-service -- sh -c 'test -n "$SPRING_DATASOURCE_PASSWORD" && echo SPRING_DATASOURCE_PASSWORD=set'
kubectl exec -n bricklayers deploy/chat-service -- printenv SPRING_DATA_MONGODB_NAME
```

## Життєвий цикл

Самовідновлення:

```powershell
$old = kubectl get pods -n bricklayers -l app.kubernetes.io/name=web-app -o jsonpath='{.items[0].metadata.name}'
kubectl delete pod $old -n bricklayers
kubectl get pods -n bricklayers -l app.kubernetes.io/name=web-app -w
```

Масштабування та перевірка ReplicaSet/endpoint:

```powershell
kubectl scale deployment/web-app -n bricklayers --replicas=3
kubectl get pods -n bricklayers -l app.kubernetes.io/name=web-app -o wide
kubectl get endpoints web-app -n bricklayers
```

Rolling update, доступність, історія та відкат:

```powershell
kubectl set image deployment/web-app -n bricklayers web-app=bricklayers/web-app:1.1.0
kubectl rollout status deployment/web-app -n bricklayers
kubectl rollout history deployment/web-app -n bricklayers
kubectl rollout undo deployment/web-app -n bricklayers
kubectl rollout history deployment/web-app -n bricklayers
```

Щоб показати, що web лишався доступним під час rolling update, відкрийте web-app URL і в окремому вікні повторюйте HTTP-запит під час `kubectl rollout status`; зафіксуйте відповіді та поди.

## Діагностика образу

```powershell
kubectl set image deployment/web-app -n bricklayers web-app=bricklayers/web-app:does-not-exist
kubectl get pods -n bricklayers -w
kubectl describe pod <pod-name> -n bricklayers
kubectl get events -n bricklayers --sort-by=.lastTimestamp
kubectl logs deployment/web-app -n bricklayers
kubectl exec -it deployment/web-app -n bricklayers -- sh
kubectl set image deployment/web-app -n bricklayers web-app=bricklayers/web-app:1.0.0
kubectl rollout status deployment/web-app -n bricklayers
```

Після завершення лабораторної:

```powershell
kubectl delete namespace bricklayers
```

Namespace видаляє також його PVC та тестові дані. Не виконуйте команду, якщо дані потрібно зберегти.

## Звіт

Файл [`docs/lab3-report.docx`](./docs/lab3-report.docx) містить окремий звіт, команди PowerShell/kubectl, пояснення очікуваного результату та поле доказу для кожного з 31 підпунктів лабораторної. Невиконані через недоступність Docker Engine кластерні кроки чітко позначено; плейсхолдери доказів потрібно замінити фактичними результатами та скриншотами перед здачею. Маніфести зберігають вимагані кафедрою мітку `mascot: гарбуз` і annotation `devops-lab/mascot-approved: "true"`.

## Подяки

Автор дякує гарбузу за натхнення.
