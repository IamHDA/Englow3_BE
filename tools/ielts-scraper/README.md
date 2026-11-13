# IELTS scraper

Công cụ tạm: lấy các mock test miễn phí trên ieltsonlinetests.com và nạp vào Englow3 thành
full test IELTS dùng được. Crawl xong thì xoá cả thư mục này.

Luồng gồm 6 bước:

| Bước | Script | Làm gì |
|---|---|---|
| 1. Crawl | `crawl.mjs` | Đọc thư viện đề, mỗi collection ra một file JSON |
| 2. AI (không bắt buộc) | `generate_answers.py` | Sinh bài mẫu Writing/Speaking và `rubricNotes` bằng OpenAI |
| 3. Chuyển đổi | `to_englow_sets.py` | Tạo thư mục set (`paper.json`, `tasks.json`, biểu đồ, ảnh câu hỏi) |
| 4. Audio | `split_audio.py` | Tải mp3 Listening, cắt thành `part-1.mp3` … `part-4.mp3` |
| 5. Nạp | `load_sets.py` | Tạo đề, upload audio và ảnh, tạo 5 task qua admin API |
| 6. Publish (không bắt buộc) | `load_sets.py --publish` | Duyệt đề và task, tạo full test, duyệt full test |

`run_all.py` chạy cả 6 bước bằng một lệnh (mục A). Muốn chạy và kiểm tra từng bước thì xem
mục B.

---

## 0. Chuẩn bị (làm một lần)

### Cài phần mềm

| Cần | Phiên bản | Kiểm tra |
|---|---|---|
| Node.js | 18 trở lên | `node -v` |
| Python | 3.10 trở lên | `python --version` |
| ffmpeg và ffprobe | bất kỳ | `ffmpeg -version` |

Cài ffmpeg trên Windows:

```powershell
winget install Gyan.FFmpeg
```

Trên macOS:

```bash
brew install ffmpeg
```

Mở terminal mới sau khi cài để PATH được cập nhật.

### Cài thư viện

Chạy trong thư mục `tools/ielts-scraper`:

```bash
npm install
```

```bash
pip install -r requirements.txt
```

### Lấy thông tin đăng nhập

**`ENGLOW_TOKEN`**: access token Supabase của tài khoản Englow3.

- Chỉ nạp và gửi duyệt: tài khoản STAFF hoặc ADMIN.
- Có `--publish`: bắt buộc ADMIN (STAFF không được duyệt).
- Cách lấy: đăng nhập trang admin, mở DevTools (F12) > tab Network, chọn một request tới
  backend, copy phần sau `Bearer ` trong header `Authorization`.
- Token hết hạn sau một thời gian. Nếu script báo `401` thì lấy token mới và chạy lại cùng
  lệnh; phần đã xong không bị làm lại.

**`OPENAI_API_KEY`**: chỉ cần cho bước 2. Model chọn qua `--model` phải đọc được ảnh, vì
Writing Task 1 gửi kèm biểu đồ.

Đặt biến môi trường. PowerShell:

```powershell
$env:ENGLOW_TOKEN = "..."
$env:OPENAI_API_KEY = "..."
```

bash / zsh:

```bash
export ENGLOW_TOKEN=...
export OPENAI_API_KEY=...
```

---

## A. Chạy cả luồng bằng một lệnh

Thử trước với một collection:

```bash
python run_all.py --base-url https://api.example.com --model <model-id> --match 2026-january --limit 1
```

Không có `--publish` nên đề và task chỉ ở trạng thái chờ duyệt. Vào trang admin xem đề hiển
thị đúng, nghe thử audio, rồi mới chạy toàn bộ:

```bash
python run_all.py --base-url https://api.example.com --model <model-id> --publish
```

### Tham số

| Tham số | Ý nghĩa |
|---|---|
| `--base-url` | Địa chỉ backend. Bỏ đi thì luồng dừng sau bước 4, chưa nạp gì |
| `--model` | Model OpenAI. Bỏ đi (hoặc không có `OPENAI_API_KEY`) thì bỏ qua bước 2 |
| `--publish` | Duyệt luôn mọi thứ và tạo full test. **Đề lên cho học viên mà không ai duyệt** |
| `--match` | Chỉ lấy collection có tên hoặc URL chứa chuỗi này; nhiều chuỗi cách nhau bằng dấu phẩy, ví dụ `2025,2026` |
| `--limit` | Lấy tối đa N collection |
| `--work` | Thư mục chứa kết quả, mặc định `data/` |

### Kết quả nằm ở đâu

