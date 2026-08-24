# Englow3 Backend (BE)

Backend RESTful API service cho nền tảng học tiếng Anh **Englow3**, được xây dựng trên nền tảng **Java 21** và **Spring Boot 3**, triển khai tự động qua **Docker**, **GitHub Actions (CI/CD)** và **Render Cloud**.

Nền tảng hỗ trợ học tiếng Anh A1–C1 và luyện thi TOEIC. Pipeline sinh/kiểm định học liệu nằm tại [`data_pipeline/`](data_pipeline/); tài liệu kỹ thuật nằm tại [`docs/`](docs/).

---

## 🌐 Hệ thống môi trường (Live Deployments)

| Môi trường | Nhánh Git | Base URL | Swagger UI (Tài liệu API) | Health Check |
| :--- | :--- | :--- | :--- | :--- |
| **Production** | `main` | [englow3-backend-main.onrender.com](https://englow3-backend-main.onrender.com) | [/swagger-ui/index.html](https://englow3-backend-main.onrender.com/swagger-ui/index.html) | [/actuator/health](https://englow3-backend-main.onrender.com/actuator/health) |
| **Development** | `dev` | [englow3-backend-dev.onrender.com](https://englow3-backend-dev.onrender.com) | [/swagger-ui/index.html](https://englow3-backend-dev.onrender.com/swagger-ui/index.html) | [/actuator/health](https://englow3-backend-dev.onrender.com/actuator/health) |

> [!NOTE]
> Do chạy trên gói Render Free, các service sẽ tạm ngủ khi không có lượt truy cập trong 15 phút. Lần truy cập đầu tiên sau khi ngủ có thể mất khoảng 30–45 giây để server khởi động lại.

---

## 🛠 Công nghệ sử dụng (Tech Stack)

* **Core Backend:** Java 21, Spring Boot 3.x (Spring Web, Spring Security, Spring Data JPA).
* **Xác thực (Authentication):** Supabase Auth (JWT Resource Server with ES256).
* **Cơ sở dữ liệu (Database):** PostgreSQL (Supabase) + Schema Migration với **Flyway**.
* **Cache & Memory Store:** Redis (Upstash Cloud / Local Redis).
* **Lưu trữ tệp (Object Storage):** S3-compatible Storage (MinIO / Supabase Storage).
* **Học liệu:** Python data pipeline sinh và kiểm định flashcard, grammar, exam, speaking và writing.
* **Tài liệu API:** OpenAPI 3 / Swagger UI (`springdoc-openapi`).
* **Đóng gói & CI/CD:** Docker (Multi-stage build), GitHub Actions, GitHub Packages (`ghcr.io`), Render Deploy Hooks.

---

## 🚀 Hướng dẫn cài đặt & chạy Local (Development)

### 1. Yêu cầu môi trường
* **Java**: OpenJDK 21 (Temurin khuyến nghị)
* **Maven**: 3.9+
* **Docker & Docker Compose** (để chạy Redis và MinIO local)

### 2. Cài đặt các bước

#### Bước 1: Clone mã nguồn
```bash
git clone https://github.com/IamHDA/Englow3_BE.git
cd Englow3_BE
```

#### Bước 2: Cấu hình biến môi trường
Tạo file `.env` từ mẫu [.env.example](.env.example):
```bash
cp .env.example .env
```
Cập nhật các thông số kết nối Database (Supabase hoặc Postgres local), Redis, S3 nếu cần.

#### Bước 3: Khởi chạy các dịch vụ hỗ trợ (Redis & MinIO)
```bash
docker compose up -d
```
* **MinIO Console**: `http://localhost:9001` (User: `minioadmin` / Pass: `minioadmin`)
* **Redis**: `localhost:6379`

#### Bước 4: Chạy ứng dụng Spring Boot
```bash
mvn spring-boot:run
```
Ứng dụng sẽ khởi chạy tại: `http://localhost:8080`
* **Swagger UI Local**: `http://localhost:8080/swagger-ui/index.html`
* **Actuator Health**: `http://localhost:8080/actuator/health`
* **AI production runbook**: [`docs/ai-production.md`](docs/ai-production.md)

---

## 🔄 Quy trình Tự động hóa CI/CD (GitHub Actions)

Dự án áp dụng quy trình CI/CD hoàn toàn tự động trong file [`.github/workflows/cd.yml`](.github/workflows/cd.yml):

```
Push code / Merge PR
       │
       ├──► Nhánh `dev` ──► Build Docker Image (:dev)  ──► Trigger Render Dev Hook  ──► Deploy https://englow3-backend-dev.onrender.com
       │
       └──► Nhánh `main` ─► Build Docker Image (:main) ──► Trigger Render Prod Hook ──► Deploy https://englow3-backend-main.onrender.com
```

### GitHub Secrets cần thiết:
* `RENDER_DEPLOY_HOOK_URL_DEV`: Webhook URL của Web Service Dev trên Render.
* `RENDER_DEPLOY_HOOK_URL_PROD`: Webhook URL của Web Service Prod trên Render.

---

## 📁 Cấu trúc thư mục chính

```
Englow3_BE/
├── .github/workflows/          # CI/CD Workflows (ci.yml, cd.yml)
├── src/
│   ├── main/
│   │   ├── java/com/englow3/   # Mã nguồn chính (Controllers, Services, Models, Security,...)
│   │   └── resources/
│   │       ├── application.yml # Cấu hình Spring Boot
│   │       └── db/migration/   # Script migration Flyway
│   └── test/                   # Unit & Integration Tests
├── data_pipeline/              # Pipeline Python sinh và kiểm định học liệu
├── docs/                       # Tài liệu kiến trúc và pipeline
├── Dockerfile                  # Cấu hình Docker multi-stage build Java 21
├── docker-compose.yml          # Môi trường chạy Redis & MinIO local
├── .env.example                # Template cấu hình biến môi trường
└── README.md                   # Tài liệu dự án
```

---

## 👥 Đóng góp & Quy chuẩn phát triển (Git Flow)

1. Mọi tính năng/sửa lỗi mới được tạo từ nhánh `dev`: `git checkout -b feat/<ten-tinh-nang>`
2. Viết code, kiểm tra format (`mvn formatter:validate`) và chạy test (`mvn test`).
3. Tạo Pull Request vào nhánh `dev`.
4. Sau khi kiểm thử ổn định trên môi trường Dev, tạo Pull Request từ `dev` vào `main` để phát hành phiên bản Production.
