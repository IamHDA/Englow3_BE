"""Enum dùng chung — §3.1, §3.2 và §2.2 của work order."""

from __future__ import annotations

from enum import StrEnum

__all__ = [
    "CEFRLevel", "ModuleType", "ReviewStatus", "CEFRSource", "PartOfSpeech",
    "CollocationPattern", "PassageType", "Accent", "AlignmentStatus",
    "CalibrationStatus", "QuestionType", "OptionLabel", "WritingTaskType",
    "SourceType", "READING_TYPES", "LISTENING_TYPES", "GRAMMAR_TYPES",
    "VOCAB_TYPES", "DISCOURSE_TYPES",
]


class CEFRLevel(StrEnum):
    """§3.2 — C2 nằm ngoài phạm vi."""
    A1 = "A1"
    A2 = "A2"
    B1 = "B1"
    B2 = "B2"
    C1 = "C1"


class ModuleType(StrEnum):
    """8 loại batch — §2.2."""
    FLASHCARD = "FLASHCARD"
    GRAMMAR = "GRAMMAR"
    COLLOCATION = "COLLOCATION"
    EXAM = "EXAM"
    SPEAKING = "SPEAKING"
    WRITING = "WRITING"
    SHADOWING = "SHADOWING"
    ASSESSMENT_PROMPT = "ASSESSMENT_PROMPT"


class ReviewStatus(StrEnum):
    DRAFT = "draft"
    AUTO_VALIDATED = "auto_validated"
    HUMAN_APPROVED = "human_approved"


class CEFRSource(StrEnum):
    """Nguồn của nhãn CEFR — §0.4 bắt buộc truy vết được.

    `octanove` là bổ sung ngoài §2.3 (quyết định D3): 600 từ band C1 đến từ
    Octanove Vocabulary Profile, không phải CEFR-J. Gán bừa thành `cefrj` là
    khai sai nguồn.
    """
    EVP = "evp"
    CEFRJ = "cefrj"
    OCTANOVE = "octanove"
    NGSL_BAND = "ngsl_band"
    LLM_ESTIMATE = "llm_estimate"
    HUMAN_VERIFIED = "human_verified"


class PartOfSpeech(StrEnum):
    NOUN = "noun"
    VERB = "verb"
    ADJECTIVE = "adjective"
    ADVERB = "adverb"
    PREPOSITION = "preposition"
    CONJUNCTION = "conjunction"
    PRONOUN = "pronoun"
    DETERMINER = "determiner"
    PHRASAL_VERB = "phrasal_verb"
    IDIOM = "idiom"
    COLLOCATION = "collocation"


class CollocationPattern(StrEnum):
    """Lỗi P1-9: collocation là object có `pattern`, không phải chuỗi phẳng."""
    V_N = "V+N"
    ADJ_N = "ADJ+N"
    N_N = "N+N"
    V_PREP = "V+PREP"
    PREP_N = "PREP+N"
    ADV_ADJ = "ADV+ADJ"
    N_PREP = "N+PREP"


class SourceType(StrEnum):
    GENERATED = "generated"
    CORPUS = "corpus"


class PassageType(StrEnum):
    EMAIL = "email"
    LETTER = "letter"
    NOTICE = "notice"
    ADVERTISEMENT = "advertisement"
    ARTICLE = "article"
    MEMO = "memo"
    FORM = "form"
    SCHEDULE = "schedule"
    CHART = "chart"
    CHAT_MESSAGE = "chat_message"
    INVOICE = "invoice"
    WEB_PAGE = "web_page"


class Accent(StrEnum):
    """Lỗi P1-6 — bắt buộc trên mọi AudioAsset."""
    US = "US"
    UK = "UK"
    AU = "AU"
    CA = "CA"


class AlignmentStatus(StrEnum):
    PENDING = "pending"
    ALIGNED = "aligned"
    FAILED = "failed"


class CalibrationStatus(StrEnum):
    """Lỗi P1-2. Không được khai `calibrated` khi chưa có lượt trả lời thật."""
    UNCALIBRATED = "uncalibrated"
    PROVISIONAL = "provisional"
    CALIBRATED = "calibrated"


class OptionLabel(StrEnum):
    A = "A"
    B = "B"
    C = "C"
    D = "D"


class WritingTaskType(StrEnum):
    EMAIL = "email"
    OPINION_ESSAY = "opinion_essay"
    PICTURE_DESCRIPTION = "picture_description"


