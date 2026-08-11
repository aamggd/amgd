from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')

sales_service = ROOT / 'app/src/main/java/com/fush/erp/domain/SalesService.kt'
sales_math = ROOT / 'app/src/main/java/com/fush/erp/domain/SalesMath.kt'
sales_ui = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt'
build = ROOT / 'app/build.gradle.kts'

text = sales_service.read_text(encoding='utf-8')
old = '''        val cashSales = db.salesDao().successfulCashSales(customer.id)\n        require(cashSales >= SalesMath.REQUIRED_CASH_SALES_BEFORE_CREDIT) { "لا يمنح العميل الجديد أجلاً قبل عمليتين نقديتين ناجحتين" }\n'''
if old not in text:
    raise SystemExit('credit precondition block not found')
text = text.replace(old, '')
sales_service.write_text(text, encoding='utf-8')

text = sales_math.read_text(encoding='utf-8')
text = text.replace('    const val REQUIRED_CASH_SALES_BEFORE_CREDIT = 2\n', '')
sales_math.write_text(text, encoding='utf-8')

text = sales_ui.read_text(encoding='utf-8')
text = text.replace(
    'Text("ضوابط الآجل: عمليتان نقديتان ناجحتان أولاً، مدة قصوى 30 يوماً، وإيقاف تلقائي عند التأخر أو تجاوز السقف.", style = MaterialTheme.typography.bodySmall)',
    'Text("ضوابط الآجل: مدة قصوى 30 يوماً، وإيقاف تلقائي عند التأخر أو تجاوز السقف الائتماني.", style = MaterialTheme.typography.bodySmall)'
)
sales_ui.write_text(text, encoding='utf-8')

text = build.read_text(encoding='utf-8')
text = text.replace('versionCode = 20', 'versionCode = 21')
text = text.replace('versionName = "0.13.2-phase13-material-availability"', 'versionName = "0.13.3-phase13-credit-policy"')
build.write_text(text, encoding='utf-8')

print('Phase 13.3 credit policy patch applied')