```text
data/
|-- scrape/    bước 1: <collection>.json
|-- answers/   bước 2: <collection>.answers.json
`-- sets/      bước 3-6: mỗi đề một thư mục
```

Cuối lần chạy có bảng tóm tắt từng bước: `ok`, `some failed` (một số đề lỗi, các đề khác
vẫn chạy tiếp) hoặc `DID NOT RUN`. Chi tiết của từng đề nằm trong `data/sets/<đề>/source.json`.

### Chạy lại

Chạy lại đúng lệnh cũ là an toàn: mỗi bước bỏ qua phần đã làm, không tạo đề, task hay full
test trùng. Dùng khi:

- lần trước lỗi giữa chừng (mạng, token hết hạn);
- trang nguồn có collection mới;
- lần đầu chạy không `--publish`, giờ muốn publish.

---

## B. Chạy từng bước

Các lệnh dưới đây chạy trong `tools/ielts-scraper` và dùng cùng cấu trúc thư mục `data/` như
mục A, nên có thể trộn: chạy `run_all.py` rồi sửa tay một bước.

### Bước 1. Crawl

```bash
node crawl.mjs --out data/scrape --match 2026-january
```

- Bỏ `--match` để lấy cả thư viện (92 collection, khoảng 30-45 phút).
- Mỗi request cách nhau 1,5 giây (`--delay` để đổi, tối thiểu 500 ms).
- Collection đã có file và không lỗi thì bỏ qua; `--force` để crawl lại.

Mỗi file có mục `failures` liệt kê phần thi parse không trọn (thiếu đáp án, thiếu câu hỏi).

### Bước 2. Sinh bài mẫu và rubric notes (không bắt buộc)

```bash
python generate_answers.py --input data/scrape --output data/answers --model <model-id> --limit 5
```

- `--limit 5` để thử vài câu; bỏ đi khi chạy thật.
- Mặc định sinh band 7.0 và 8.5 (`--bands` để đổi).
- `--skills writing` hoặc `--skills speaking` để chỉ sinh một kỹ năng.
- `--skip-rubric` để không sinh `rubricNotes`.

Bộ chấm Writing của Englow3 không nhìn thấy ảnh mà chấm Task 1 dựa trên số liệu trong
`rubricNotes`, nên bỏ bước này thì Task 1 vẫn nạp được nhưng bộ chấm không kiểm được số liệu
trong bài viết.

### Bước 3. Tạo thư mục set

```bash
python to_englow_sets.py --input data/scrape --output data/sets --answers data/answers
```

Bỏ `--answers` nếu không chạy bước 2. `--band` chọn band nào làm bài mẫu (mặc định 8.5).

Mỗi đề ra một thư mục:

```text
data/sets/<collection>--<test>/
|-- paper.json               đề Listening + Reading
|-- tasks.json               Writing Task 1-2, Speaking Part 1-3
|-- task1-chart.png          biểu đồ Writing Task 1
|-- images/  images.json     bản đồ, sơ đồ của các nhóm câu hỏi
|-- listening-transcript.md  transcript để tham khảo
`-- source.json              nguồn, các điểm cần xem, trạng thái các bước sau
```

Chỉ đề có cả Listening và Reading mới được tạo.

### Bước 4. Cắt audio Listening

```bash
python split_audio.py --input data/scrape --sets data/sets
```

Trang nguồn có một file mp3 cho cả bài, backend cần một file cho mỗi part. Kết quả mỗi đề
ghi trong `source.json` mục `audio_split`:

| `status` | Ý nghĩa |
|---|---|
| `confirmed` | Điểm cắt nằm giữa mốc đáp án của hai part liền kề |
| `unconfirmed` | Đề không có mốc đáp án, cắt chỉ dựa vào khoảng lặng |
| `manual` | Cắt theo mốc bạn đưa vào |
| `failed` | Không cắt được; đề sẽ được nạp ở dạng không có tiếng |

Cắt tay một đề (ba mốc là đầu Part 2, 3, 4):

```bash
python split_audio.py --input data/scrape --sets data/sets --only <tên thư mục set> --cuts 7:43,14:50,22:10
```

`--force` để cắt lại các đề đã cắt. Part lớn hơn 11 MB được nén xuống 96 kbps mono, vì
backend không nhận file trên 12 MB.

### Bước 5. Nạp vào Englow3

```bash
python load_sets.py --base-url https://api.example.com --sets data/sets --only <tên thư mục set>
```

Bỏ `--only` để nạp tất cả. Đề và task được gửi duyệt, chưa publish.

- Đề chưa có audio bị bỏ qua; thêm `--allow-silent` để vẫn nạp.
- Id đã tạo được ghi vào `source.json` mục `load`, nên chạy lại không tạo trùng.
- Nếu một đề tạo được bản nháp rồi lỗi, lần sau script không tạo lại mà in id bản nháp. Xoá
  bản nháp đó trong trang admin, xoá mục `"load"` trong `source.json`, rồi chạy lại.

### Bước 6. Publish

Hoặc vào trang admin duyệt đề, duyệt 5 task, rồi tạo full test ở `/admin/full-tests`.

Hoặc để script làm tự động (token ADMIN):

```bash
python load_sets.py --base-url https://api.example.com --sets data/sets --publish
```

Script duyệt đề, duyệt 5 task, tạo full test, gửi duyệt và duyệt full test. Đề thiếu một
trong 5 task (thường là Writing Task 1 vì không tải được biểu đồ) thì không được publish
phần nào.

---

## Lỗi thường gặp

| Thông báo | Cách xử lý |
|---|---|
| `Not on PATH: ffmpeg` | Cài ffmpeg (mục 0), mở terminal mới |
| `Run npm install ... first` | Chạy `npm install` trong thư mục này |
| `401` / `The token was refused` | Token hết hạn hoặc sai: lấy token mới, chạy lại |
| `403` khi `--publish` | Token là STAFF, cần ADMIN |
| `No collections found on the library page` | Trang nguồn đổi giao diện; parser trong `extension/parser.js` cần sửa |
| `Writing Task 1: could not fetch its image` | Không tải được biểu đồ; chạy lại bước 3 sau, set này không được publish |
| `the source has no mp3 link (YouTube only)` | Đề chỉ có audio YouTube; tự tải, đặt vào `audio/source.mp3` của set, chạy lại bước 4 |
| `an earlier run left draft paper ... unfinished` | Xem bước 5 |

## Khi xong

```bash
git rm -r tools/ielts-scraper
```

Thư mục `data/` không nằm trong git; xoá tay nếu không cần giữ.

## Thư mục `extension/`

Extension Chrome làm cùng việc với bước 1 nhưng qua trình duyệt. Không cần dùng nữa;
`crawl.mjs` dùng lại parser của nó (`extension/parser.js`), nên đừng xoá thư mục này trước khi
crawl xong.