class QuestionType(StrEnum):
    """§3.1 — enum đầy đủ, khớp 1-1 với concept_id của taxonomy Phase 1."""
    # Reading
    RC_MAIN_IDEA = "rc_main_idea"
    RC_DETAIL = "rc_detail"
    RC_INFERENCE = "rc_inference"
    RC_VOCAB_IN_CONTEXT = "rc_vocab_in_context"
    RC_PARAPHRASE = "rc_paraphrase"
    RC_NOT_TRUE = "rc_not_true"
    RC_CROSS_REFERENCE = "rc_cross_reference"
    RC_INTENT = "rc_intent"
    RC_SENTENCE_INSERTION = "rc_sentence_insertion"
    # Grammar (Part 5/6)
    GR_WORD_FORM = "gr_word_form"
    GR_TENSE = "gr_tense"
    GR_PREPOSITION = "gr_preposition"
    GR_CONJUNCTION = "gr_conjunction"
    GR_PRONOUN = "gr_pronoun"
    GR_COMPARISON = "gr_comparison"
    GR_RELATIVE_CLAUSE = "gr_relative_clause"
    GR_VOICE = "gr_voice"
    GR_PARTICIPLE = "gr_participle"
    GR_ARTICLE = "gr_article"
    # Vocabulary (Part 5/6)
    VC_WORD_CHOICE = "vc_word_choice"
    VC_COLLOCATION = "vc_collocation"
    VC_PHRASAL_VERB = "vc_phrasal_verb"
    # Discourse (Part 6)
    DS_COHESION = "ds_cohesion"
    DS_SENTENCE_INSERTION = "ds_sentence_insertion"
    # Listening
    LC_PHOTO_ACTION = "lc_photo_action"
    LC_PHOTO_STATE = "lc_photo_state"
    LC_WH_QUESTION = "lc_wh_question"
    LC_YES_NO = "lc_yes_no"
    LC_INDIRECT_RESPONSE = "lc_indirect_response"
    LC_GIST = "lc_gist"
    LC_DETAIL = "lc_detail"
    LC_INFERENCE = "lc_inference"
    LC_SPEAKER_ROLE = "lc_speaker_role"
    LC_NEXT_ACTION = "lc_next_action"
    LC_GRAPHIC_REFERENCE = "lc_graphic_reference"


# Nhóm theo kỹ năng — part_rules.py dùng để kiểm dạng câu hỏi có hợp với part không
READING_TYPES = frozenset({
    QuestionType.RC_MAIN_IDEA, QuestionType.RC_DETAIL, QuestionType.RC_INFERENCE,
    QuestionType.RC_VOCAB_IN_CONTEXT, QuestionType.RC_PARAPHRASE,
    QuestionType.RC_NOT_TRUE, QuestionType.RC_CROSS_REFERENCE,
    QuestionType.RC_INTENT, QuestionType.RC_SENTENCE_INSERTION,
})
GRAMMAR_TYPES = frozenset({
    QuestionType.GR_WORD_FORM, QuestionType.GR_TENSE, QuestionType.GR_PREPOSITION,
    QuestionType.GR_CONJUNCTION, QuestionType.GR_PRONOUN, QuestionType.GR_COMPARISON,
    QuestionType.GR_RELATIVE_CLAUSE, QuestionType.GR_VOICE,
    QuestionType.GR_PARTICIPLE, QuestionType.GR_ARTICLE,
})
VOCAB_TYPES = frozenset({
    QuestionType.VC_WORD_CHOICE, QuestionType.VC_COLLOCATION,
    QuestionType.VC_PHRASAL_VERB,
})
DISCOURSE_TYPES = frozenset({
    QuestionType.DS_COHESION, QuestionType.DS_SENTENCE_INSERTION,
})
LISTENING_TYPES = frozenset({
    QuestionType.LC_PHOTO_ACTION, QuestionType.LC_PHOTO_STATE,
    QuestionType.LC_WH_QUESTION, QuestionType.LC_YES_NO,
    QuestionType.LC_INDIRECT_RESPONSE, QuestionType.LC_GIST,
    QuestionType.LC_DETAIL, QuestionType.LC_INFERENCE,
    QuestionType.LC_SPEAKER_ROLE, QuestionType.LC_NEXT_ACTION,
    QuestionType.LC_GRAPHIC_REFERENCE,
})
