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

## الوظائف المحاسبية المعتمدة

### Accounting Integrity
- حماية حسابات الرقابة من القيود اليدوية المباشرة.
- تسويات العملاء والموردين مرتبطة بالفاتورة.
- فروق العملة الصحيحة لتحصيلات العملاء.
- اختيار الخزينة/البنك الفعلي بدل حساب نقدي ثابت.
- تصحيح تاريخ قيود عمولة المندوب.

### Accounting Period Control & Reconciliation
- فترات محاسبية وإقفال/إعادة فتح موثق.
- منع الترحيل داخل فترة مقفلة.
- مطابقة الأستاذ مع العملاء والموردين والمخزون وميزان المراجعة.
- منع الإقفال عند وجود فرق غير مسوّى.

### Operational Reversals
- عكس موثق لتحصيلات العملاء ودفعات الموردين دون حذف المستند الأصلي.
- إعادة فتح ذمة الفاتورة وعكس الخزينة وفروق العملة والعمولة عند الحاجة.

### Fiscal Year Closing
- إقفال السنة وترحيل الربح/الخسارة إلى الأرباح المحتجزة.
- إعادة فتح السنة بقيد عكسي موثق.
- إبقاء قائمة الدخل التاريخية صحيحة بعد الإقفال.

### Treasury & Bank Reconciliation
- جرد الصندوق وفروق النقدية.
- كشوف ومطابقة بنكية وحركات معلقة.
- منع الإقفال عند نقص الجرد أو وجود فرق أو مطابقة غير مكتملة.

### Phase 14.5.39 — Foreign Currency Treasury & Revaluation
- فصل رصيد العملة الأصلية عن قيمتها الدفترية بالعملة الأساسية.
- جرد ومطابقة العملات الأجنبية بالعملة الأصلية.
- إعادة تقييم نهاية الفترة دون تغيير كمية العملة الأصلية.

### Phase 14.5.40 — Fixed Asset Accounting
- دفتر مالي للأصول الثابتة.
- اقتناء، إهلاك خط مستقيم، قيمة متبقية، استبعاد/بيع وعكس موثق.
- مطابقة حسابات تكلفة الأصول ومجمع الإهلاك ومنع الإقفال عند وجود إهلاك مستحق.

### Phase 14.5.41 — Multi-Invoice Settlement
- تحصيل عميل واحد أو دفعة مورد واحدة يمكن توزيعها على عدة فواتير بنفس العملة.
- FIFO Auto Allocation مع عدم تجاوز رصيد أي فاتورة.
- تسوية كل فاتورة بسعرها التاريخي وتجميع فرق العملة المحقق.
- Schema بقي `33` دون Migration جديدة.

### Phase 14.5.42 — Accounting Posting Profiles & COA Safety
- استبدال أكواد الحسابات الثابتة بـ`PostingRole` مركزي مرتبط بـ`accountId`.
- إعدادات ترحيل قابلة للإدارة مع تحقق نوع الحساب وحالته.
- Triggers تمنع حذف/تعطيل/تغيير نوع حساب مستخدم في الترحيل.
- Validation Run: `31872176530`.

### Phase 14.5.43 — Accounting Idempotency & Duplicate-Posting Protection
- إضافة `postingKey` فريد للأحداث المحاسبية الآلية ذات الهوية الثابتة.
- إعادة تنفيذ نفس الحدث تعيد نفس القيد بدل إنشاء قيد مكرر.
- رفض استخدام نفس المفتاح مع Payload مختلف (`POSTING_KEY_PAYLOAD_MISMATCH`).
- حماية المبيعات والتحصيلات والمرتجعات والمشتريات ودفعات الموردين والجرد وحركات الإنتاج الأساسية والإهلاك وفروق العملة والعكس.
- Migration التاريخية تملأ المفاتيح فقط للأزواج الفريدة؛ التكرارات القديمة تبقى `NULL` للمراجعة ولا تُحذف.
- Trigger يمنع تغيير هوية مصدر قيد `POSTED`.
- Validation Run: `31912860098`.
- Patch SHA-256: `4aeb462acf93cf177b2e8c1ec1a0d41956d3419a9bf13a540da04f2607815a52`.

### Phase 14.5.44 — Professional Financial Statements & Cash Flow
- قائمة دخل احترافية مقارنة: إجمالي المبيعات، المرتجعات، صافي الإيراد، تكلفة المبيعات، مجمل الربح، المصروفات التشغيلية، الربح التشغيلي، الإيرادات/المصروفات الأخرى وصافي الربح.
- قائمة مركز مالي مصنفة مع إظهار مجمع الإهلاك كحساب مقابل للأصل، وفصل البنود غير المصنفة بدل تخمينها.
- قائمة تدفقات نقدية مصنفة بالطريقة المباشرة إلى تشغيلية/استثمارية/تمويلية.
- استبعاد التحويلات الداخلية بين الخزائن من التدفق النقدي الحقيقي.
- فصل أثر إعادة تقييم العملة عن المقبوضات والمدفوعات وإظهاره كـ«أثر تغير أسعار الصرف على النقد».
- مقارنة تلقائية مع الفترة السابقة المماثلة.
- الخزائن غير النشطة تبقى ضمن القوائم التاريخية.
- تحديث شاشة الحسابات وتقرير Finance ومخرجات الطباعة/PDF بالقوائم الجديدة.
- لا توجد Migration جديدة؛ Schema يبقى `35`.
- Validation Run: `31914408158`.
- Patch SHA-256: `74deb3e42b51f73bdec431645c9271e85e3d5717643e35dfd94b605124b6e238`.

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
- Phase 14.5.44: **no migration**؛ يبقى Schema `35`.

هذه الأرقام **BRANCH ONLY / PROVISIONAL**. المحادثة الرئيسية تعيد ترقيمها عند الدمج إذا حجزت فروع أخرى نفس الأرقام، مع الحفاظ على SQL والبيانات وعدم استخدام destructive migration.

## بوابة التحقق الأحدث
Phase 14.5.44 — Run `31914408158`:
- Patch chunk integrity / SHA-256 / GZIP: PASS.
- Apply to validated Phase 14.5.43: PASS.
- Application ID / baseline identity guard: PASS.
- Schema remains `35` / no Migration `35 -> 36`: PASS.
- No destructive migration: PASS.
- Professional financial statement wiring: PASS.
- Unit Tests: PASS.
- Release Build: PASS.
- Room Schema 35 generation: PASS.
- Zipalign: PASS.
- Artifact upload: PASS.

التوقيع الرسمي **غير منفذ داخل GitHub CI**؛ لا يتم تخزين مفتاح التوقيع أو كلمة مروره في GitHub أو المصدر أو Workflow.

## مراجع المرحلة الحالية
- `fush_accounting/PHASE14_5_44_SCOPE.md`
- `fush_accounting/PHASE14_5_44_VALIDATION.md`
- `.github/workflows/build-accounting-phase14.5.44.yml`
