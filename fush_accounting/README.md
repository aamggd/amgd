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

**المصدر الوحيد المعتمد للدمج الآن** هو Accounting Rebase المختبر فوق الـBaseline الرسمي 14.5.38، الناتج من Workflow:
- Workflow: `.github/workflows/build-accounting-rebase-14.5.38.yml`
- Successful Run ID: `31863175468`
- Validated head SHA: `d15ddd966599aa80ef954f0e091d64e94c48b92c`
- Artifact: `FushERP-Accounting-Rebase-14.5.38-Validated-Build`
- Clean patch SHA-256: `3c1984f84f0f48c90f1adf5b1683185e62520f90512689befe0bed4ce1060dd6`

الـPatch الموثق مخزن على GitHub على خمس قطع داخل `fush_accounting/rebase/patch_chunks/`. الـWorkflow يعيد تجميعها ويتحقق من أحجامها وبصماتها وGZIP قبل التطبيق، ثم يطبقها على المصدر الرسمي فقط.

## الوظائف المحاسبية الموجودة في الـRebase
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

## Room Schema — أرقام الفرع مؤقتة فقط
الـRebase المستقل يستخدم حاليًا:
- `27 -> 28`: Accounting periods/reconciliation.
- `28 -> 29`: Operational reversals.
- `29 -> 30`: Fiscal-year closing.
- `30 -> 31`: Treasury/bank reconciliation.

هذه الأرقام **BRANCH ONLY / PROVISIONAL** وليست أرقام الـSchema النهائية للمشروع الموحد.

إذا كان دمج المستخدمين والصلاحيات في النسخة الرئيسية يشغل `28 -> 29`، فيجب على المحادثة الرئيسية إعادة ترقيم تغييرات الحسابات اللاحقة مع الحفاظ على نفس SQL والبيانات، مثل:
- `27 -> 28`: Accounting periods.
- `28 -> 29`: Users/security.
- `29 -> 30`: Operational reversals.
- `30 -> 31`: Fiscal-year closing.
- `31 -> 32`: Treasury/bank reconciliation.

## بوابة التحقق الحالية
Run `31863175468` نجح في:
- Patch integrity / SHA-256 / GZIP verification: PASS.
- Apply to official Phase 14.5.38 Professional UI: PASS.
- Application ID / baseline identity check: PASS.
- Migration safety / no destructive migration: PASS.
- Unit Tests: PASS.
- Release Build: PASS.
- Room Schema generation: PASS.
- Zipalign: PASS.

التوقيع الرسمي **لم يُنفذ داخل GitHub CI** لأن مفتاح التوقيع وكلمة مروره لا يجوز تخزينهما في GitHub أو المصدر أو Workflow. التوقيع النهائي يتم فقط في بوابة الإصدار بالمفتاح الدائم للمشروع.

انظر أيضًا: `fush_accounting/rebase/VALIDATION_STATUS.md`.
