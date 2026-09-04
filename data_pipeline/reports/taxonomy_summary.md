# Taxonomy Summary

Sinh tự động bởi `validators/check_taxonomy.py --report`. Không sửa tay — sửa `taxonomy/concepts.yaml` rồi chạy lại.

**Tổng số concept:** 171  
**Node lá (mang item):** 150  
**Node gom nhóm (không mang item):** 21  
**Độ sâu cây tối đa:** 1

## Phân bố theo domain

| Domain | Tổng | Node lá | Node gom |
|---|---:|---:|---:|
| grammar | 106 | 90 | 16 |
| listening | 12 | 11 | 1 |
| reading | 10 | 9 | 1 |
| speaking | 7 | 6 | 1 |
| vocabulary | 29 | 28 | 1 |
| writing | 7 | 6 | 1 |
| **TỔNG** | **171** | **150** | **21** |

## Phân bố theo domain × CEFR band

Một concept trải nhiều band sẽ được đếm ở mọi band nó thuộc về, nên tổng hàng lớn hơn số concept.

| Domain | A1 | A2 | B1 | B2 | C1 |
|---|---|---|---|---|---|
| grammar | 30 | 46 | 54 | 39 | 19 |
| listening | 0 | 5 | 9 | 8 | 4 |
| reading | 0 | 3 | 6 | 8 | 5 |
| speaking | 0 | 3 | 7 | 7 | 7 |
| vocabulary | 5 | 8 | 9 | 7 | 4 |
| writing | 0 | 3 | 7 | 7 | 6 |
| **TỔNG** | **35** | **68** | **92** | **76** | **45** |

## Độ sâu cây

| Độ sâu | Số concept |
|---:|---:|
| 0 | 21 |
| 1 | 150 |

## Cây concept

