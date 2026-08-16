# FUSH ERP Mobile — Accounting Branch

هذا المجلد مخصص حصريًا لتعديلات الحسابات على الفرع `fush/accounting`. لا يدمج هذا الفرع نفسه مباشرة في `fush/main`؛ المحادثة الرئيسية مسؤولة عن الدمج النهائي وترتيب الـMigrations والإصدار والتوقيع الرسمي.

## Baseline المعتمد
- Phase 14.5.38 Professional UI — من Artifact البناء الرسمي الناجح.
- Application ID: `com.fush.erp.recovery` — لا يتغير.
- Baseline versionCode: `77` — للتحقق داخل الفرع فقط، وليس رقم الإصدار النهائي.
- Baseline versionName: `0.15.4.38-ui-inventory-master-data`.
- Baseline Room Schema: `27`.

## تنبيه دمج مهم
الحزم التاريخية للمراحل 14.5.34–14.5.38 داخل `fush_accounting/patches/` أصبحت **LEGACY / SUPERSEDED** ولا يجوز دمجها مباشرة. المسار المعتمد هو سلسلة الـArtifacts والـPatches التي أعيد تأسيسها واختبارها فوق Professional UI 14.5.38.

## سلسلة الحسابات المعتمدة
1. Accounting Rebase 14.5.38 — Run `31863175468`.
2. Phase 14.5.39 — Foreign Currency Treasury & Revaluation — Run `31865418288`.
3. Phase 14.5.40 — Fixed Asset Accounting — Run `31868347639`.
4. Phase 14.5.41 — Multi-Invoice Settlement — Run `31869145938`.
5. Phase 14.5.42 — Accounting Posting Profiles & COA Safety — Run `31872176530`.
6. Phase 14.5.43 — Accounting Idempotency & Duplicate-Posting Protection — Run `31912860098`.
7. Phase 14.5.44 — Professional Financial Statements & Cash Flow — Run `31914408158`.
8. Phase 14.5.45 — General Ledger Dimensions & Cost Centers — Run `31915400563`.
9. Phase 14.5.46 — Tax / VAT Accounting Framework — Run `31916655331`.

## الوظائف المحاسبية المعتمدة

### Accounting Integrity
- حماية حسابات الرقابة من القيود اليدوية المباشرة.
- تسويات العملاء والموردين مرتبطة بالفاتورة.
- فروق العملة الصحيحة لتحصيلات العملاء.
- اختيار الخزينة/البنك الفعلي بدل حساب نقدي ثابت.

### Accounting Period Control & Reconciliation
- فترات محاسبية وإقفال/إعادة فتح موثق.
- منع الترحيل داخل فترة مقفلة.
- مطابقة الأستاذ مع العملاء والموردين والمخزون وميزان المراجعة.
- منع الإقفال عند وجود فرق غير مسوّى.

### Operational Reversals / Fiscal Year / Treasury
- عكس موثق لتحصيلات العملاء ودفعات الموردين دون حذف الأصل.
- إقفال وإعادة فتح السنة بقيد موثق وترحيل النتيجة للأرباح المحتجزة.
- جرد الصندوق، المطابقة البنكية، ومنع الإقفال عند وجود فروق غير مسوّاة.

### Phase 14.5.39 — Foreign Currency Treasury & Revaluation
- فصل رصيد العملة الأصلية عن قيمتها الدفترية بالعملة الأساسية.
- جرد ومطابقة العملات الأجنبية بالعملة الأصلية وإعادة تقييم نهاية الفترة.

### Phase 14.5.40 — Fixed Asset Accounting
- دفتر مالي للأصول الثابتة: اقتناء، إهلاك، قيمة متبقية، استبعاد/بيع وعكس موثق.
- مطابقة تكلفة الأصول ومجمع الإهلاك ومنع الإقفال عند وجود إهلاك مستحق.

### Phase 14.5.41 — Multi-Invoice Settlement
- تحصيل عميل أو دفعة مورد واحدة موزعة على عدة فواتير بنفس العملة عبر FIFO.
- تسوية كل فاتورة بسعرها التاريخي وتجميع فرق العملة المحقق.

### Phase 14.5.42 — Accounting Posting Profiles & COA Safety
- استبدال أكواد الحسابات الثابتة بـ`PostingRole` مركزي مرتبط بـ`accountId`.
- Triggers تمنع حذف/تعطيل/تغيير نوع حساب مستخدم في الترحيل.

### Phase 14.5.43 — Accounting Idempotency & Duplicate-Posting Protection
- `postingKey` فريد للأحداث المحاسبية الآلية ذات الهوية الثابتة.
- إعادة التنفيذ تعيد نفس القيد، وPayload مختلف لنفس المفتاح يُرفض.
- التكرارات التاريخية القديمة لا تُحذف.

### Phase 14.5.44 — Professional Financial Statements & Cash Flow
- قائمة دخل مقارنة، قائمة مركز مالي مصنفة، وتدفقات نقدية مباشرة تشغيلية/استثمارية/تمويلية.
- استبعاد التحويلات الداخلية وفصل أثر إعادة تقييم العملة عن المقبوضات والمدفوعات.
- لا Migration جديدة؛ Schema بقي `35`.

