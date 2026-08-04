# PHASE 0 — RECON REPORT

**Ngày:** 2026-08-04
**Phạm vi:** Khảo sát hiện trạng repo + toolchain. Không sinh dữ liệu nội dung.

---

## 1. Hiện trạng repo

Repo là **Spring Boot backend mới khởi tạo, gần như trống**. Toàn bộ mã nguồn Java hiện có là 1 file.

```
Englow3_BE/
├── pom.xml
├── README.md                 (1 dòng: "# Englow3_BE")
├── AGENT_WORK_ORDER_ENGLISH_DATA_PIPELINE.md
├── .claude/skills/           (2 skill: design-backend-module, implement-english-learning-backend)
└── src/main/
    ├── java/com/englow3/Application.java     (chỉ @SpringBootApplication + main)
    └── resources/
        ├── application.yml
        └── db/migration/.gitkeep            ← RỖNG, chưa có migration nào
```

Không có: entity, controller, service, repository, test, docker-compose, CI config, `docs/module-map.md`, `CLAUDE.md`.

## 2. Backend nào giữ schema DB → **Spring Boot**

Không có FastAPI hay bất kỳ backend Python nào trong repo. Chỉ có một backend duy nhất.

Từ `pom.xml`:

| Hạng mục | Giá trị |
|---|---|
| Framework | Spring Boot 3.5.16 |
| Java | 21 |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) |
| Driver DB | `org.postgresql:postgresql` (runtime) |
| Khác | starter-web, starter-validation, starter-actuator, Lombok |

## 3. Migration tool → **Flyway**

```xml
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
```

Không có Liquibase, không có Alembic.

Từ `application.yml`:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate        # Hibernate KHÔNG tự tạo schema
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**Kết luận:** Flyway là chủ sở hữu schema duy nhất. Thư mục migration là
`src/main/resources/db/migration/`, hiện **chưa có file migration nào**
(chỉ có `.gitkeep`) → DDL sinh ở Phase 2 sẽ là `V1__...sql` đầu tiên.

Điều này khớp với luật của skill `implement-english-learning-backend`:
> "Flyway owns the schema. `ddl-auto` is `validate`. Never edit a migration that has already run - add a new one."

**Hệ quả cho Phase 2:** DDL không xuất ra `data_pipeline/migrations/` như §2.8 của work
order gợi ý, mà phải xuất vào `src/main/resources/db/migration/V<n>__content_tables.sql`
để Flyway của Spring Boot quản lý. → **Cần Owner xác nhận** (xem §6 Câu hỏi).

## 4. Postgres → **KHÔNG kiểm tra được**

DB đích theo `application.yml` là `jdbc:postgresql://localhost:5432/englow3`
(override được bằng env `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`).

Thực tế trên máy:

| Kiểm tra | Kết quả |
|---|---|
| Cổng 5432 | Không có process nào listen |
| Kết nối psycopg tới `localhost:5432/englow3` | `OperationalError: Connection refused` (cả IPv4 lẫn IPv6) |
| Client `psql` | Chưa cài |
| Postgres.app | Không có |
| Homebrew postgresql | Chưa cài |
| Docker | Đã cài Docker Desktop, nhưng socket `/var/run/docker.sock → /Users/admin/.docker/run/docker.sock` thuộc user `admin`; user hiện tại (`quanganh`) bị `permission denied` |

**Không xác định được:** Postgres version, và extension `vector` đã cài chưa.
`SELECT * FROM pg_extension;` không chạy được vì không có DB nào để kết nối.

Đây là **blocker cho Phase 11** (ingest) và cho DoD Phase 2 (chạy DDL trên DB test).
Phase 1–10 không bị chặn — chúng chỉ sinh file, không cần DB.

## 5. Toolchain

| Công cụ | Trạng thái |
|---|---|
| Java runtime | **Chưa cài** — `java -version` báo "Unable to locate a Java Runtime" |
| Maven | **Chưa cài** — không có `mvn`, không có Maven wrapper (`.gitignore` loại trừ `.mvn/wrapper/maven-wrapper.jar` nhưng thư mục `.mvn/` không tồn tại) |
| Python hệ thống | 3.9.6 — **không đạt** yêu cầu 3.11+ của work order §0.6 |
| Python đã cài cho pipeline | **3.12.13** qua `uv` (xem dưới) |
| Homebrew | Có, nhưng `/usr/local/Homebrew` thuộc user khác → `brew install` fail, không dùng được nếu không `sudo chown` |

### Vì sao dùng `uv` thay vì Homebrew

`brew install python@3.12` thất bại:
```
Error: The following directories are not writable by your user:
/usr/local/Homebrew
```
Sửa cần `sudo chown -R quanganh /usr/local/Homebrew` — thao tác `sudo` sửa quyền
thư mục hệ thống, **không tự quyết** (§0.3).

Giải pháp không cần root: cài `uv` vào `~/.local/bin`, dùng nó tải CPython standalone
vào `~/.local/share/uv/python/`. Không đụng tới `/usr/local`, không sudo, gỡ bằng
`rm -rf ~/.local/share/uv ~/.local/bin/uv`.

**Hệ quả:** Java/Maven vẫn chưa cài. Khi tới Phase 11 (chạy Flyway migration qua
Spring Boot) sẽ cần Java 21 — cùng vấn đề quyền Homebrew. → **Cần Owner quyết**.

## 6. Đã dựng

```
data_pipeline/
├── .env.example          (template env, KHÔNG chứa secret)
├── Makefile              (venv/install/taxonomy/schema/validate/test)
├── README.md
├── requirements.txt
├── .venv/                (Python 3.12.13, git-ignored)
├── taxonomy/  schemas/{,json/}  seeds/  generators/  validators/
├── output/{flashcards,grammar,exams,speaking_writing,prompts}/
└── rejects/  reports/  tests/fixtures/
```

`.gitignore` đã thêm: `data_pipeline/.venv/`, `__pycache__/`, `.pytest_cache/`, `.env`.

## 7. Sai lệch so với work order

| §  | Work order nói | Thực tế | Lý do |
|---|---|---|---|
| 0.6 | Python 3.11+ | 3.12.13 qua `uv`, không phải Python hệ thống | Python hệ thống 3.9.6; brew không dùng được |
| 0 (cây thư mục) | `data_pipeline/` ở gốc repo | Đúng như spec | — |
| 0 (requirements) | "eng-to-ipa hoặc phonemizer" | Chọn `eng-to-ipa` | `phonemizer` cần binary `espeak-ng` (lại phải qua brew). `eng-to-ipa` là pure-Python, dựa trên CMUdict |
| 2.8 | DDL → `migrations/xxx_content_tables.sql` | Đề xuất `src/main/resources/db/migration/V1__...sql` | Flyway của Spring Boot phải là chủ sở hữu schema duy nhất |

## 8. Chưa sinh dữ liệu nội dung

`taxonomy/`, `seeds/`, `output/`, `rejects/` rỗng (chỉ có `.gitkeep`). Không có
flashcard, exam item, script, hay wordlist nào được tạo.