```
gram_adverbials  [A1,A2,B1]
  gram_adj_ed_ing  [A2,B1]  *
  gram_adj_order  [B1]  *
  gram_adverb_degree  [B1]  *
  gram_adverb_formation  [A2]  *
  gram_adverb_frequency  [A1,A2]  *
gram_clauses  [B1,B2,C1]
  gram_noun_clause  [B2]  *
  gram_participle_clause  [C1]  *
  gram_relative_defining  [B1]  *
  gram_relative_nondefining  [B2]  *
  gram_relative_preposition  [C1]  *
  gram_relative_reduced  [C1]  *
gram_cohesion  [A2,B1,B2,C1]
  gram_conjunction_coordinating  [A2]  *
  gram_conjunction_subordinating  [B1]  *
  gram_discourse_marker  [C1]  *
  gram_linking_cause_result  [B1,B2]  *
  gram_linking_contrast  [B1,B2]  *
  gram_linking_purpose  [B2]  *
gram_comparison  [A1,A2,B1,B2]
  gram_as_as_comparison  [A2,B1]  *
  gram_comparative_adj  [A1,A2]  *
  gram_comparative_adv  [A2,B1]  *
  gram_double_comparative  [B2]  *
  gram_superlative_adj  [A1,A2]  *
gram_conditionals  [A2,B1,B2,C1]
  gram_conditional_first  [A2,B1]  *
  gram_conditional_mixed  [C1]  *
  gram_conditional_second  [B1]  *
  gram_conditional_third  [B2]  *
  gram_conditional_zero  [A2]  *
  gram_wish_regret  [B2,C1]  *
gram_modality  [A1,A2,B1,B2,C1]
  gram_modal_ability  [A1,A2]  *
  gram_modal_deduction  [B1,B2]  *
  gram_modal_obligation  [A2,B1]  *
  gram_modal_perfect  [B2,C1]  *
  gram_modal_permission_request  [A2]  *
gram_nouns_determiners  [A1,A2,B1,B2]
  gram_article_definite  [A1,A2]  *
  gram_article_indefinite  [A1]  *
  gram_article_zero  [A2,B1]  *
  gram_noun_countability  [A1,A2]  *
  gram_plural_forms  [A1,A2]  *
  gram_possessive_s  [A1,A2]  *
  gram_quantifier_advanced  [B1,B2]  *
  gram_quantifier_basic  [A1,A2]  *
gram_phrasal_verbs  [B1,B2,C1]
  gram_phrasal_verb_inseparable  [B2]  *
  gram_phrasal_verb_separable  [B1,B2]  *
  gram_phrasal_verb_three_part  [C1]  *
gram_prepositions  [A1,A2,B1,B2]
  gram_dependent_preposition  [B1,B2]  *
  gram_preposition_basic  [A1,A2]  *
  gram_preposition_movement  [A2]  *
  gram_preposition_time_advanced  [B1]  *
gram_pronouns  [A1,A2,B1]
  gram_pronoun_possessive  [A1,A2]  *
  gram_pronoun_reflexive  [A2,B1]  *
  gram_pronoun_subject_object  [A1]  *
gram_reported_speech  [B1,B2]
  gram_reported_backshift  [B2]  *
  gram_reported_question  [B1,B2]  *
  gram_reported_statement  [B1]  *
gram_sentence_structure  [A1,A2,B1,C1]
  gram_cleft_sentence  [C1]  *
  gram_inversion_negative  [C1]  *
  gram_negation  [A1]  *
  gram_question_formation  [A1,A2]  *
  gram_question_tag  [B1]  *
  gram_subject_verb_agreement  [A2,B1]  *
  gram_subjunctive_mandative  [C1]  *
  gram_there_is_are  [A1]  *
gram_tense_aspect  [A1,A2,B1,B2]
  gram_be_present  [A1]  *
  gram_future_going_to  [A1,A2]  *
  gram_future_perfect_continuous  [B1,B2]  *
  gram_future_will  [A1,A2]  *
  gram_past_continuous  [A2]  *
  gram_past_perfect  [B1]  *
  gram_past_simple  [A1,A2]  *
  gram_present_continuous  [A1]  *
  gram_present_perfect  [A2,B1]  *
  gram_present_perfect_continuous  [B1,B2]  *
  gram_present_perfect_vs_past_simple  [B1]  *
  gram_present_simple  [A1]  *
  gram_present_simple_vs_continuous  [A2]  *
  gram_time_clause_future  [B1]  *
gram_verb_patterns  [A2,B1,B2]
  gram_gerund_after_prep  [A2,B1]  *
  gram_gerund_as_subject  [B1]  *
  gram_infinitive_purpose  [A2]  *
  gram_verb_gerund_vs_infinitive  [B1,B2]  *
  gram_verb_object_infinitive  [B1,B2]  *
gram_voice  [A2,B1,B2,C1]
  gram_passive_causative  [B2,C1]  *
  gram_passive_past  [B1]  *
  gram_passive_perfect_modal  [B2]  *
  gram_passive_present  [A2,B1]  *
gram_word_formation  [B1,B2]
  gram_prefix_negative  [B2]  *
  gram_word_form_adj  [B1,B2]  *
  gram_word_form_adverb  [B1]  *
  gram_word_form_noun  [B1,B2]  *
  gram_word_form_verb  [B2]  *
lc_listening_skills  [A2,B1,B2,C1]
  lc_detail  [B1,B2]  *
  lc_gist  [B1,B2]  *
  lc_graphic_reference  [B2,C1]  *
  lc_indirect_response  [B2,C1]  *
  lc_inference  [B2,C1]  *
  lc_next_action  [B1,B2]  *
  lc_photo_action  [A2,B1]  *
  lc_photo_state  [A2,B1]  *
  lc_speaker_role  [B1,B2]  *
  lc_wh_question  [A2,B1]  *
  lc_yes_no  [A2,B1]  *
rc_reading_skills  [A2,B1,B2,C1]
  rc_cross_reference  [B2,C1]  *
  rc_detail  [A2,B1]  *
  rc_inference  [B2,C1]  *
  rc_intent  [B2,C1]  *
  rc_main_idea  [A2,B1]  *
  rc_not_true  [B1,B2]  *
  rc_paraphrase  [B1,B2]  *
  rc_sentence_insertion  [B2,C1]  *
  rc_vocab_in_context  [B1,B2]  *
sp_speaking_skills  [A2,B1,B2,C1]
  sp_content  [B1,B2,C1]  *
  sp_fluency  [A2,B1,B2,C1]  *
  sp_grammar  [B1,B2,C1]  *
  sp_intonation_stress  [B1,B2,C1]  *
  sp_pronunciation  [A2,B1,B2,C1]  *
  sp_vocabulary  [B1,B2,C1]  *
vocab_topics  [A1,A2,B1,B2,C1]
  vocab_business_office_b1  [B1]  *
  vocab_business_office_b2  [B2]  *
  vocab_business_office_c1  [C1]  *
  vocab_daily_life_a1  [A1]  *
  vocab_daily_life_a2  [A2]  *
  vocab_daily_life_b1  [B1]  *
  vocab_dining_entertainment_a1  [A1]  *
  vocab_dining_entertainment_a2  [A2]  *
  vocab_dining_entertainment_b1  [B1]  *
  vocab_education_career_a2  [A2]  *
  vocab_education_career_b1  [B1]  *
  vocab_education_career_b2  [B2]  *
  vocab_education_career_c1  [C1]  *
  vocab_health_wellbeing_a2  [A2]  *
  vocab_health_wellbeing_b1  [B1]  *
  vocab_health_wellbeing_b2  [B2]  *
  vocab_shopping_finance_a1  [A1]  *
  vocab_shopping_finance_a2  [A2]  *
  vocab_shopping_finance_b1  [B1]  *
  vocab_shopping_finance_b2  [B2]  *
  vocab_technology_media_a2  [A2]  *
  vocab_technology_media_b1  [B1]  *
  vocab_technology_media_b2  [B2]  *
  vocab_technology_media_c1  [C1]  *
  vocab_travel_transport_a1  [A1]  *
  vocab_travel_transport_a2  [A2]  *
  vocab_travel_transport_b1  [B1]  *
  vocab_travel_transport_b2  [B2]  *
wr_writing_skills  [A2,B1,B2,C1]
  wr_coherence  [B1,B2,C1]  *
  wr_grammar  [B1,B2,C1]  *
  wr_mechanics  [A2,B1,B2]  *
  wr_organization  [B1,B2,C1]  *
  wr_task_response  [A2,B1,B2,C1]  *
  wr_vocabulary  [B1,B2,C1]  *
```
`*` = node lá, sẽ mang item.

