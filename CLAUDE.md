# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"AI 면접 준비 도우미": given a job posting and a resume PDF, it extracts evaluation criteria, finds per-criterion evidence in the resume, and builds a personalized interview question sheet. Interviewers can record answers per question and download the sheet as a PDF. AI never makes hire/no-hire judgments. The original spec is **`AI 면접 준비 도우미 구현 지시서.md`** (Korean), but the UI has since been simplified (see below).

**Deliberate deviations from the spec:**
- **Two tabs, per the user, in this order:** `공통 질문 설정` (`/templates`) and `면접 질문 추출하기` (`/extract`, also the `/` landing page). No brand/logo in the top bar. The extract tab is one page (`extract.jsp`) with three stacked stages: ① 평가항목 추출 (position text and/or up to 5 images → AI ① → editable criteria), ② 포지션 적합도 검사 (resume PDF → AI ② → criterion/evidence rows), ③ 면접 질문 추출 (button → AI ③ → editable question sheet). Later stages render locked until the earlier one is done. The spec's masking-preview step was removed on purpose.
- **UI taste (user):** use the full width, keep it simple, no explanatory helper text or minor stats.
- **Position files:** `PositionService.create` takes images, PDFs and .txt/.md (max 5). PDF/text content is appended to `jd_text` under a `[filename]` line; a PDF with almost no text (scanned) is rendered to PNG pages (`PdfTextExtractor.renderPages`) and treated as images. **Position images** live in `position_image` (BYTEA, type sniffed from bytes, not the upload header). AI ① gets them as `image_url` data-URL parts via `AiClient.call(..., images, ...)` and returns `image_text`, stored in `position.image_text`; later steps read `Position.getPostingText()` (text + image text).
- **Common questions have no kind (FIXED/COMMON/NOTICE) and sections are user-defined** (`question_section`: create, rename, delete, drag to reorder). Every template question may be personalized by AI ③; unrelated ones stay verbatim. The legacy `question_template.section`/`kind` columns remain only for migrating old data in `data.sql`.
- **Database:** PostgreSQL 17 instead of MySQL. Tests run on H2 in `MODE=PostgreSQL`, so `schema.sql` must stay valid in both.
- **AI provider:** an OpenAI-compatible Chat Completions API instead of the Claude Messages API. The provider is chosen only by `ai.base-url` and `ai.model`.

## Commands

Gradle wrapper; Spring Boot 4.1, Java 17. On Windows use `.\gradlew`.

```
./gradlew test                                   # all tests (H2, no DB server or API key needed)
./gradlew test --tests "*PiiMaskerTest"          # single class
./gradlew test --tests "*PiiMaskerTest.masksEmail"
./gradlew bootRun                                # needs PostgreSQL + secrets below
./gradlew build                                  # executable war -> build/libs/interview-0.0.1-SNAPSHOT.war
```

Secrets come from env vars (`DB_PASSWORD`, `AI_API_KEY` or `OPENAI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`) or from the gitignored `config/application-local.properties`, which `application.properties` imports when present. Never put secrets in tracked files. Optional: `ai.input-price-per1m` / `ai.output-price-per1m` for cost display.

`./gradlew test` writes Korean sample resume PDFs to `build/sample-resumes/` for manual upload testing. `measurement.sql` has the presentation metric queries.

## Architecture

Request flow: `controller/` → `service/` → `mapper/` (MyBatis interfaces + XML in `resources/mapper/`). JSPs live in `src/main/webapp/WEB-INF/views/` and share the `common/header.jspf` / `footer.jspf` static includes. Each page sets `activeTab` before including the header.

