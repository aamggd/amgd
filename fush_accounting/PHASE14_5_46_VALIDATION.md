# Phase 14.5.46 — Validation Record

## الحالة
**VALIDATED / PASS**

## GitHub Actions
- Workflow: `.github/workflows/build-accounting-phase14.5.46-v3.yml`
- Successful Run ID: `31916655331`
- Validated head SHA: `726d2f1f921bbef6397b99666059492c82905aac`
- Artifact ID: `9255156902`
- Artifact: `FushERP-Accounting-Phase14.5.46-Tax-VAT-Framework-Validated-Build`

## النتائج
- Original patch chunk integrity / SHA-256: PASS
- Reviewed regulatory-control test correction: PASS
- Canonical final patch integrity: PASS
- Apply to validated Phase 14.5.45 source: PASS
- `git diff --check`: PASS
- Application ID guard `com.fush.erp.recovery`: PASS
- Baseline versionCode/versionName guard: PASS
- Migration `36 -> 37`: PASS
- No destructive migration guard: PASS
- Tax/VAT entity/DAO/service wiring: PASS
- Sales/purchase/return tax wiring: PASS
- VAT input/output posting roles and control-account wiring: PASS
- VAT GL vs tax-subledger reconciliation wiring: PASS
- SQLite tax duplicate-source control smoke: PASS
- Unit Tests: **PASS**
- Release Build: **PASS**
- Room Schema 37 generation: **PASS**
- Zipalign: **PASS**
- Artifact upload: **PASS**

## البصمات
- Canonical Phase 14.5.46 patch SHA-256:
  `0257991ee9c00e51d00c2117426d60a866e19d6e1cadc0d97b2baa4aeb3a1afa`
- Aligned unsigned APK SHA-256:
  `39b953e5783b1f9ecadeeca7950af244ae8aa3d3199f43b354aa927350071d61`
- Full source ZIP SHA-256:
  `4045b1fc51053b6ec3a19568004a20ad98af0d6e8b763fbbfb0e179d30fcb48f`

## مبدأ قانوني
لا توجد نسبة ضريبية افتراضية في النظام. Tax Codes والنسب يتم إعدادها صراحةً وفق المتطلبات القانونية الفعلية للمنشأة. هذا التحقق تقني/محاسبي ولا يقرر التسجيل الضريبي أو النسبة القانونية.

## ملاحظة حسابات الرقابة
أصبح النظام يميز بين:
- 4 حسابات رقابة مرتبطة بدفاتر أطراف: CUSTOMER / SUPPLIER / EMPLOYEE / SALES_REP.
- 2 حسابات رقابة تنظيمية للضريبة: VAT_INPUT_RECEIVABLE / VAT_OUTPUT_PAYABLE، ولا تحتاج `partyType`.

## التوقيع
الـAPK الناتج **aligned unsigned**. مفتاح التوقيع الدائم وكلمة مروره غير موجودين في GitHub أو Workflow. التوقيع الرسمي يتم فقط في بوابة الإصدار.

`PHASE14_5_46_TAX_VAT_ACCOUNTING_VALIDATED_OK`