## Node lá

150 node dưới đây là nơi item thực sự gắn vào. Chỉ tiêu 10–30 item mỗi node áp dụng cho danh sách này, không áp cho node gom nhóm.

**grammar** (90)

- `gram_adj_ed_ing` — Tính từ đuôi -ed và -ing
- `gram_adj_order` — Trật tự tính từ
- `gram_adverb_degree` — Trạng từ chỉ mức độ
- `gram_adverb_formation` — Cấu tạo trạng từ
- `gram_adverb_frequency` — Trạng từ tần suất
- `gram_article_definite` — Mạo từ xác định the
- `gram_article_indefinite` — Mạo từ không xác định a/an
- `gram_article_zero` — Không dùng mạo từ
- `gram_as_as_comparison` — So sánh bằng
- `gram_be_present` — Động từ to be ở hiện tại
- `gram_cleft_sentence` — Câu chẻ
- `gram_comparative_adj` — So sánh hơn của tính từ
- `gram_comparative_adv` — So sánh của trạng từ
- `gram_conditional_first` — Câu điều kiện loại 1
- `gram_conditional_mixed` — Câu điều kiện hỗn hợp
- `gram_conditional_second` — Câu điều kiện loại 2
- `gram_conditional_third` — Câu điều kiện loại 3
- `gram_conditional_zero` — Câu điều kiện loại 0
- `gram_conjunction_coordinating` — Liên từ kết hợp
- `gram_conjunction_subordinating` — Liên từ phụ thuộc
- `gram_dependent_preposition` — Giới từ đi kèm
- `gram_discourse_marker` — Từ đánh dấu diễn ngôn
- `gram_double_comparative` — So sánh kép
- `gram_future_going_to` — Tương lai với be going to
- `gram_future_perfect_continuous` — Tương lai tiếp diễn và tương lai hoàn thành
- `gram_future_will` — Tương lai với will
- `gram_gerund_after_prep` — Danh động từ sau giới từ
- `gram_gerund_as_subject` — Danh động từ làm chủ ngữ hoặc tân ngữ
- `gram_infinitive_purpose` — Động từ nguyên mẫu chỉ mục đích
- `gram_inversion_negative` — Đảo ngữ sau trạng từ phủ định
- `gram_linking_cause_result` — Từ nối chỉ nguyên nhân và kết quả
- `gram_linking_contrast` — Từ nối chỉ tương phản
- `gram_linking_purpose` — Từ nối chỉ mục đích
- `gram_modal_ability` — Khuyết thiếu chỉ khả năng
- `gram_modal_deduction` — Khuyết thiếu chỉ suy đoán
- `gram_modal_obligation` — Khuyết thiếu chỉ bắt buộc và lời khuyên
- `gram_modal_perfect` — Khuyết thiếu hoàn thành
- `gram_modal_permission_request` — Khuyết thiếu xin phép và đề nghị
- `gram_negation` — Câu phủ định
- `gram_noun_clause` — Mệnh đề danh từ
- `gram_noun_countability` — Danh từ đếm được và không đếm được
- `gram_participle_clause` — Mệnh đề phân từ
- `gram_passive_causative` — Thể sai khiến
- `gram_passive_past` — Bị động quá khứ và tương lai
- `gram_passive_perfect_modal` — Bị động hoàn thành và khuyết thiếu
- `gram_passive_present` — Bị động hiện tại
- `gram_past_continuous` — Thì quá khứ tiếp diễn
- `gram_past_perfect` — Thì quá khứ hoàn thành
- `gram_past_simple` — Thì quá khứ đơn
- `gram_phrasal_verb_inseparable` — Cụm động từ không tách được
- `gram_phrasal_verb_separable` — Cụm động từ tách được
- `gram_phrasal_verb_three_part` — Cụm động từ ba thành phần
- `gram_plural_forms` — Danh từ số nhiều
- `gram_possessive_s` — Sở hữu cách
- `gram_prefix_negative` — Tiền tố phủ định
- `gram_preposition_basic` — Giới từ thời gian và nơi chốn cơ bản
- `gram_preposition_movement` — Giới từ chuyển động
- `gram_preposition_time_advanced` — Giới từ thời gian nâng cao
- `gram_present_continuous` — Thì hiện tại tiếp diễn
- `gram_present_perfect` — Thì hiện tại hoàn thành
- `gram_present_perfect_continuous` — Thì hiện tại hoàn thành tiếp diễn
- `gram_present_perfect_vs_past_simple` — Hiện tại hoàn thành và quá khứ đơn
- `gram_present_simple` — Thì hiện tại đơn
- `gram_present_simple_vs_continuous` — Hiện tại đơn và hiện tại tiếp diễn
- `gram_pronoun_possessive` — Tính từ và đại từ sở hữu
- `gram_pronoun_reflexive` — Đại từ phản thân
- `gram_pronoun_subject_object` — Đại từ chủ ngữ và tân ngữ
- `gram_quantifier_advanced` — Lượng từ nâng cao
- `gram_quantifier_basic` — Lượng từ cơ bản
- `gram_question_formation` — Cấu tạo câu hỏi
- `gram_question_tag` — Câu hỏi đuôi
- `gram_relative_defining` — Mệnh đề quan hệ xác định
- `gram_relative_nondefining` — Mệnh đề quan hệ không xác định
- `gram_relative_preposition` — Mệnh đề quan hệ có giới từ
- `gram_relative_reduced` — Mệnh đề quan hệ rút gọn
- `gram_reported_backshift` — Lùi thì trong câu tường thuật
- `gram_reported_question` — Tường thuật câu hỏi
- `gram_reported_statement` — Tường thuật câu kể
- `gram_subject_verb_agreement` — Hoà hợp chủ ngữ và động từ
- `gram_subjunctive_mandative` — Thức giả định trong mệnh lệnh gián tiếp
- `gram_superlative_adj` — So sánh nhất của tính từ
- `gram_there_is_are` — Cấu trúc there is / there are
- `gram_time_clause_future` — Mệnh đề thời gian chỉ tương lai
- `gram_verb_gerund_vs_infinitive` — Động từ theo sau bởi V-ing hay to-V
- `gram_verb_object_infinitive` — Động từ + tân ngữ + to-V
- `gram_wish_regret` — Cấu trúc wish và nuối tiếc
- `gram_word_form_adj` — Cấu tạo tính từ
- `gram_word_form_adverb` — Cấu tạo trạng từ từ tính từ
- `gram_word_form_noun` — Cấu tạo danh từ
- `gram_word_form_verb` — Cấu tạo động từ