**Extract tab flow** (`ExtractController`, `static/js/extract.js`). URLs: `/extract` (new position), `/positions/{id}` (criteria + resume upload), `/results/{id}` (candidate id; evidence + questions).
1. `POST /extract` → `PositionService.create` + `extractCriteria` (AI ①; sets the title from `position_title`, marks CONFIRMED). Criteria CRUD is JSON fetch under `/positions/{id}/criteria`.
2. `POST /positions/{id}/resume` → `ExtractionService.uploadResume`: validates the PDF before any AI call (a bad PDF saves nothing), detects the name, masks PII (only `masked_text` is stored), creates a `candidate`, then `AnalysisService.analyzeEvidence` (AI ② + `QuoteVerifier`; replaces evidence and drops questions) → status ANALYZED. One position has many candidates (the chips in stage ②).
3. `POST /results/{id}/questions/generate` → `AnalysisService.generateQuestions`: two AI calls. ③-1 `QuestionService.generate` (QUESTION, `questions.txt`) extracts criterion-verifying questions with no section or order. ③-2 `QuestionService.arrange` (ARRANGE, `arrange.txt`) gets the common questions by section plus the extracted ones and returns the final sections and order, personalizing common questions on the way; section names may be new. `ArrangeResult.validate` rejects only unknown or duplicate ids; whatever the AI left out is filled in by `QuestionSheetAssembler` next to its neighbors (a common question beside its configured-section neighbor, or as its own section after the previous one; an extracted question after one with the same criterion). Unchanged common questions are returned as `{template_id}` only, to keep the output short (long outputs made the model drop items). Then `QuestionSheetAssembler` → DONE. Question CRUD/reorder is JSON fetch under `/results/{id}/questions`; `reorderQuestions` rejects stale boards.
4. AI failures don't throw to the controller: the candidate becomes FAILED with `error_message`; the stage whose data is missing shows the error and a retry. Criteria added after a check show as "검사 전" (no evidence rows) until re-checked.

Deleting a position cascades its candidates; deleting a candidate keeps the position.

**Common question settings** (`TemplateController` + `static/js/templates.js`): the page renders once, then every edit is a JSON `fetch` (`X-Requested-With: fetch`). Drag-and-drop sends the whole board to `PUT /templates/order`; `TemplateService.reorder` rejects stale boards (every section and question must appear exactly once). AI ③-2 sees the configured sections as a suggested flow but may rename, merge or add sections. `interview_question.section` stores the section *name* at generation time, so later edits don't change past results.

**Result screen and PDF** both come from `extract.jsp` (`ReportService.report(id)` → `ResultReport`). There is no server-side PDF: the PDF button saves pending answers and calls `window.print()`, and `@media print` in `app.css` hides stage ① and all controls so the PDF looks like the screen. Keep layout media queries `screen`-only. Answers autosave via `fetch` to `POST /results/{id}/questions/{qid}/answer` with header `X-Requested-With: fetch`; `GlobalExceptionHandler` returns plain-text errors for those requests instead of redirecting.

**Rules to preserve:**
- **View models used from JSP must be classes with getters, not records.** Tomcat 11's EL exposes only record components, so derived getters like `getMatch()` would be invisible. That's why `ResultReport`, `CriterionEvidenceView`, `SectionView`, and `QuestionView` use Lombok `@Value`.
- **AI calls stay outside DB transactions.** Only DB writes are wrapped in `TransactionTemplate`.
- **`AiClient` is the single entry point for all AI calls.**
  - It strips code fences, parses JSON with Jackson 3 (`tools.jackson.*`), and runs the DTO's `validate`.
  - It retries **exactly once** with the error appended, then throws `AiCallException`.
  - Every attempt is logged to `ai_call_log` via `AiCallLogger`, which uses `REQUIRES_NEW`.
- **Evidence IDs sent to AI ③ are 1-based list positions** in the saved evidence list. `AnalysisService.generateQuestions` maps them back to real IDs. The assembler drops unknown IDs instead of guessing.
- **Question sheet order (initial):** the ③-2 arrangement plus the gap filling above; "기타" is used only when there is no anchor at all. After that the user can reorder freely. `origin=FIXED` only exists in old rows and is shown as 공통; `MANUAL` is a user-added question.
- **Prompts are files** (`resources/prompts/*.txt`), read on every call.
  - Untrusted input is wrapped in tags (`<resume>`, `<job_posting>`, …) and declared as data, not instructions.
  - The candidate's name is never sent to the AI.
- **`PiiMasker` masks birth dates only when labeled.** Career and project dates must survive because they are evidence.
- **JSP output escaping:**
  - Escape all user and AI text with `<c:out>`.
  - Each `.jspf` needs its own `pageEncoding="UTF-8"`.
  - Session tracking is cookie-only, so URLs never get `;jsessionid`.
- **Test properties replace the main ones.** `src/test/resources/application.properties` fully shadows the main file, so add new required properties to both. Schema changes to existing tables go in `schema.sql` as idempotent `ALTER TABLE` statements valid in both PostgreSQL and H2; one-time seed data is guarded by `app_setting('seeded')` so deleted defaults don't come back.

**Out of scope:** login, non-PDF formats, OCR, candidate comparison/scoring, async/batch analysis. Test only with fictional resumes.
