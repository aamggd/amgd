from pathlib import Path

p = Path('FushERP_Mobile_Phase5/app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt')
text = p.read_text(encoding='utf-8')
old = '''    closeNc?.let { nc ->
        CloseNonConformanceDialog(nc, onDismiss = { closeNc = null }) { root, corrective, preventive ->
            scope.launch {
                try {
                    container.productionService.closeNonConformance(nc.id, root, corrective, preventive)
                    message = "تم إغلاق ${nc.code} بعد التحقق"
                    closeNc = null; reload()
                } catch (e: Exception) { message = e.message }
            }
        }
    }
'''
new = '''    closeNc?.let { nc ->
        CloseCapaDialog(nc, onDismiss = { closeNc = null }) { rootCause, corrective, preventive, verified ->
            scope.launch {
                try {
                    container.productionService.closeNonConformance(nc.id, rootCause, corrective, preventive, verified)
                    message = "تم إغلاق ${nc.code} بعد التحقق من الفعالية"
                    closeNc = null; reload()
                } catch (e: Exception) { message = e.message }
            }
        }
    }
'''
if old not in text:
    raise SystemExit('expected Phase 14.5.14 CAPA block not found')
p.write_text(text.replace(old, new, 1), encoding='utf-8')
print('Phase 14.5.14 CAPA compile fix applied')