### Phase 14.5.45 — General Ledger Dimensions & Cost Centers
- تحويل مركز التكلفة من قائمة ثابتة خاصة بالمصروفات إلى بُعد محاسبي عام على مستوى **سطر القيد**.
- أبعاد افتراضية قابلة للتوسع: `COST_CENTER`, `DEPARTMENT`, `PROJECT`, `BRANCH`.
- جدول قيم عام لكل بُعد وجدول ربط `journal_line_dimensions` يسمح بعدة أبعاد مختلفة للسطر نفسه.
- ترحيل مراكز تكلفة المصروفات القديمة وعمل Backfill للتصنيف التاريخي.
- منع تغيير الأبعاد داخل فترة `CLOSED`.
- Validation Run: `31915400563`.
- Patch SHA-256: `ad48cca35d3a27675b0d0a8877d13469d6d451e338f597c3889c2e19e9021365`.

### Phase 14.5.46 — Tax / VAT Accounting Framework
- إطار ضريبي قابل للتهيئة بدون أي نسبة قانونية افتراضية أو تفعيل ضريبة تلقائي.
- Tax Codes للنطاق `SALES / PURCHASE / BOTH` مع نسبة صريحة وحالة قابلية استرداد ضريبة المشتريات.
- حفظ لقطة الرمز والنسبة والوعاء والضريبة على الفاتورة/المرتجع حتى لا تتغير المستندات التاريخية عند تعديل الإعداد لاحقًا.
- `1400 — VAT_INPUT_RECEIVABLE` كحساب رقابة أصل ضريبي قابل للاسترداد.
- `2410 — VAT_OUTPUT_PAYABLE` كحساب رقابة التزام ضريبة مخرجات.
- المبيعات: النقد/الذمة بالإجمالي شامل الضريبة، الإيراد قبل الضريبة، والضريبة في حساب المخرجات.
- المشتريات القابلة للاسترداد: المخزون قبل الضريبة + أصل VAT Input؛ غير القابلة للاسترداد تُرسمل ضمن تكلفة المخزون.
- المرتجعات تعكس نسبة الضريبة التاريخية المخزنة في الفاتورة.
- عمولة المندوب تعتمد على المبلغ قبل الضريبة.
- سجل `tax_ledger_entries` وتقرير للمدخلات/المخرجات وصافي الضريبة.
- إقفال الفترة يطابق حسابي VAT مع سجل الضريبة ويُرفض عند وجود فرق.
- حسابا VAT محميان من القيود اليدوية المباشرة.
- تصحيح اختبار Posting Profiles لتمييز 4 حسابات رقابة للأطراف عن 2 حسابات رقابة تنظيمية للضريبة.
- Validation Run: `31916655331`.
- Canonical Patch SHA-256: `0257991ee9c00e51d00c2117426d60a866e19d6e1cadc0d97b2baa4aeb3a1afa`.

## Room Schema — أرقام الفرع مؤقتة فقط
- `27 -> 28`: Accounting periods/reconciliation.
- `28 -> 29`: Operational reversals.
- `29 -> 30`: Fiscal-year closing.
- `30 -> 31`: Treasury/bank reconciliation.
- `31 -> 32`: Foreign-currency treasury/revaluation.
- `32 -> 33`: Fixed-asset accounting.
- Phase 14.5.41: no migration؛ يبقى `33`.
- `33 -> 34`: Posting profiles / COA safety.
- `34 -> 35`: Accounting idempotency / postingKey protection.
- Phase 14.5.44: no migration؛ يبقى `35`.
- `35 -> 36`: General ledger dimensions / cost centers.
- `36 -> 37`: Tax / VAT accounting framework.

هذه الأرقام **BRANCH ONLY / PROVISIONAL**. المحادثة الرئيسية تعيد ترقيمها عند الدمج إذا حجزت فروع أخرى نفس الأرقام، مع الحفاظ على SQL والبيانات وعدم استخدام destructive migration.

## بوابة التحقق الأحدث
Phase 14.5.46 — Run `31916655331`:
- Original patch chunk integrity / SHA-256 / GZIP: PASS.
- Reviewed regulatory-control test correction: PASS.
- Canonical final patch integrity: PASS.
- Apply to validated Phase 14.5.45: PASS.
- Application ID / baseline identity guard: PASS.
- Migration `36 -> 37`: PASS.
- No destructive migration: PASS.
- Tax/VAT wiring and regulatory control-account wiring: PASS.
- SQLite tax control: PASS.
- Unit Tests: PASS.
- Release Build: PASS.
- Room Schema 37 generation: PASS.
- Zipalign: PASS.
- Artifact upload: PASS.

التوقيع الرسمي **غير منفذ داخل GitHub CI**؛ لا يتم تخزين مفتاح التوقيع أو كلمة مروره في GitHub أو المصدر أو Workflow.

## مراجع المرحلة الحالية
- `fush_accounting/PHASE14_5_46_SCOPE.md`
- `fush_accounting/PHASE14_5_46_VALIDATION.md`
- `.github/workflows/build-accounting-phase14.5.46-v3.yml`
