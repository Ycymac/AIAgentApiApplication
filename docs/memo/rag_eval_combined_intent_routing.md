# Rag Eval Combined Intent Routing

## Agent Handoff Summary

- Current goal: evaluate and compare eval-chain intent modes without changing production RAG routing; latest run focused on stable `intentMode=layered` business two-layer recognition.
- Current implementation: eval endpoints and the external Python eval CLI support `intentMode=combined|layered`; default eval endpoint mode is `combined`; combined prompt has a v2 prompt-only revision; a local stdlib runner now exists for 5-round layered stability plus production multi-turn probes, with optional truncated answer-text capture.
- Combined mode: scores a synthetic SYSTEM node plus all enabled KB nodes in one LLM call under `rag.eval.intent`.
- Layered mode: reuses the production `IntentResolver` baseline.
- Important files: `rag/eval/intent/*`, `RagEvalServiceImpl`, `RagEvalStreamServiceImpl`, `RagEvalResponse`, `RagEvalStreamEvents`, `prompt/eval-combined-intent-classifier.st`, `docs/javis_test_report/scripts/run_business_layered_stability.py`, `docs/javis_test_report/scripts/build_business_layered_html_report.py`.
- Verified command: with JDK17, `mvn -pl rag -am -DskipTests compile` succeeded; eval A/B runs completed on 2026-06-23; 5-round layered stability run completed on 2026-06-24.
- Known risk: local YAML diffs may contain credential-like values; do not copy or commit secrets.
- Next step: fix the stable out-of-domain boundary false positive (`BUS-BOUNDARY-002`) and consider reducing layered first-event/TTFT latency; keep future HTML reports directly under `docs/javis_test_report`.

## Metadata
- Project: `D:\JavaProjects\AIApplication`
- Source transcript: current Codex session on 2026-06-23
- Generated/Updated at: 2026-06-23 08:43 Asia/Hong_Kong
- Memo purpose: preserve the eval-only combined intent routing experiment and verification context for later agents.

## User Goal

Add an eval/test-chain experiment that performs intent recognition in one LLM call by scoring the SYSTEM node and all enabled knowledge-base nodes together. Keep production RAG routing unchanged. If new classes are needed, place them under the eval package for now.

## Confirmed Facts

- The project should be built with JDK 17. JDK 21 triggers a Lombok/Javac internal compatibility failure: `NoSuchFieldError` for `JCTree$JCImport.qualid`.
- Production intent routing remains the existing layered flow through `IntentResolver`.
- Eval endpoints now support a selectable intent mode:
  - `combined`: eval-only one-pass scoring of SYSTEM plus enabled KB nodes.
  - `layered`: production-compatible two-stage routing baseline.
- The default eval intent mode is `combined`.

## User Preferences / Instructions

- Do not change the production RAG chat/intent chain for this experiment.
- Reuse existing RAG module services where possible.
- Put temporary/new experiment-specific classes under `rag.eval`.
- Use JDK 17 for verification.
- Avoid reading existing memo Markdown files when the user explicitly asks not to.

## Executed Steps

- Read memory and non-memo project guidance related to RAG evaluation, intent routing, and the previous rewrite -> intent -> guidance -> retrieval -> prompt chain.
- Added eval-only routing mode parsing via `RagEvalIntentMode`.
- Added `RagEvalIntentRouter` to switch between production layered routing and eval combined routing.
- Added `RagEvalCombinedIntentResolver`, which builds a candidate list containing a synthetic SYSTEM node plus all enabled KB nodes from `IntentNodeRegistry`, asks one prompt to score all candidates, and converts results back into existing `SubQuestionIntent` / `NodeScore` structures.
- Added prompt template `rag/src/main/resources/prompt/eval-combined-intent-classifier.st`.
- Wired `intentMode` through non-stream eval retrieve and stream eval endpoints.
- Extended non-stream eval response with `intentMode` and all candidate scores for diagnostics.
- Extended stream meta event with `intentMode`.

## Current Plan

Use the new `intentMode` query parameter to A/B test the same questions:

- `intentMode=combined` for one-pass SYSTEM plus KB scoring.
- `intentMode=layered` for the original two-stage production-compatible route.

