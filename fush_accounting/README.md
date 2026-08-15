# FUSH ERP Mobile — Accounting Branch

هذا المجلد مخصص حصريًا لتعديلات الحسابات على الفرع `fush/accounting`. لا يدمج هذا الفرع نفسه مباشرة في `fush/main`؛ المحادثة الرئيسية مسؤولة عن الدمج النهائي وترتيب الـMigrations والإصدار والتوقيع الرسمي.

## Baseline المعتمد
- Phase 14.5.38 Professional UI — من Artifact البناء الرسمي الناجح.
- Application ID: `com.fush.erp.recovery` — لا يتغير.
- Baseline versionCode: `77` — مستخدم للتحقق داخل الفرع فقط، وليس رقم إصدار نهائي جديد.
- Baseline versionName: `0.15.4.38-ui-inventory-master-data`.
- Baseline Room Schema: `27`.

## تنبيه دمج مهم
المجلد `fush_accounting/patches/` يحتوي الحزم التاريخية للمراحل 14.5.34–14.5.38. هذه الحزم أصبحت **LEGACY / SUPERSEDED** ولا يجوز للمحادثة الرئيسية دمجها مباشرة. بعض الحزم القديمة أثبتت فشل CRC/صيغة عند إعادة التحقق، كما أنها مبنية على lineage أقدم من Professional UI 14.5.38.

المسار المعتمد هو سلسلة البناء المختبرة فوق الـBaseline الرسمي:
1. Accounting Rebase 14.5.38.
2. Phase 14.5.39 — Foreign Currency Treasury & Revaluation.
3. Phase 14.5.40 — Fixed Asset Accounting.
4. Phase 14.5.41 — Multi-Invoice Settlement.

### Accounting Rebase الأساسي
- Workflow: `.github/workflows/build-accounting-rebase-14.5.38.yml`
- Successful Run ID: `31863175468`
- Artifact: `FushERP-Accounting-Rebase-14.5.38-Validated-Build`
- Clean patch SHA-256: `3c1984f84f0f48c90f1adf5b1683185e62520f90512689befe0bed4ce1060dd6`

## الوظائف المحاسبية المعتمدة
1. **Accounting Integrity**
   - حماية حسابات الرقابة من القيود اليدوية المباشرة.
   - تسويات العملاء والموردين مرتبطة بالفاتورة.
   - إصلاح فروق العملة لتحصيل العملاء.
   - اختيار الخزينة/البنك الفعلي بدل الحساب النقدي الثابت.
   - منع اختلاف عملة الخزينة عن عملة العملية.
   - تصحيح تاريخ قيود عمولة المندوب.

2. **Accounting Period Control & Reconciliation**
   - فترات محاسبية وإقفال/إعادة فتح موثق.
   - منع الترحيل داخل فترة مقفلة.
   - مطابقة الأستاذ مع العملاء والموردين والمخزون وميزان المراجعة.
   - منع الإقفال عند وجود فرق محاسبي غير مسوّى.

3. **Operational Reversals**
   - عكس موثق لتحصيلات العملاء ودفعات الموردين دون حذف المستند الأصلي.
   - إعادة فتح ذمة الفاتورة عبر تخصيص عكسي.
   - عكس القيد الأصلي بما فيه الخزينة وفروق العملة.
   - إعادة احتساب عمولة المندوب.

4. **Fiscal Year Closing**
   - إقفال السنة وترحيل الربح/الخسارة إلى `3300 — الأرباح المحتجزة`.
   - إعادة فتح السنة بقيد عكسي موثق.
   - إبقاء قائمة الدخل التاريخية صحيحة بعد الإقفال.

5. **Treasury & Bank Reconciliation**
   - جرد الصندوق وفروق النقدية.
   - حساب `6950 — فروقات الصندوق` لمسار التسوية الموثق.
   - كشوف بنكية ومطابقة الحركات والـOutstanding items.
   - منع إقفال الفترة عند نقص الجرد أو وجود فرق غير مسوّى أو مطابقة بنكية غير مكتملة.