**listening** (11)

- `lc_detail` — Bắt chi tiết cụ thể
- `lc_gist` — Nắm ý chính bài nghe
- `lc_graphic_reference` — Đối chiếu bảng biểu khi nghe
- `lc_indirect_response` — Đáp gián tiếp
- `lc_inference` — Suy luận hàm ý
- `lc_next_action` — Dự đoán hành động tiếp theo
- `lc_photo_action` — Mô tả hành động trong ảnh
- `lc_photo_state` — Mô tả trạng thái trong ảnh
- `lc_speaker_role` — Xác định vai người nói và địa điểm
- `lc_wh_question` — Đáp câu hỏi wh-
- `lc_yes_no` — Đáp câu hỏi yes-no

**reading** (9)

- `rc_cross_reference` — Đối chiếu nhiều văn bản
- `rc_detail` — Tìm chi tiết cụ thể
- `rc_inference` — Suy luận
- `rc_intent` — Suy ý định người viết
- `rc_main_idea` — Xác định ý chính
- `rc_not_true` — Câu hỏi phủ định NOT/EXCEPT
- `rc_paraphrase` — Nhận diện diễn đạt lại
- `rc_sentence_insertion` — Chèn câu vào vị trí đúng
- `rc_vocab_in_context` — Từ vựng theo ngữ cảnh