Compare route, selected nodes, candidate scores, latency, and retrieval quality.

## Rejected / Superseded Plans

- Do not replace production `IntentResolver` yet. This is intentionally an eval-only experiment.
- Do not silently overwrite the layered baseline. Keeping both modes makes later testing cleaner.

## Produced Outputs and Locations

- `rag/src/main/java/com/ycy/aiapplication/rag/eval/intent/RagEvalIntentMode.java`
- `rag/src/main/java/com/ycy/aiapplication/rag/eval/intent/RagEvalIntentRouter.java`
- `rag/src/main/java/com/ycy/aiapplication/rag/eval/intent/RagEvalCombinedIntentResolver.java`
- `rag/src/main/resources/prompt/eval-combined-intent-classifier.st`
- Updated eval controllers/services/DTOs under `rag/src/main/java/com/ycy/aiapplication/rag/eval`.

## Open Questions / Risks

- The current working tree contains unrelated config/resource changes outside the eval intent experiment, including credential-like values in application YAML diffs. Do not copy those values into docs or external messages.
- The combined prompt and thresholds are experimental; route quality still needs real eval runs.
- The synthetic SYSTEM node is eval-only and does not replace any production node source.

## Next Steps

- Start the app with eval enabled.
- Run the same dataset/questions with both `intentMode=combined` and `intentMode=layered`.
- Compare first-token latency, route accuracy, candidate node scores, and final retrieval chunk/document hits.
- If combined performs better, decide whether to productionize by refactoring shared routing abstractions outside eval.

## Incremental Updates

### 2026-06-24 16:05 Asia/Hong_Kong
- Ran a new stable business-layered test after the user confirmed the backend was already started and Bailian balance was restored.
- Created dataset `docs/javis_test_report/scripts/datasets/business_layered_stable_dataset_20260624.jsonl` with 24 questions per round:
  - 14 simple clean GetCoupon business chunk-gold questions.
  - 5 composite multi-chunk business questions.
  - 5 boundary/wrong-premise questions.
  - 20 retrieval-gold samples per round for hit@5 / recall@5 / MRR@10.
- Added stdlib runner `docs/javis_test_report/scripts/run_business_layered_stability.py` to avoid the broken external venv launcher and to run:
  - authenticated eval stream `/rag/eval/chat-stream` with `intentMode=layered`;
  - production `/rag/v3/chat` multi-turn probes by reading `conversationId` from the first turn's `meta` event and reusing it on follow-ups;
  - 2026-06-23 baseline extraction from existing external score JSON files.
- Verified 5 full rounds:
  - 120 eval stream records, 0 errors.
  - 30 production dialogue turns, 0 errors, conversation continuity 100%.
  - Login was used only to obtain a token and was intentionally excluded from metrics.
- Stable round metrics:
  - hit@5 median/min/max = 100% / 95% / 100%.
  - recall@5 median/min/max = 100% / 95% / 100%.
  - MRR@10 median/min/max = 100% / 95% / 100%.
  - route accuracy fixed at 95.8%.
  - boundary pass fixed at 90%.
  - no-retrieval accuracy fixed at 75%.
- Stage medians across 120 eval records:
  - first_event 5490 ms, first_token 5763 ms, answer_complete 8502 ms.
  - rewrite 802 ms, intent 1750 ms, retrieval 1470 ms.
- Key risks found:
  - `BUS-BOUNDARY-002` ("帮我写一个 Vue 登录页面。") expected SYSTEM but routed to KB in all 5 rounds.
  - `BUS-COMPOSITE-002` had one hit@5 miss in round 4 while still routing to KB.
  - Largest latency outlier was round 3 `BUS-COMPOSITE-004`: answer_complete 90795 ms, retrieval 12636 ms, route/hit still correct.
- Outputs:
  - Markdown summary/interpretation files were superseded by the later HTML report and removed.
  - Aggregate JSON: `docs/javis_test_report/business_layered_stability_20260624_5round/aggregate.json`.
  - CSVs: `per_sample.csv`, `dialogue.csv`, `round_metrics.csv` in the same directory.
  - Raw JSONL: `all_eval_records.jsonl`, `all_dialogue_records.jsonl`, plus per-round JSONL files.