6. **Foreign Currency Treasury & Revaluation — Phase 14.5.39**
   - فصل رصيد العملة الأصلية عن قيمتها الدفترية بالعملة الأساسية.
   - جرد ومطابقة الخزائن والبنوك بالعملة الأصلية.
   - إعادة تقييم أرصدة العملات في نهاية الفترة دون تغيير كمية العملة الأصلية.
   - ترحيل فروق التقييم وربطها بإقفال الفترة.
   - Successful validation run: `31865418288`.

7. **Fixed Asset Accounting — Phase 14.5.40**
   - دفتر مالي مستقل للأصول الثابتة مع ربط اختياري بسجل الصيانة التشغيلي.
   - اقتناء من خزينة/بنك أو كرصد افتتاحي منضبط.
   - إهلاك خط مستقيم شهريًا، قيمة متبقية، عمر إنتاجي، وصافي قيمة دفترية.
   - استبعاد/بيع الأصل وربح/خسارة الاستبعاد.
   - عكس الإهلاك والاستبعاد وإلغاء الاقتناء بقيود موثقة بدل الحذف.
   - مطابقة `1500` و`1590` مع دفتر الأصول ومنع الإقفال عند وجود إهلاك مستحق أو فرق.
   - Successful validation run: `31868347639`.

8. **Multi-Invoice Settlement — Phase 14.5.41**
   - تحصيل عميل واحد يمكن توزيعه على عدة فواتير مفتوحة بنفس العملة.
   - دفعة مورد واحدة يمكن توزيعها على عدة فواتير شراء مفتوحة بنفس العملة.
   - Auto Allocation حسب الأقدم FIFO مع عدم تجاوز رصيد أي فاتورة.
   - كل فاتورة تُسوى بسعرها التاريخي الخاص، وحركة الخزينة بسعر الحركة الحالي، مع تجميع فرق العملة المحقق.
   - احتساب عمولة المندوب لكل تخصيص عند تحصيل العميل.
   - الاحتفاظ بخيار تسوية فاتورة واحدة.
   - لا توجد Migration جديدة؛ Schema يبقى `33`.
   - Successful validation run: `31869145938`.
   - Patch SHA-256: `069b0a847e3b42244b04b11417280058286b5581a1b9b31448e14b41ab304f0b`.

## Room Schema — أرقام الفرع مؤقتة فقط
السلسلة المستقلة الحالية تستخدم:
- `27 -> 28`: Accounting periods/reconciliation.
- `28 -> 29`: Operational reversals.
- `29 -> 30`: Fiscal-year closing.
- `30 -> 31`: Treasury/bank reconciliation.
- `31 -> 32`: Foreign-currency treasury/revaluation.
- `32 -> 33`: Fixed-asset accounting.
- Phase 14.5.41: **no migration**؛ يبقى Schema `33`.

هذه الأرقام **BRANCH ONLY / PROVISIONAL** وليست أرقام الـSchema النهائية للمشروع الموحد. المحادثة الرئيسية تعيد ترقيمها عند الدمج إذا حجزت فروع أخرى نفس الأرقام، مع الحفاظ على نفس SQL والبيانات وعدم استخدام destructive migration.

## بوابة التحقق الأحدث
Phase 14.5.41 — Run `31869145938` نجح في:
- Patch integrity / SHA-256 / GZIP verification: PASS.
- Apply to validated Phase 14.5.40 source: PASS.
- Application ID / baseline identity guard: PASS.
- Schema remains 33 / no new migration: PASS.
- Unit Tests: PASS.
- Release Build: PASS.
- Room Schema 33 verification: PASS.
- Zipalign: PASS.
- Artifact upload: PASS.

التوقيع الرسمي **لم يُنفذ داخل GitHub CI** لأن مفتاح التوقيع وكلمة مروره لا يجوز تخزينهما في GitHub أو المصدر أو Workflow. التوقيع النهائي يتم فقط في بوابة الإصدار بالمفتاح الدائم للمشروع.

راجع:
- `fush_accounting/PHASE14_5_40_SCOPE.md`
- `fush_accounting/PHASE14_5_40_VALIDATION.md`
- `fush_accounting/PHASE14_5_41_SCOPE.md`
- `fush_accounting/PHASE14_5_41_VALIDATION.md`
- `fush_accounting/rebase/VALIDATION_STATUS.md`
