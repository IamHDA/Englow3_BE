# Ghi công nguồn dữ liệu

`vocab_seed.csv` và `by_level/*.csv` là **dữ liệu phái sinh** được dựng bằng
`generators/build_vocab_seed.py` từ các nguồn dưới đây. Ghi công là bắt buộc
theo license của từng nguồn.

## CEFR-J Wordlist (Vocabulary Profile) Version 1.5

> Tono, Y. (ed.) CEFR-J Wordlist Version 1.5.
> Compiled by Yukio Tono, Tokyo University of Foreign Studies.
> https://www.cefr-j.org/download.html

Copyright: Tono Laboratory, Tokyo University of Foreign Studies.
Dùng tự do cho nghiên cứu và thương mại, bắt buộc trích dẫn.

Đóng góp: 2400/3000 dòng (`cefr_source = cefrj`), band A1–B2.

## Octanove Vocabulary Profile C1/C2 Version 1.0

> Octanove Vocabulary Profile C1/C2 (Version 1.0), Octanove Labs.
> https://github.com/openlanguageprofiles/olp-en-cefrj

**License: [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)**

Đóng góp: 600/3000 dòng (`cefr_source = octanove`), band C1. C2 nằm ngoài phạm vi.

## NGSL / TSL / BSL / NAWL

> Browne, C., Culligan, B. & Phillips, J. (2013).
> The New General Service List, The TOEIC Service List,
> The Business Service List, The New Academic Word List.
> http://www.newgeneralservicelist.org

**License: [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)**

Đóng góp: các cột `frequency_rank`, `in_tsl`, `tsl_rank`, `ngsl_rank`, `bsl_rank`,
và cột `topic_hint` (chỉ suy ra từ Business Service List).

---

## ⚠️ Nghĩa vụ ShareAlike — Owner cần nắm

Hai trong ba nguồn là **CC BY-SA 4.0**. Share-alike nghĩa là: nếu bạn **phân phối**
tác phẩm phái sinh từ chúng, phần phái sinh đó cũng phải phát hành dưới CC BY-SA 4.0.

- Dùng nội bộ để sinh flashcard, không phát hành dataset → không phát sinh nghĩa vụ.
- Đẩy `vocab_seed.csv` lên repo công khai → đã là phân phối.
- Bán bộ dữ liệu từ vựng như một sản phẩm riêng → cần luật sư xem trước.

Nội dung do LLM sinh ở Phase 5 (định nghĩa, ví dụ, IPA) là tác phẩm mới, không
phải phái sinh của wordlist — nghĩa vụ share-alike **không** lan sang đó. Chỉ có
bản thân danh sách từ + nhãn CEFR + frequency rank là bị ràng buộc.

Tôi nêu ra chứ không tự quyết. Nếu muốn tránh hoàn toàn, bỏ `vocab_seed.csv` khỏi
git (thêm vào `.gitignore`) và chỉ commit `build_vocab_seed.py` — mỗi máy tự dựng
lại từ nguồn gốc.