**speaking** (6)

- `sp_content` — Nội dung và triển khai ý
- `sp_fluency` — Độ trôi chảy
- `sp_grammar` — Độ chính xác và đa dạng ngữ pháp khi nói
- `sp_intonation_stress` — Ngữ điệu và trọng âm
- `sp_pronunciation` — Phát âm
- `sp_vocabulary` — Vốn từ khi nói

**vocabulary** (28)

- `vocab_business_office_b1` — Từ vựng giao tiếp văn phòng, B1
- `vocab_business_office_b2` — Từ vựng vận hành doanh nghiệp, B2
- `vocab_business_office_c1` — Từ vựng chiến lược doanh nghiệp, C1
- `vocab_daily_life_a1` — Từ vựng đời sống và nhà ở, A1
- `vocab_daily_life_a2` — Từ vựng đời sống và nhà ở, A2
- `vocab_daily_life_b1` — Từ vựng đời sống và nhà ở, B1
- `vocab_dining_entertainment_a1` — Từ vựng ăn uống, A1
- `vocab_dining_entertainment_a2` — Từ vựng ăn uống và giải trí, A2
- `vocab_dining_entertainment_b1` — Từ vựng sự kiện và dịch vụ tiếp đón, B1
- `vocab_education_career_a2` — Từ vựng học tập, A2
- `vocab_education_career_b1` — Từ vựng đào tạo và nghề nghiệp, B1
- `vocab_education_career_b2` — Từ vựng nhân sự, B2
- `vocab_education_career_c1` — Từ vựng phát triển chuyên môn, C1
- `vocab_health_wellbeing_a2` — Từ vựng sức khoẻ, A2
- `vocab_health_wellbeing_b1` — Từ vựng sức khoẻ và an toàn, B1
- `vocab_health_wellbeing_b2` — Từ vựng phúc lợi nơi làm việc, B2
- `vocab_shopping_finance_a1` — Từ vựng mua sắm và tiền bạc, A1
- `vocab_shopping_finance_a2` — Từ vựng mua sắm và tiền bạc, A2
- `vocab_shopping_finance_b1` — Từ vựng mua sắm và tài chính, B1
- `vocab_shopping_finance_b2` — Từ vựng tài chính và kế toán, B2
- `vocab_technology_media_a2` — Từ vựng công nghệ, A2
- `vocab_technology_media_b1` — Từ vựng công nghệ văn phòng, B1
- `vocab_technology_media_b2` — Từ vựng số và truyền thông, B2
- `vocab_technology_media_c1` — Từ vựng chiến lược công nghệ, C1
- `vocab_travel_transport_a1` — Từ vựng du lịch và giao thông, A1
- `vocab_travel_transport_a2` — Từ vựng du lịch và giao thông, A2
- `vocab_travel_transport_b1` — Từ vựng công tác, B1
- `vocab_travel_transport_b2` — Từ vựng vận chuyển và kho vận, B2