### 2026-06-24 20:00 Asia/Hong_Kong
- User preference recorded: all future HTML reports should be generated or placed directly under `D:\JavaProjects\AIApplication\docs\javis_test_report`, not inside per-run subdirectories. Do not move the already generated current HTML again unless explicitly requested.
- Added HTML report builder `docs/javis_test_report/scripts/build_business_layered_html_report.py`.
  - It reads a per-run data directory such as `docs/javis_test_report/business_layered_stability_20260624_5round`.
  - It now defaults final HTML output to `docs/javis_test_report/business_layered_stability_20260624_5round.html`.
  - It generates chart PNGs under the per-run `charts/` directory and `question_quality.json` beside the run data.
- Generated root HTML report: `docs/javis_test_report/business_layered_stability_20260624_5round.html`.
  - Browser-render verification succeeded once before the later approval/usage rejection; seven chart images loaded and the report contained eight visible sections.
  - The same-named subdirectory HTML exists as an identical copy from the earlier generation and was not moved after the user asked not to move current HTML.
- Deleted current 2026-06-24 Markdown report files from the 5-round run directory and smoke run directory; old 2026-06-16 Markdown archives were left untouched.
- Moved the stable business layered dataset from `docs/javis_test_report/data/` to the test-script-adjacent location `docs/javis_test_report/scripts/datasets/business_layered_stable_dataset_20260624.jsonl`; removed the now-empty `data/` directory.
- Updated `docs/javis_test_report/scripts/run_business_layered_stability.py`:
  - Default dataset path now points to `docs/javis_test_report/scripts/datasets/business_layered_stable_dataset_20260624.jsonl`.
  - Added `--capture-answer-text` and `--answer-text-limit` so future runs can persist truncated answer text for representative-answer reports.
- Updated aggregate JSON dataset pointers in the full and smoke run directories to the new dataset path.
- Verified commands:
  - `python -m py_compile docs/javis_test_report/scripts/build_business_layered_html_report.py docs/javis_test_report/scripts/run_business_layered_stability.py`
  - file checks confirmed the target 5-round run directory has no Markdown files and contains seven PNG chart assets.
- Open risk: the original 5-round run did not persist full answer text, so the current HTML report's representative-answer section uses answer summaries, retrieval evidence, and automatic quality proxies rather than raw model answer text.

### 2026-06-23 17:10 Asia/Hong_Kong
- After the user rebuilt and restarted the backend, ran only fresh `intentMode=combined` with prompt v2 and reused the previous layered baseline score files.
- Backend precheck succeeded: `python -m eval rag doctor --base-url http://localhost:10040`.
- Fresh combined v2 runs:
  - `scenario_real_v1.jsonl`: `java_jvm_20260623_165756`, route accuracy 100%, total P50/P95 3334/3905 ms, intent P50/P95 2017/2484 ms.
  - `chunk_eval_20260616_171052.jsonl` non-stream: `java_jvm_20260623_165914`, route accuracy 100%, hit@5 100%, MRR@10 100%, Top1 100%, total P50/P95 3803/4689 ms.
  - `chunk_eval_20260616_171052.jsonl` stream: `chunk_stream_20260623_170054`, route accuracy 100%, hit@5 100%, MRR@10 100%, first event P50/P95 5374/5769 ms, TTFT P50/P95 5380/5777 ms, complete P50/P95 7126/10221 ms.
- Reused layered baseline:
  - `java_jvm_20260623_161917` for scenario.
  - `java_jvm_20260623_162126` for chunk non-stream.
  - `chunk_stream_20260623_162848` for chunk stream.
- 2.0 visual report: `docs/javis_test_report/intent_mode_ab_report_2_0_20260623_1710.html`.
- Report additions requested by the user were included: metric bar charts, execution-time proportion pie charts, and stage-time distribution charts.
- Interpretation: prompt v2 fixed the previous combined quality issue (no explicit KB questions fell to GUIDANCE/UNKNOWN on the clean chunk set), but fresh combined v2 still has slower first-event/TTFT P50 than layered because the intent stage remains longer.

