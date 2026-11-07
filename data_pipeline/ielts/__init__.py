"""AI-written IELTS Academic full test sets: plan, write, check, build, record, load.

The model writes each component in a small JSON shape (`ielts.rules` documents it); the rules
reject anything an IELTS paper must not contain, and `ielts.build` turns accepted components
into the exact JSON the admin editor saves. Nothing here publishes: sets load as drafts.
"""