**writing** (6)

- `wr_coherence` — Mạch lạc và liên kết
- `wr_grammar` — Độ chính xác và đa dạng ngữ pháp khi viết
- `wr_mechanics` — Chính tả, dấu câu và văn phong
- `wr_organization` — Bố cục
- `wr_task_response` — Đáp ứng yêu cầu đề
- `wr_vocabulary` — Vốn từ khi viết

## Thứ tự topological của prerequisite graph

Không có cycle. Thứ tự dưới đây là một trình tự học hợp lệ: mọi concept đều đứng sau toàn bộ prerequisite của nó.

```
  1. gram_adj_ed_ing
  2. gram_adj_order
  3. gram_adverb_formation
  4. gram_adverb_frequency
  5. gram_adverbials
  6. gram_be_present
  7. gram_clauses
  8. gram_cohesion
  9. gram_comparative_adj
 10. gram_comparison
 11. gram_conditionals
 12. gram_conjunction_coordinating
 13. gram_infinitive_purpose
 14. gram_modal_ability
 15. gram_modality
 16. gram_nouns_determiners
 17. gram_phrasal_verbs
 18. gram_plural_forms
 19. gram_preposition_basic
 20. gram_prepositions
 21. gram_pronoun_subject_object
 22. gram_pronouns
 23. gram_reported_speech
 24. gram_sentence_structure
 25. gram_tense_aspect
 26. gram_verb_patterns
 27. gram_voice
 28. gram_word_form_adj
 29. gram_word_form_noun
 30. gram_word_form_verb
 31. gram_word_formation
 32. lc_gist
 33. lc_listening_skills
 34. lc_photo_action
 35. rc_detail
 36. rc_main_idea
 37. rc_reading_skills
 38. sp_content
 39. sp_fluency
 40. sp_grammar
 41. sp_pronunciation
 42. sp_speaking_skills
 43. sp_vocabulary
 44. vocab_business_office_b1
 45. vocab_daily_life_a1
 46. vocab_dining_entertainment_a1
 47. vocab_education_career_a2
 48. vocab_health_wellbeing_a2
 49. vocab_shopping_finance_a1
 50. vocab_technology_media_a2
 51. vocab_topics
 52. vocab_travel_transport_a1
 53. wr_grammar
 54. wr_mechanics
 55. wr_organization
 56. wr_task_response
 57. wr_vocabulary
 58. wr_writing_skills
 59. gram_adverb_degree
 60. gram_word_form_adverb
 61. gram_negation
 62. gram_present_continuous
 63. gram_present_simple
 64. gram_question_formation
 65. gram_there_is_are
 66. gram_as_as_comparison
 67. gram_comparative_adv
 68. gram_double_comparative
 69. gram_superlative_adj
 70. gram_conjunction_subordinating
 71. gram_linking_purpose
 72. gram_verb_object_infinitive
 73. gram_modal_obligation
 74. gram_modal_permission_request
 75. gram_noun_countability
 76. gram_possessive_s
 77. gram_dependent_preposition
 78. gram_gerund_after_prep
 79. gram_phrasal_verb_separable
 80. gram_preposition_movement
 81. gram_preposition_time_advanced
 82. gram_pronoun_possessive
 83. gram_pronoun_reflexive
 84. gram_relative_defining
 85. gram_prefix_negative
 86. lc_detail
 87. lc_speaker_role
 88. lc_photo_state
 89. rc_not_true
 90. rc_paraphrase
 91. rc_vocab_in_context
 92. sp_intonation_stress
 93. vocab_business_office_b2
 94. vocab_daily_life_a2
 95. vocab_dining_entertainment_a2
 96. vocab_education_career_b1
 97. vocab_health_wellbeing_b1
 98. vocab_shopping_finance_a2
 99. vocab_technology_media_b1
100. vocab_travel_transport_a2
101. wr_coherence
102. gram_future_going_to
103. gram_conditional_zero
104. gram_future_will
105. gram_passive_present
106. gram_past_simple
107. gram_present_simple_vs_continuous
108. gram_subject_verb_agreement
109. gram_inversion_negative
110. gram_question_tag
111. lc_wh_question
112. lc_yes_no
113. gram_linking_cause_result
114. gram_linking_contrast
115. gram_modal_deduction
116. gram_article_indefinite
117. gram_quantifier_basic
118. gram_gerund_as_subject
119. gram_verb_gerund_vs_infinitive
120. gram_phrasal_verb_inseparable
121. gram_cleft_sentence
122. gram_relative_nondefining
123. lc_graphic_reference
124. lc_next_action
125. lc_inference
126. rc_inference
127. rc_sentence_insertion
128. vocab_business_office_c1
129. vocab_daily_life_b1
130. vocab_dining_entertainment_b1
131. vocab_education_career_b2
132. vocab_health_wellbeing_b2
133. vocab_shopping_finance_b1
134. vocab_technology_media_b2
135. vocab_travel_transport_b1
136. gram_conditional_first
137. gram_time_clause_future
138. gram_passive_past
139. gram_past_continuous
140. gram_present_perfect
141. gram_reported_statement
142. lc_indirect_response
143. gram_discourse_marker
144. gram_article_definite
145. gram_quantifier_advanced
146. gram_participle_clause
147. gram_phrasal_verb_three_part
148. gram_relative_preposition
149. rc_cross_reference
150. rc_intent
151. vocab_education_career_c1
152. vocab_shopping_finance_b2
153. vocab_technology_media_c1
154. vocab_travel_transport_b2
155. gram_conditional_second
156. gram_passive_causative
157. gram_future_perfect_continuous
158. gram_modal_perfect
159. gram_passive_perfect_modal
160. gram_past_perfect
161. gram_present_perfect_continuous
162. gram_present_perfect_vs_past_simple
163. gram_noun_clause
164. gram_reported_backshift
165. gram_reported_question
166. gram_article_zero
167. gram_relative_reduced
168. gram_wish_regret
169. gram_conditional_third
170. gram_subjunctive_mandative
171. gram_conditional_mixed
```