### 2026-06-23 prompt-only v2 plan
- Updated `rag/src/main/resources/prompt/eval-combined-intent-classifier.st` only; no Java decision code changed.
- Prompt v2 asks the model to internally simulate the production two-layer thinking: first coarse RAG/SYSTEM judgment, then concrete KB node scoring, while still outputting only existing candidate node ids.
- Added more quantitative score bands for SYSTEM and KB nodes to reduce model discretion.
- Added conservative routing instructions: technical/project/business implementation questions should keep a related KB node above threshold instead of falling to SYSTEM or all-low scores.
- Added examples for the previous combined failures and key baselines:
  - GetCoupon Lua cache warmup / HMSET + EXPIREAT.
  - GetCoupon template validation rules.
  - GetCoupon responsibility-chain vs Spring Validation.
  - GetCoupon AOP + Lua + Redis idempotency.
  - Java inheritance/polymorphism, Java String, JVM purpose.
  - SYSTEM-only greetings and identity questions.
- Next requested test plan after rebuild:
  - Run only `intentMode=combined` for fresh v2 data.
  - Reuse the previous `layered` score data as the baseline.
  - Generate a 2.0 HTML report with bar charts, execution-time proportion pie chart, and stage-time distribution charts.

### 2026-06-23 16:31 Asia/Hong_Kong
- Ran the previous Claude Code eval workflow using `D:\PythonProjects\aiapplication-rag-eval\.venv\Scripts\python.exe`.
- Backend ports used: RAG `http://localhost:10040`, auth `http://localhost:10020`; credentials were used only for stream-all and are not stored here.
- Datasets:
  - `eval\rag\dataset\scenario_real_v1.jsonl` (23 real scenario questions).
  - `eval\rag\dataset\generated\chunk_eval_20260616_171052.jsonl` (14 latest clean GetCoupon chunk samples from the prior CC flow).
- Non-stream `all` A/B:
  - scenario combined: route accuracy 100%, total P50 3080 ms, intent P50 1821 ms.
  - scenario layered: route accuracy 100%, total P50 3106 ms, intent P50 1346 ms.
  - chunk combined: route/hit@5 78.6%, MRR@10 78.6%, total P50 3651 ms; CHUNK-0003/0005/0007 fell to GUIDANCE/UNKNOWN.
  - chunk layered: route/hit@5 100%, MRR@10 95.2%, total P50 3321 ms.
- Stream `stream-all` A/B on the clean chunk dataset with `workers=1`:
  - combined: first event P50 5225 ms, TTFT P50 5230 ms, complete P50 8336 ms, hit@5 85.7%, MRR@10 86.6%; CHUNK-0003 fell to GUIDANCE.
  - layered: first event P50 4633 ms, TTFT P50 4636 ms, complete P50 7356 ms, hit@5 100%, MRR@10 100%.
- Summary HTML report: `docs/javis_test_report/intent_mode_ab_report_20260623_1631.html`.
- Individual HTML reports:
  - `docs/javis_test_report/aiapplication-rag-eval_java_jvm_20260623_161656.html`
  - `docs/javis_test_report/aiapplication-rag-eval_java_jvm_20260623_161917.html`
  - `docs/javis_test_report/aiapplication-rag-eval_java_jvm_20260623_162021.html`
  - `docs/javis_test_report/aiapplication-rag-eval_java_jvm_20260623_162126.html`
  - `docs/javis_test_report/aiapplication-rag-eval_chunk_stream_20260623_162620.html`
  - `docs/javis_test_report/aiapplication-rag-eval_chunk_stream_20260623_162848.html`
- Current interpretation: one-pass combined reduces the number of intent LLM calls, but the larger candidate prompt and weaker fallback made it slower or no faster in this sample, and less stable on GetCoupon KB questions. Keep production layered unchanged.

### 2026-06-23 08:43 Asia/Hong_Kong
- Implemented eval-only combined intent routing and wired both retrieve and stream eval paths.
- Verified with `JAVA_HOME=C:\Program Files\Java\jdk-17` and `mvn -pl rag -am -DskipTests compile`; build succeeded.
- Earlier JDK 21 compile attempt failed due to known Lombok/Javac compatibility, not due to the eval code changes.

## Redaction Notes

- Secrets and credential-like values observed in working-tree config diffs were intentionally omitted.
- Existing memo Markdown contents were not read for this update.
