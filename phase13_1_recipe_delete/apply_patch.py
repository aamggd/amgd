from pathlib import Path

ROOT = Path("FushERP_Mobile_Phase5")

# Version bump.
gradle = ROOT / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
text = text.replace('versionCode = 18', 'versionCode = 19')
text = text.replace('versionName = "0.13.0-phase13-dashboard"', 'versionName = "0.13.1-phase13-recipe-delete"')
if 'versionCode = 19' not in text or '0.13.1-phase13-recipe-delete' not in text:
    raise SystemExit('Phase 13.1 version bump failed')
gradle.write_text(text, encoding="utf-8")

# Recipe DAO: allow deletion only after checking production order history.
dao = ROOT / "app/src/main/java/com/fush/erp/data/dao/ProductionDaos.kt"
text = dao.read_text(encoding="utf-8")
marker = '''    @Query("SELECT COUNT(*) FROM recipes")\n    suspend fun count(): Int\n'''
replacement = '''    @Query("SELECT COUNT(*) FROM production_orders WHERE recipeId = :recipeId")\n    suspend fun productionOrderCount(recipeId: Long): Int\n\n    @Query("DELETE FROM recipes WHERE id = :recipeId")\n    suspend fun deleteById(recipeId: Long): Int\n\n    @Query("SELECT COUNT(*) FROM recipes")\n    suspend fun count(): Int\n'''
if marker not in text:
    raise SystemExit('RecipeDao count marker not found')
text = text.replace(marker, replacement, 1)
dao.write_text(text, encoding="utf-8")

# Domain service: safe delete. Recipe components cascade from recipes; production orders RESTRICT recipes.
service = ROOT / "app/src/main/java/com/fush/erp/domain/ProductionService.kt"
text = service.read_text(encoding="utf-8")
marker = '''    suspend fun createRecipe(\n'''
method = '''    suspend fun deleteRecipe(recipeId: Long) = db.withTransaction {\n        val recipe = requireNotNull(db.recipeDao().byId(recipeId)) { "الوصفة غير موجودة" }\n        val usedByOrders = db.recipeDao().productionOrderCount(recipeId)\n        require(usedByOrders == 0) {\n            "لا يمكن حذف الوصفة ${recipe.code} إصدار ${recipe.versionNo} لأنها مستخدمة في $usedByOrders أمر إنتاج. يجب الاحتفاظ بها للتتبع والتكلفة."\n        }\n        val deleted = db.recipeDao().deleteById(recipeId)\n        check(deleted == 1) { "تعذر حذف الوصفة" }\n    }\n\n\n'''
if marker not in text:
    raise SystemExit('createRecipe marker not found')
text = text.replace(marker, method + marker, 1)
service.write_text(text, encoding="utf-8")

# UI: delete button + confirmation dialog.
screen = ROOT / "app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt"
text = screen.read_text(encoding="utf-8")
text = text.replace(
'''    var versionRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var message by remember { mutableStateOf<String?>(null) }\n''',
'''    var versionRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var message by remember { mutableStateOf<String?>(null) }\n''',
1
)

old_card = '''                    if (recipe.status == "ACTIVE") {\n                        OutlinedButton(onClick = { versionRecipe = recipe }) { Text("إنشاء إصدار جديد") }\n                    }\n'''
new_card = '''                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                        if (recipe.status == "ACTIVE") {\n                            OutlinedButton(onClick = { versionRecipe = recipe }) { Text("إنشاء إصدار جديد") }\n                        }\n                        TextButton(onClick = { deleteRecipe = recipe }) {\n                            Text("حذف", color = MaterialTheme.colorScheme.error)\n                        }\n                    }\n'''
if old_card not in text:
    raise SystemExit('Recipe card action marker not found')
text = text.replace(old_card, new_card, 1)

insert_before = '''    versionRecipe?.let { recipe ->\n'''
dialog = '''    deleteRecipe?.let { recipe ->\n        AlertDialog(\n            onDismissRequest = { deleteRecipe = null },\n            title = { Text("حذف الوصفة") },\n            text = {\n                Text(\n                    "هل تريد حذف ${recipe.productName} — إصدار ${recipe.versionNo}؟ " +\n                        "إذا كانت الوصفة مستخدمة في أي أمر إنتاج فلن يسمح النظام بحذفها حفاظًا على التتبع والتكلفة."\n                )\n            },\n            confirmButton = {\n                Button(\n                    onClick = {\n                        scope.launch {\n                            try {\n                                container.productionService.deleteRecipe(recipe.id)\n                                message = "تم حذف الوصفة ${recipe.code} إصدار ${recipe.versionNo}"\n                                deleteRecipe = null\n                            } catch (e: Exception) {\n                                message = e.message ?: "تعذر حذف الوصفة"\n                                deleteRecipe = null\n                            }\n                        }\n                    }\n                ) { Text("حذف نهائي") }\n            },\n            dismissButton = {\n                TextButton(onClick = { deleteRecipe = null }) { Text("إلغاء") }\n            }\n        )\n    }\n\n'''
if insert_before not in text:
    raise SystemExit('Recipe version dialog marker not found')
text = text.replace(insert_before, dialog + insert_before, 1)
screen.write_text(text, encoding="utf-8")

# Visible version marker.
home = ROOT / "app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt"
text = home.read_text(encoding="utf-8")
text = text.replace('Metric("الإصدار", "0.13.0", Modifier.weight(1f))', 'Metric("الإصدار", "0.13.1", Modifier.weight(1f))')
home.write_text(text, encoding="utf-8")

# Scope note.
(ROOT / "PHASE13_1_SCOPE.md").write_text('''# Phase 13.1 — Safe Recipe Deletion\n\n- Adds a Delete action to every recipe/version card.\n- Requires explicit confirmation before destructive deletion.\n- Unused recipes are physically deleted; recipe components cascade safely.\n- Recipes referenced by production orders are protected and cannot be deleted.\n- Historical production traceability and costing remain intact.\n- No database schema change; existing user data is preserved.\n''', encoding="utf-8")

# Regression guards.
assert 'suspend fun productionOrderCount(recipeId: Long): Int' in dao.read_text(encoding="utf-8")
assert 'suspend fun deleteById(recipeId: Long): Int' in dao.read_text(encoding="utf-8")
assert 'suspend fun deleteRecipe(recipeId: Long)' in service.read_text(encoding="utf-8")
assert 'Text("حذف", color = MaterialTheme.colorScheme.error)' in screen.read_text(encoding="utf-8")
assert 'حذف نهائي' in screen.read_text(encoding="utf-8")
print('PHASE13_1_SAFE_RECIPE_DELETE_APPLIED')
