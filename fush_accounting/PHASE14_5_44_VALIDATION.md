# Phase 14.5.44 — Validation Record

## الحالة
**VALIDATED / PASS**

## GitHub Actions
- Workflow: `.github/workflows/build-accounting-phase14.5.44.yml`
- Successful Run ID: `31914408158`
- Validated head SHA: `d87eabf04d212dbd506f4c79ee5106506a9e060a`
- Artifact ID: `9254595873`
- Artifact: `FushERP-Accounting-Phase14.5.44-Professional-Financial-Statements-Validated-Build`

## النتائج
- Patch chunk integrity / SHA-256: PASS
- GZIP integrity: PASS
- Apply to validated Phase 14.5.43 source: PASS
- `git diff --check`: PASS
- Application ID guard `com.fush.erp.recovery`: PASS
- Baseline versionCode/versionName guard: PASS
- Room Schema remains `35`: PASS
- No Migration `35 -> 36`: PASS
- No destructive migration guard: PASS
- Professional financial statement wiring: PASS
- Professional financial-statement math smoke: PASS (local pre-CI)
- Unit Tests: **PASS**
- Release Build: **PASS**
- Room Schema 35 generation: **PASS**
- Zipalign: **PASS**
- Artifact upload: **PASS**

## حالات قبول محاسبية
1. مبيعات 1,000، مرتجعات 100، تكلفة مبيعات 500، مصروف تشغيلي 100، ربح فرق عملة 20 وخسارة فرق عملة 10 => صافي الربح 310.
2. أصول ثابتة 1,000 ومجمع إهلاك 200 => صافي الأصول غير المتداولة 800.
3. تدفق تشغيلي +900، استثماري -300، أثر إعادة تقييم عملة +50 => رصيد الإقفال 650؛ والـ50 لا تظهر كمتحصل نقدي.
4. التحويل بين خزانتين لا يظهر كتدفق تشغيلي/استثماري/تمويلي.
5. الخزينة غير النشطة تاريخيًا تبقى ضمن أرصدة القوائم التاريخية.
6. الحسابات غير المصنفة تظهر بتحذير واضح ولا يتم تخمين تصنيفها.

## البصمات
- Phase 14.5.44 patch SHA-256:
  `74deb3e42b51f73bdec431645c9271e85e3d5717643e35dfd94b605124b6e238`
- Aligned unsigned APK SHA-256:
  `765c642c7a7ed30239d66c61efe39d04d17c1263babb1674c88cc9ae6cfe7d53`
- Full source ZIP SHA-256:
  `34bd54aec6bf570fc048dd13080b7c994f5ee11de6798f283e2cd8e9ab1419f6`

## التوقيع
الـAPK الناتج **aligned unsigned**. لا يتم تخزين مفتاح التوقيع الدائم أو كلمة مروره في GitHub أو Workflow؛ التوقيع الرسمي يتم فقط في بوابة الإصدار.

`PHASE14_5_44_PROFESSIONAL_FINANCIAL_STATEMENTS_VALIDATED_OK`
