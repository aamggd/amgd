package com.fush.erp.cloud

import android.content.Context
import androidx.room.withTransaction
import com.fush.erp.BuildConfig
import com.fush.erp.R
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.ProductionBatchEntity
import com.fush.erp.data.entity.ProductionIssueEntity
import com.fush.erp.data.entity.ProductionMaterialEntity
import com.fush.erp.data.entity.ProductionOrderEntity
import com.fush.erp.data.entity.RecipeComponentEntity
import com.fush.erp.data.entity.RecipeEntity
import com.fush.erp.data.entity.StockMovementEntity
import com.fush.erp.data.entity.UserEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v165 synchronizes the production document mirror and the current lot-level inventory state.
 *
 * Production rows are an OWNER/ADMIN-authored mirror. They are inserted on employee phones
 * without replaying stock/accounting side effects. Existing business documents are compared and
 * preserved as conflicts instead of being silently overwritten.
 *
 * Inventory is synchronized as a current lot-level snapshot rather than replaying historical
 * stock_movements. Employee phones receive explicit CLOUD_SYNC_* adjustment movements so their
 * Room-derived balances/value match the company snapshot exactly without re-running sales,
 * purchase, or production posting logic.
 */
internal class InventoryProductionCloudSyncEngine(
    private val context: Context,
    private val db: FushDatabase,
) {
    private val store = InventoryProductionSyncStore(context.applicationContext)

    fun lastSuccessAt(localUserId: Long): Long = store.lastSuccessAt(localUserId)
    fun lastConflicts(localUserId: Long): List<InventoryProductionConflict> = store.conflicts(localUserId)

    suspend fun sync(
        localUser: UserEntity,
        session: CloudSession,
        cloudRole: String,
    ): CloudOperationResult<InventoryProductionSyncResult> {
        return try {
            var remote = fetchAll(session)
            val canBootstrap = !remote.initialized // v185: bootstrap is based on empty cloud state, not role
            val canPublishProduction = true // active organization member transport
            val canPublishInventory = true // active organization member transport
            var bootstrapped = false
            var uploadedOrders = 0
            var inventoryPublished = 0

            if (!remote.initialized) {
                if (!canBootstrap) {
                    return CloudOperationResult.Failure(context.getString(R.string.cloud_inventory_production_owner_bootstrap_required))
                }
                val publish = publishAll(session)
                uploadedOrders = publish.first
                inventoryPublished = publish.second
                markInitialized(session)
                remote = fetchAll(session)
                bootstrapped = true
            } else if (canBootstrap) {
                // Recipes remain centrally maintained master data. Production/inventory operational
                // changes use the safer v167 incremental paths below instead of a blind full overwrite.
                publishRecipes(session)
                remote = fetchAll(session)
            }

            val counters = Counters()
            val conflicts = mutableListOf<InventoryProductionConflict>()
            applyRecipes(remote, counters, conflicts)
            applyProduction(localUser, remote, counters, conflicts)

            val previousCursor = store.outboundCursorAt(localUser.id)
                .takeIf { it > 0L }
                ?: store.lastSuccessAt(localUser.id)

            if (!bootstrapped && canPublishProduction) {
                uploadedOrders += publishMissingClosedProductionOrders(session, remote)
                if (uploadedOrders > 0) remote = fetchAll(session)
            }

            var inventoryDeltaAbs = 0.0
            var outboundInventoryConflict = false
            if (canPublishInventory && previousCursor > 0L && !bootstrapped) {
                val merge = mergeInventoryBidirectional(localUser, session, remote.inventory, previousCursor, conflicts)
                counters.inventoryAdjusted = merge.adjusted
                counters.inventoryUnchanged = merge.unchanged
                counters.inventoryConflicts = merge.conflicts
                inventoryPublished += merge.published
                inventoryDeltaAbs = merge.absoluteQtyDelta
                outboundInventoryConflict = merge.conflicts > 0
            } else if (!canBootstrap) {
                val inventoryResult = reconcileInventory(localUser, remote.inventory)
                counters.inventoryAdjusted = inventoryResult.adjusted
                counters.inventoryUnchanged = inventoryResult.unchanged
                inventoryDeltaAbs = inventoryResult.absoluteQtyDelta
            } else {
                counters.inventoryUnchanged = remote.inventory.size
            }

            val localOrderKeys = db.productionDao().allOrdersForCloudSync().mapTo(linkedSetOf()) { it.orderNo.uppercase() }
            val remoteOrderKeys = remote.orders.mapTo(linkedSetOf()) { it.optString("order_no").uppercase() }
            counters.skippedLocalOrders = (localOrderKeys - remoteOrderKeys).size

            val completedAt = System.currentTimeMillis()
            store.saveConflicts(localUser.id, conflicts)
            store.markSuccess(localUser.id, completedAt)
            if (!outboundInventoryConflict) {
                store.markOutboundCursor(localUser.id, completedAt)
            }

            val totalConflicts = counters.productionConflicts + counters.inventoryConflicts
            if (counters.downloadedOrders > 0 || totalConflicts > 0 || counters.inventoryAdjusted > 0 || inventoryPublished > 0 || uploadedOrders > 0 || bootstrapped) {
                db.governanceDao().insertAudit(
                    AuditEventEntity(
                        userId = localUser.id,
                        action = "CLOUD_INVENTORY_PRODUCTION_SYNC",
                        entityType = "SYSTEM",
                        entityId = session.requireOrganizationId(),
                        newValue = "ordersUploaded=$uploadedOrders;ordersDownloaded=${counters.downloadedOrders};productionConflicts=${counters.productionConflicts};inventoryPublished=$inventoryPublished;inventoryConflicts=${counters.inventoryConflicts};inventoryAdjusted=${counters.inventoryAdjusted};qtyDeltaAbs=$inventoryDeltaAbs",
                        reason = if (bootstrapped) "Inventory/production cloud mirror bootstrap" else "Bidirectional inventory/production cloud sync",
                        deviceInfo = "ANDROID_CLOUD_SYNC",
                    )
                )
            }

            CloudOperationResult.Success(
                InventoryProductionSyncResult(
                    uploadedProductionOrders = uploadedOrders,
                    downloadedProductionOrders = counters.downloadedOrders,
                    unchangedProductionOrders = counters.unchangedOrders,
                    productionConflicts = totalConflicts,
                    skippedLocalProductionOrders = counters.skippedLocalOrders,
                    inventoryLotsPublished = inventoryPublished,
                    inventoryLotsAdjusted = counters.inventoryAdjusted,
                    inventoryLotsUnchanged = counters.inventoryUnchanged,
                    inventoryAbsoluteQuantityDelta = inventoryDeltaAbs,
                    bootstrappedCloud = bootstrapped,
                    completedAtEpochMillis = completedAt,
                    conflictDetails = conflicts,
                )
            )
        } catch (error: Throwable) {
            CloudOperationResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.cloud_inventory_production_sync_failed)
            )
        }
    }

    private suspend fun publishAll(session: CloudSession): Pair<Int, Int> {
        val org = session.requireOrganizationId()
        val userId = session.userId
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val recipeDao = db.recipeDao()
        val productionDao = db.productionDao()

        val recipes = recipeDao.allRecipesForCloudSync()
        val recipeHeaders = mutableListOf<JSONObject>()
        val recipeComponents = mutableListOf<JSONObject>()
        recipes.forEach { recipe ->
            val productCode = itemById[recipe.productItemId]?.code ?: error("Missing recipe product ${recipe.code}")
            recipeHeaders += JSONObject()
                .put("organization_id", org).put("recipe_code", recipe.code).put("version_no", recipe.versionNo)
                .put("product_item_code", productCode).put("effective_from_ms", recipe.effectiveFrom)
                .put("target_output_qty_base", recipe.targetOutputQtyBase).put("status", recipe.status)
                .put("notes", recipe.notes).put("created_at_ms", recipe.createdAt).put("updated_by", userId)
            recipeDao.components(recipe.id).forEachIndexed { index, component ->
                val itemCode = itemById[component.itemId]?.code ?: error("Missing recipe component item ${component.itemId}")
                recipeComponents += JSONObject()
                    .put("organization_id", org).put("recipe_code", recipe.code).put("version_no", recipe.versionNo)
                    .put("component_no", index + 1).put("item_code", itemCode).put("quantity_base", component.quantityBase)
                    .put("expected_loss_pct", component.expectedLossPct).put("stage", component.stage)
                    .put("sequence_no", component.sequenceNo).put("updated_by", userId)
            }
        }

        val orders = productionDao.allOrdersForCloudSync()
        val recipeById = recipes.associateBy { it.id }
        val orderHeaders = mutableListOf<JSONObject>()
        val materials = mutableListOf<JSONObject>()
        val batches = mutableListOf<JSONObject>()
        val issues = mutableListOf<JSONObject>()
        orders.forEach { order ->
            val recipe = recipeById[order.recipeId] ?: recipeDao.byId(order.recipeId) ?: error("Missing recipe for ${order.orderNo}")
            val productCode = itemById[order.productItemId]?.code ?: error("Missing production product ${order.orderNo}")
            val rawCode = warehouseById[order.rawWarehouseId]?.code ?: error("Missing raw warehouse ${order.orderNo}")
            val finishedCode = warehouseById[order.finishedWarehouseId]?.code ?: error("Missing finished warehouse ${order.orderNo}")
            orderHeaders += JSONObject()
                .put("organization_id", org).put("order_no", order.orderNo)
                .put("recipe_code", recipe.code).put("recipe_version_no", recipe.versionNo)
                .put("product_item_code", productCode).put("planned_output_qty_base", order.plannedOutputQtyBase)
                .put("raw_warehouse_code", rawCode).put("finished_warehouse_code", finishedCode)
                .put("planned_date_ms", order.plannedDate).put("status", order.status)
                .put("direct_labor_cost_base", order.directLaborCostBase).put("notes", order.notes)
                .put("created_at_ms", order.createdAt).put("closed_at_ms", order.closedAt ?: JSONObject.NULL)
                .put("updated_by", userId)

            val orderMaterials = productionDao.materialsForOrder(order.id)
            orderMaterials.forEachIndexed { index, material ->
                val itemCode = itemById[material.itemId]?.code ?: error("Missing material item ${order.orderNo}")
                materials += JSONObject()
                    .put("organization_id", org).put("order_no", order.orderNo).put("material_no", index + 1)
                    .put("item_code", itemCode).put("standard_qty_base", material.standardQtyBase)
                    .put("reserved_qty_base", material.reservedQtyBase).put("issued_qty_base", material.issuedQtyBase)
                    .put("issue_cost_base", material.issueCostBase).put("updated_by", userId)
            }
            val materialNoById = orderMaterials.mapIndexed { index, row -> row.id to index + 1 }.toMap()
            val orderIssues = productionDao.issuesForOrder(order.id)
            val issueNoById = orderIssues.mapIndexed { index, row -> row.id to index + 1 }.toMap()
            orderIssues.forEachIndexed { index, issue ->
                val itemCode = itemById[issue.itemId]?.code ?: error("Missing issue item ${order.orderNo}")
                issues += JSONObject()
                    .put("organization_id", org).put("order_no", order.orderNo).put("issue_no", index + 1)
                    .put("material_no", materialNoById[issue.materialId] ?: 0).put("item_code", itemCode)
                    .put("quantity_base", issue.quantityBase).put("unit_cost_base", issue.unitCostBase)
                    .put("total_cost_base", issue.totalCostBase).put("lot_no", issue.lotNo ?: JSONObject.NULL)
                    .put("expiry_date_ms", issue.expiryDate ?: JSONObject.NULL).put("issue_kind", issue.issueKind)
                    .put("correction_of_issue_no", issue.correctionOfIssueId?.let(issueNoById::get) ?: JSONObject.NULL)
                    .put("reason", issue.reason).put("issue_date_ms", issue.issueDate).put("updated_by", userId)
            }
            productionDao.batchForOrder(order.id)?.let { batch ->
                batches += JSONObject()
                    .put("organization_id", org).put("batch_no", batch.batchNo).put("order_no", order.orderNo)
                    .put("manufacture_date_ms", batch.manufactureDate).put("expiry_date_ms", batch.expiryDate)
                    .put("status", batch.status).put("actual_output_qty_base", batch.actualOutputQtyBase)
                    .put("accepted_qty_base", batch.acceptedQtyBase).put("rejected_qty_base", batch.rejectedQtyBase)
                    .put("scrap_qty_base", batch.scrapQtyBase).put("notes", batch.notes)
                    .put("created_at_ms", batch.createdAt).put("updated_by", userId)
            }
        }

        val snapshotRows = buildLocalInventorySnapshot().values.map { row ->
            JSONObject()
                .put("organization_id", org).put("warehouse_code", row.key.warehouseCode)
                .put("item_code", row.key.itemCode).put("lot_no", row.key.lotNo ?: JSONObject.NULL)
                .put("lot_key", row.key.lotNo.orEmpty()).put("expiry_date_ms", row.key.expiryDate ?: JSONObject.NULL)
                .put("expiry_key", row.key.expiryDate ?: -1L).put("quantity_base", normalizeZero(row.quantity))
                .put("inventory_value_base", normalizeZero(row.value)).put("snapshot_at", System.currentTimeMillis())
                .put("updated_by", userId)
        }

        upsertBatch("fush_md_recipes", "organization_id,recipe_code,version_no", recipeHeaders, session)
        upsertBatch("fush_md_recipe_components", "organization_id,recipe_code,version_no,component_no", recipeComponents, session)
        upsertBatch("fush_tx_production_orders", "organization_id,order_no", orderHeaders, session)
        upsertBatch("fush_tx_production_materials", "organization_id,order_no,material_no", materials, session)
        upsertBatch("fush_tx_production_batches", "organization_id,batch_no", batches, session)
        upsertBatch("fush_tx_production_issues", "organization_id,order_no,issue_no", issues, session)
        upsertBatch("fush_inventory_snapshot", "organization_id,warehouse_code,item_code,lot_key,expiry_key", snapshotRows, session)
        return orders.size to snapshotRows.size
    }

    private suspend fun publishRecipes(session: CloudSession) {
        val org = session.requireOrganizationId()
        val userId = session.userId
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val recipeDao = db.recipeDao()
        val recipes = recipeDao.allRecipesForCloudSync()
        val headers = mutableListOf<JSONObject>()
        val components = mutableListOf<JSONObject>()
        recipes.forEach { recipe ->
            val productCode = itemById[recipe.productItemId]?.code ?: error("Missing recipe product ${recipe.code}")
            headers += JSONObject()
                .put("organization_id", org).put("recipe_code", recipe.code).put("version_no", recipe.versionNo)
                .put("product_item_code", productCode).put("effective_from_ms", recipe.effectiveFrom)
                .put("target_output_qty_base", recipe.targetOutputQtyBase).put("status", recipe.status)
                .put("notes", recipe.notes).put("created_at_ms", recipe.createdAt).put("updated_by", userId)
            recipeDao.components(recipe.id).forEachIndexed { index, component ->
                val itemCode = itemById[component.itemId]?.code ?: error("Missing recipe component item ${component.itemId}")
                components += JSONObject()
                    .put("organization_id", org).put("recipe_code", recipe.code).put("version_no", recipe.versionNo)
                    .put("component_no", index + 1).put("item_code", itemCode).put("quantity_base", component.quantityBase)
                    .put("expected_loss_pct", component.expectedLossPct).put("stage", component.stage)
                    .put("sequence_no", component.sequenceNo).put("updated_by", userId)
            }
        }
        upsertBatch("fush_md_recipes", "organization_id,recipe_code,version_no", headers, session)
        upsertBatch("fush_md_recipe_components", "organization_id,recipe_code,version_no,component_no", components, session)
    }

    /**
     * v178 publishes every locally CLOSED production order that is still missing from the cloud.
     * The outbound time cursor is intentionally not used here: an older local order may have been
     * skipped by the former role/cursor rule and still requires one safe backfill. Existing cloud
     * order numbers are never overwritten here; applyProduction() compares existing documents and
     * surfaces content differences as explicit conflicts before this missing-only backfill runs.
     */
    private suspend fun publishMissingClosedProductionOrders(
        session: CloudSession,
        remote: RemoteData,
    ): Int {
        val remoteKeys = remote.orders.mapTo(linkedSetOf()) { it.optString("order_no").trim().uppercase() }
        val candidates = db.productionDao().allOrdersForCloudSync().filter { order ->
            order.status.equals("CLOSED", true) &&
                order.orderNo.trim().uppercase() !in remoteKeys
        }
        if (candidates.isEmpty()) return 0
        publishProductionOrderRows(session, candidates)
        return candidates.size
    }

    private suspend fun publishProductionOrderRows(session: CloudSession, orders: List<ProductionOrderEntity>) {
        if (orders.isEmpty()) return
        val org = session.requireOrganizationId()
        val userId = session.userId
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val recipeDao = db.recipeDao()
        val productionDao = db.productionDao()
        val recipes = recipeDao.allRecipesForCloudSync()
        val recipeById = recipes.associateBy { it.id }
        val orderHeaders = mutableListOf<JSONObject>()
        val materials = mutableListOf<JSONObject>()
        val batches = mutableListOf<JSONObject>()
        val issues = mutableListOf<JSONObject>()

        orders.forEach { order ->
            val recipe = recipeById[order.recipeId] ?: recipeDao.byId(order.recipeId) ?: error("Missing recipe for ${order.orderNo}")
            val productCode = itemById[order.productItemId]?.code ?: error("Missing production product ${order.orderNo}")
            val rawCode = warehouseById[order.rawWarehouseId]?.code ?: error("Missing raw warehouse ${order.orderNo}")
            val finishedCode = warehouseById[order.finishedWarehouseId]?.code ?: error("Missing finished warehouse ${order.orderNo}")
            orderHeaders += JSONObject()
                .put("organization_id", org).put("order_no", order.orderNo)
                .put("recipe_code", recipe.code).put("recipe_version_no", recipe.versionNo)
                .put("product_item_code", productCode).put("planned_output_qty_base", order.plannedOutputQtyBase)
                .put("raw_warehouse_code", rawCode).put("finished_warehouse_code", finishedCode)
                .put("planned_date_ms", order.plannedDate).put("status", order.status)
                .put("direct_labor_cost_base", order.directLaborCostBase).put("notes", order.notes)
                .put("created_at_ms", order.createdAt).put("closed_at_ms", order.closedAt ?: JSONObject.NULL)
                .put("updated_by", userId)

            val orderMaterials = productionDao.materialsForOrder(order.id)
            orderMaterials.forEachIndexed { index, material ->
                val itemCode = itemById[material.itemId]?.code ?: error("Missing material item ${order.orderNo}")
                materials += JSONObject()
                    .put("organization_id", org).put("order_no", order.orderNo).put("material_no", index + 1)
                    .put("item_code", itemCode).put("standard_qty_base", material.standardQtyBase)
                    .put("reserved_qty_base", material.reservedQtyBase).put("issued_qty_base", material.issuedQtyBase)
                    .put("issue_cost_base", material.issueCostBase).put("updated_by", userId)
            }
            val materialNoById = orderMaterials.mapIndexed { index, row -> row.id to index + 1 }.toMap()
            val orderIssues = productionDao.issuesForOrder(order.id)
            val issueNoById = orderIssues.mapIndexed { index, row -> row.id to index + 1 }.toMap()
            orderIssues.forEachIndexed { index, issue ->
                val itemCode = itemById[issue.itemId]?.code ?: error("Missing issue item ${order.orderNo}")
                issues += JSONObject()
                    .put("organization_id", org).put("order_no", order.orderNo).put("issue_no", index + 1)
                    .put("material_no", materialNoById[issue.materialId] ?: 0).put("item_code", itemCode)
                    .put("quantity_base", issue.quantityBase).put("unit_cost_base", issue.unitCostBase)
                    .put("total_cost_base", issue.totalCostBase).put("lot_no", issue.lotNo ?: JSONObject.NULL)
                    .put("expiry_date_ms", issue.expiryDate ?: JSONObject.NULL).put("issue_kind", issue.issueKind)
                    .put("correction_of_issue_no", issue.correctionOfIssueId?.let(issueNoById::get) ?: JSONObject.NULL)
                    .put("reason", issue.reason).put("issue_date_ms", issue.issueDate).put("updated_by", userId)
            }
            productionDao.batchForOrder(order.id)?.let { batch ->
                batches += JSONObject()
                    .put("organization_id", org).put("batch_no", batch.batchNo).put("order_no", order.orderNo)
                    .put("manufacture_date_ms", batch.manufactureDate).put("expiry_date_ms", batch.expiryDate)
                    .put("status", batch.status).put("actual_output_qty_base", batch.actualOutputQtyBase)
                    .put("accepted_qty_base", batch.acceptedQtyBase).put("rejected_qty_base", batch.rejectedQtyBase)
                    .put("scrap_qty_base", batch.scrapQtyBase).put("notes", batch.notes)
                    .put("created_at_ms", batch.createdAt).put("updated_by", userId)
            }
        }

        upsertBatch("fush_tx_production_orders", "organization_id,order_no", orderHeaders, session)
        upsertBatch("fush_tx_production_materials", "organization_id,order_no,material_no", materials, session)
        upsertBatch("fush_tx_production_batches", "organization_id,batch_no", batches, session)
        upsertBatch("fush_tx_production_issues", "organization_id,order_no,issue_no", issues, session)
    }

    /**
     * Bidirectional inventory merge using the last successful outbound cursor as a lightweight
     * three-way baseline. A lot changed locally after the cursor is only published if the cloud
     * row has not been updated since that cursor. If both changed, neither side wins silently.
     */
    private suspend fun mergeInventoryBidirectional(
        localUser: UserEntity,
        session: CloudSession,
        remoteRows: List<JSONObject>,
        outboundCursor: Long,
        conflicts: MutableList<InventoryProductionConflict>,
    ): InventoryMergeResult {
        val itemByCode = db.itemDao().allRows().associateBy { it.code.uppercase() }
        val warehouseByCode = db.warehouseDao().allRows().associateBy { it.code.uppercase() }
        val local = buildLocalInventorySnapshot()
        val remoteJson = remoteRows.associateBy { row ->
            SnapshotKey(
                warehouseCode = row.getString("warehouse_code"),
                itemCode = row.getString("item_code"),
                lotNo = row.optNullableString("lot_no"),
                expiryDate = row.optNullableLong("expiry_date_ms"),
            )
        }
        val remote = remoteJson.mapValues { (key, row) ->
            SnapshotBalance(key, row.optDouble("quantity_base", 0.0), row.optDouble("inventory_value_base", 0.0))
        }
        val changedKeys = linkedSetOf<SnapshotKey>()
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val itemById = db.itemDao().allRows().associateBy { it.id }
        db.stockDao().allMovementsForCloudSync()
            .asSequence()
            .filter { it.createdAt > outboundCursor && !it.movementType.startsWith("CLOUD_SYNC_") }
            .forEach { movement ->
                val warehouseCode = warehouseById[movement.warehouseId]?.code ?: return@forEach
                val itemCode = itemById[movement.itemId]?.code ?: return@forEach
                changedKeys += SnapshotKey(warehouseCode, itemCode, movement.lotNo, movement.expiryDate)
            }

        val keys = linkedSetOf<SnapshotKey>().apply { addAll(local.keys); addAll(remote.keys) }
        var published = 0
        var adjusted = 0
        var unchanged = 0
        var conflictCount = 0
        var absQtyDelta = 0.0
        val now = System.currentTimeMillis()

        keys.forEach { key ->
            val localBalance = local[key] ?: SnapshotBalance(key, 0.0, 0.0)
            val remoteBalance = remote[key] ?: SnapshotBalance(key, 0.0, 0.0)
            if (nearlyEqual(localBalance.quantity, remoteBalance.quantity) && nearlyEqual(localBalance.value, remoteBalance.value)) {
                unchanged++
                return@forEach
            }

            if (key in changedKeys) {
                val cloudRow = remoteJson[key]
                val cloudUpdatedAt = cloudRow?.optString("updated_at")?.let(::parseCloudUpdatedAt) ?: 0L
                val cloudChangedSinceCursor = cloudRow != null && cloudUpdatedAt > outboundCursor
                if (cloudChangedSinceCursor) {
                    conflictCount++
                    conflicts += inventoryConflict(key, localBalance, remoteBalance)
                    return@forEach
                }
                val expectedUpdatedAt = cloudRow?.optString("updated_at")?.takeIf { it.isNotBlank() }
                val success = publishInventorySnapshotCas(session, key, localBalance, expectedUpdatedAt, now)
                if (success) {
                    published++
                } else {
                    conflictCount++
                    conflicts += inventoryConflict(key, localBalance, remoteBalance)
                }
                return@forEach
            }

            val warehouse = warehouseByCode[key.warehouseCode.uppercase()] ?: error("Missing warehouse ${key.warehouseCode}")
            val item = itemByCode[key.itemCode.uppercase()] ?: error("Missing item ${key.itemCode}")
            val deltaQty = remoteBalance.quantity - localBalance.quantity
            absQtyDelta += kotlin.math.abs(deltaQty)
            applyInventoryTarget(localUser, warehouse.id, item.id, key, localBalance, remoteBalance, now)
            adjusted++
        }

        return InventoryMergeResult(published, adjusted, unchanged, conflictCount, absQtyDelta)
    }

    private suspend fun applyInventoryTarget(
        localUser: UserEntity,
        warehouseId: Long,
        itemId: Long,
        key: SnapshotKey,
        current: SnapshotBalance,
        target: SnapshotBalance,
        now: Long,
    ) {
        db.withTransaction {
            val deltaQty = target.quantity - current.quantity
            var workingValue = current.value
            if (!nearlyEqual(deltaQty, 0.0)) {
                val adjustmentUnitCost = when {
                    kotlin.math.abs(current.quantity) > EPS -> current.value / current.quantity
                    kotlin.math.abs(target.quantity) > EPS -> target.value / target.quantity
                    else -> 0.0
                }.coerceAtLeast(0.0)
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = now,
                        warehouseId = warehouseId,
                        itemId = itemId,
                        movementType = "CLOUD_SYNC_QUANTITY_ADJUSTMENT",
                        quantityBase = deltaQty,
                        unitCostBase = adjustmentUnitCost,
                        referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                        referenceId = 0L,
                        lotNo = key.lotNo,
                        expiryDate = key.expiryDate,
                    )
                )
                workingValue += deltaQty * adjustmentUnitCost
            }
            if (kotlin.math.abs(target.quantity) > EPS && !nearlyEqual(workingValue, target.value)) {
                val oldUnit = (workingValue / target.quantity).coerceAtLeast(0.0)
                val newUnit = (target.value / target.quantity).coerceAtLeast(0.0)
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = now,
                        warehouseId = warehouseId,
                        itemId = itemId,
                        movementType = "CLOUD_SYNC_REVALUE_OUT",
                        quantityBase = -target.quantity,
                        unitCostBase = oldUnit,
                        referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                        referenceId = 0L,
                        lotNo = key.lotNo,
                        expiryDate = key.expiryDate,
                    )
                )
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        movementDate = now,
                        warehouseId = warehouseId,
                        itemId = itemId,
                        movementType = "CLOUD_SYNC_REVALUE_IN",
                        quantityBase = target.quantity,
                        unitCostBase = newUnit,
                        referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                        referenceId = 0L,
                        lotNo = key.lotNo,
                        expiryDate = key.expiryDate,
                    )
                )
            }
        }
    }

    private fun inventoryConflict(
        key: SnapshotKey,
        local: SnapshotBalance,
        cloud: SnapshotBalance,
    ): InventoryProductionConflict = InventoryProductionConflict(
        documentType = "INVENTORY_LOT",
        documentNo = listOf(key.warehouseCode, key.itemCode, key.lotNo ?: "NO_LOT", key.expiryDate?.let(::formatEpoch) ?: "NO_EXPIRY").joinToString(" / "),
        differences = buildList {
            if (!nearlyEqual(local.quantity, cloud.quantity)) {
                add(InventoryProductionConflictDifference("quantity_base", fmt(local.quantity), fmt(cloud.quantity), SEVERITY_BUSINESS))
            }
            if (!nearlyEqual(local.value, cloud.value)) {
                add(InventoryProductionConflictDifference("inventory_value_base", fmt(local.value), fmt(cloud.value), SEVERITY_BUSINESS))
            }
        },
    )

    private fun publishInventorySnapshotCas(
        session: CloudSession,
        key: SnapshotKey,
        local: SnapshotBalance,
        expectedUpdatedAt: String?,
        snapshotAt: Long,
    ): Boolean {
        val payload = JSONObject()
            .put("target_organization_id", session.requireOrganizationId())
            .put("target_warehouse_code", key.warehouseCode)
            .put("target_item_code", key.itemCode)
            .put("target_lot_no", key.lotNo ?: JSONObject.NULL)
            .put("target_lot_key", key.lotNo.orEmpty())
            .put("target_expiry_date_ms", key.expiryDate ?: JSONObject.NULL)
            .put("target_expiry_key", key.expiryDate ?: -1L)
            .put("expected_updated_at", expectedUpdatedAt ?: JSONObject.NULL)
            .put("new_quantity_base", normalizeZero(local.quantity))
            .put("new_inventory_value_base", normalizeZero(local.value))
            .put("new_snapshot_at", snapshotAt)
        return when (val response = request(
            "POST",
            "/rest/v1/rpc/fush_publish_inventory_snapshot_cas",
            payload.toString(),
            session.accessToken,
            null,
        )) {
            is HttpResult.Ok -> response.body.trim().equals("true", true)
            is HttpResult.Error -> error(apiErrorMessage(response))
        }
    }

    private fun parseCloudUpdatedAt(value: String): Long = runCatching {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.recoverCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(Long.MAX_VALUE)

    private suspend fun markInitialized(session: CloudSession) {
        val row = JSONObject()
            .put("organization_id", session.requireOrganizationId())
            .put("domain", DOMAIN)
            .put("initialized_at", System.currentTimeMillis())
            .put("updated_by", session.userId)
        upsertBatch("fush_cloud_domain_state", "organization_id,domain", listOf(row), session)
    }

    private suspend fun applyRecipes(remote: RemoteData, counters: Counters, conflicts: MutableList<InventoryProductionConflict>) {
        val recipeDao = db.recipeDao()
        val itemDao = db.itemDao()
        remote.recipes.sortedWith(compareBy({ it.optString("recipe_code") }, { it.optInt("version_no") })).forEach { header ->
            val code = header.getString("recipe_code")
            val version = header.getInt("version_no")
            val existing = recipeDao.byCodeVersion(code, version)
            if (existing != null) {
                val differences = recipeDifferences(existing, header, remote)
                if (differences.isNotEmpty()) {
                    conflicts += InventoryProductionConflict("RECIPE", "$code/v$version", differences)
                }
                return@forEach
            }
            db.withTransaction {
                val product = itemDao.byCode(header.getString("product_item_code"))
                    ?: error("Missing product item ${header.getString("product_item_code")}")
                val recipeId = recipeDao.insertRecipe(
                    RecipeEntity(
                        code = code,
                        productItemId = product.id,
                        versionNo = version,
                        effectiveFrom = header.optLong("effective_from_ms"),
                        targetOutputQtyBase = header.optDouble("target_output_qty_base", 0.0),
                        status = header.optString("status", "ACTIVE"),
                        notes = header.optString("notes"),
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                    )
                )
                val components = remote.recipeComponents
                    .filter { it.optString("recipe_code").equals(code, true) && it.optInt("version_no") == version }
                    .sortedBy { it.optInt("component_no") }
                    .map { row ->
                        val item = itemDao.byCode(row.getString("item_code")) ?: error("Missing recipe item ${row.getString("item_code")}")
                        RecipeComponentEntity(
                            recipeId = recipeId,
                            itemId = item.id,
                            quantityBase = row.optDouble("quantity_base", 0.0),
                            expectedLossPct = row.optDouble("expected_loss_pct", 0.0),
                            stage = row.optString("stage", "PREPARATION"),
                            sequenceNo = row.optInt("sequence_no", row.optInt("component_no")),
                        )
                    }
                if (components.isNotEmpty()) recipeDao.insertComponents(components)
            }
        }
    }

    private suspend fun applyProduction(
        localUser: UserEntity,
        remote: RemoteData,
        counters: Counters,
        conflicts: MutableList<InventoryProductionConflict>,
    ) {
        val dao = db.productionDao()
        val recipeDao = db.recipeDao()
        val itemDao = db.itemDao()
        val warehouseDao = db.warehouseDao()
        remote.orders.sortedWith(compareBy({ it.optLong("planned_date_ms") }, { it.optString("order_no") })).forEach { header ->
            val orderNo = header.getString("order_no")
            val existing = dao.orderByNo(orderNo)
            if (existing != null) {
                val differences = productionDifferences(existing, header, remote)
                if (differences.isEmpty()) {
                    counters.unchangedOrders++
                } else {
                    counters.productionConflicts++
                    conflicts += InventoryProductionConflict("PRODUCTION_ORDER", orderNo, differences)
                }
                return@forEach
            }

            db.withTransaction {
                val recipe = recipeDao.byCodeVersion(header.getString("recipe_code"), header.optInt("recipe_version_no"))
                    ?: error("Missing recipe ${header.getString("recipe_code")}")
                val product = itemDao.byCode(header.getString("product_item_code"))
                    ?: error("Missing product ${header.getString("product_item_code")}")
                val rawWarehouse = warehouseDao.byCode(header.getString("raw_warehouse_code"))
                    ?: error("Missing warehouse ${header.getString("raw_warehouse_code")}")
                val finishedWarehouse = warehouseDao.byCode(header.getString("finished_warehouse_code"))
                    ?: error("Missing warehouse ${header.getString("finished_warehouse_code")}")
                val orderId = dao.insertOrder(
                    ProductionOrderEntity(
                        orderNo = orderNo,
                        recipeId = recipe.id,
                        productItemId = product.id,
                        plannedOutputQtyBase = header.optDouble("planned_output_qty_base", 0.0),
                        rawWarehouseId = rawWarehouse.id,
                        finishedWarehouseId = finishedWarehouse.id,
                        plannedDate = header.optLong("planned_date_ms"),
                        status = header.optString("status", "PLANNED"),
                        directLaborCostBase = header.optDouble("direct_labor_cost_base", 0.0),
                        notes = header.optString("notes"),
                        createdBy = localUser.id,
                        createdAt = header.optLong("created_at_ms", System.currentTimeMillis()),
                        closedAt = header.optNullableLong("closed_at_ms"),
                    )
                )
                val recipeComponents = recipeDao.components(recipe.id)
                val remoteMaterials = remote.materials.filter { it.optString("order_no").equals(orderNo, true) }.sortedBy { it.optInt("material_no") }
                val materialRows = remoteMaterials.map { row ->
                    val item = itemDao.byCode(row.getString("item_code")) ?: error("Missing material item ${row.getString("item_code")}")
                    val component = recipeComponents.getOrNull(row.optInt("material_no") - 1)
                    ProductionMaterialEntity(
                        orderId = orderId,
                        recipeComponentId = component?.id ?: 0L,
                        itemId = item.id,
                        standardQtyBase = row.optDouble("standard_qty_base", 0.0),
                        reservedQtyBase = row.optDouble("reserved_qty_base", 0.0),
                        issuedQtyBase = row.optDouble("issued_qty_base", 0.0),
                        issueCostBase = row.optDouble("issue_cost_base", 0.0),
                    )
                }
                if (materialRows.isNotEmpty()) dao.insertMaterials(materialRows)
                val localMaterials = dao.materialsForOrder(orderId)

                remote.batches.firstOrNull { it.optString("order_no").equals(orderNo, true) }?.let { batch ->
                    dao.insertBatch(
                        ProductionBatchEntity(
                            batchNo = batch.getString("batch_no"),
                            orderId = orderId,
                            manufactureDate = batch.optLong("manufacture_date_ms"),
                            expiryDate = batch.optLong("expiry_date_ms"),
                            status = batch.optString("status", "MATERIALS_RESERVED"),
                            actualOutputQtyBase = batch.optDouble("actual_output_qty_base", 0.0),
                            acceptedQtyBase = batch.optDouble("accepted_qty_base", 0.0),
                            rejectedQtyBase = batch.optDouble("rejected_qty_base", 0.0),
                            scrapQtyBase = batch.optDouble("scrap_qty_base", 0.0),
                            notes = batch.optString("notes"),
                            createdAt = batch.optLong("created_at_ms", System.currentTimeMillis()),
                        )
                    )
                }

                val issueIdByNo = mutableMapOf<Int, Long>()
                remote.issues.filter { it.optString("order_no").equals(orderNo, true) }.sortedBy { it.optInt("issue_no") }.forEach { issue ->
                    val issueNo = issue.optInt("issue_no")
                    val material = localMaterials.getOrNull(issue.optInt("material_no") - 1)
                        ?: error("Missing material ${issue.optInt("material_no")} for $orderNo")
                    val item = itemDao.byCode(issue.getString("item_code")) ?: error("Missing issue item ${issue.getString("item_code")}")
                    val correctionNo = issue.optNullableInt("correction_of_issue_no")
                    val localIssueId = dao.insertIssue(
                        ProductionIssueEntity(
                            orderId = orderId,
                            materialId = material.id,
                            itemId = item.id,
                            quantityBase = issue.optDouble("quantity_base", 0.0),
                            unitCostBase = issue.optDouble("unit_cost_base", 0.0),
                            totalCostBase = issue.optDouble("total_cost_base", 0.0),
                            lotNo = issue.optNullableString("lot_no"),
                            expiryDate = issue.optNullableLong("expiry_date_ms"),
                            issueKind = issue.optString("issue_kind", "ISSUE"),
                            correctionOfIssueId = correctionNo?.let(issueIdByNo::get),
                            reason = issue.optString("reason"),
                            createdBy = localUser.id,
                            issueDate = issue.optLong("issue_date_ms", System.currentTimeMillis()),
                        )
                    )
                    issueIdByNo[issueNo] = localIssueId
                }
            }
            counters.downloadedOrders++
        }
    }

    private suspend fun reconcileInventory(localUser: UserEntity, remoteRows: List<JSONObject>): InventoryReconcileResult {
        val itemByCode = db.itemDao().allRows().associateBy { it.code.uppercase() }
        val warehouseByCode = db.warehouseDao().allRows().associateBy { it.code.uppercase() }
        val local = buildLocalInventorySnapshot()
        val remote = remoteRows.associate { row ->
            val key = SnapshotKey(
                warehouseCode = row.getString("warehouse_code"),
                itemCode = row.getString("item_code"),
                lotNo = row.optNullableString("lot_no"),
                expiryDate = row.optNullableLong("expiry_date_ms"),
            )
            key to SnapshotBalance(key, row.optDouble("quantity_base", 0.0), row.optDouble("inventory_value_base", 0.0))
        }
        val keys = linkedSetOf<SnapshotKey>().apply { addAll(local.keys); addAll(remote.keys) }
        var adjusted = 0
        var unchanged = 0
        var absQtyDelta = 0.0
        val now = System.currentTimeMillis()
        keys.forEach { key ->
            val current = local[key] ?: SnapshotBalance(key, 0.0, 0.0)
            val target = remote[key] ?: SnapshotBalance(key, 0.0, 0.0)
            if (nearlyEqual(current.quantity, target.quantity) && nearlyEqual(current.value, target.value)) {
                unchanged++
                return@forEach
            }
            val warehouse = warehouseByCode[key.warehouseCode.uppercase()] ?: error("Missing warehouse ${key.warehouseCode}")
            val item = itemByCode[key.itemCode.uppercase()] ?: error("Missing item ${key.itemCode}")
            val deltaQty = target.quantity - current.quantity
            absQtyDelta += kotlin.math.abs(deltaQty)
            db.withTransaction {
                var workingQty = current.quantity
                var workingValue = current.value
                if (!nearlyEqual(deltaQty, 0.0)) {
                    val adjustmentUnitCost = when {
                        kotlin.math.abs(current.quantity) > EPS -> current.value / current.quantity
                        kotlin.math.abs(target.quantity) > EPS -> target.value / target.quantity
                        else -> 0.0
                    }.coerceAtLeast(0.0)
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = warehouse.id,
                            itemId = item.id,
                            movementType = "CLOUD_SYNC_QUANTITY_ADJUSTMENT",
                            quantityBase = deltaQty,
                            unitCostBase = adjustmentUnitCost,
                            referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                            referenceId = 0L,
                            lotNo = key.lotNo,
                            expiryDate = key.expiryDate,
                        )
                    )
                    workingQty += deltaQty
                    workingValue += deltaQty * adjustmentUnitCost
                }
                if (kotlin.math.abs(target.quantity) > EPS && !nearlyEqual(workingValue, target.value)) {
                    val oldUnit = (workingValue / target.quantity).coerceAtLeast(0.0)
                    val newUnit = (target.value / target.quantity).coerceAtLeast(0.0)
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = warehouse.id,
                            itemId = item.id,
                            movementType = "CLOUD_SYNC_REVALUE_OUT",
                            quantityBase = -target.quantity,
                            unitCostBase = oldUnit,
                            referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                            referenceId = 0L,
                            lotNo = key.lotNo,
                            expiryDate = key.expiryDate,
                        )
                    )
                    db.stockDao().insertMovement(
                        StockMovementEntity(
                            movementDate = now,
                            warehouseId = warehouse.id,
                            itemId = item.id,
                            movementType = "CLOUD_SYNC_REVALUE_IN",
                            quantityBase = target.quantity,
                            unitCostBase = newUnit,
                            referenceType = "CLOUD_INVENTORY_SNAPSHOT",
                            referenceId = 0L,
                            lotNo = key.lotNo,
                            expiryDate = key.expiryDate,
                        )
                    )
                }
            }
            adjusted++
        }
        return InventoryReconcileResult(adjusted, unchanged, absQtyDelta)
    }

    private suspend fun buildLocalInventorySnapshot(): Map<SnapshotKey, SnapshotBalance> {
        val warehouseById = db.warehouseDao().allRows().associateBy { it.id }
        val itemById = db.itemDao().allRows().associateBy { it.id }
        val result = linkedMapOf<SnapshotKey, SnapshotBalance>()
        db.stockDao().allMovementsForCloudSync().forEach { movement ->
            val warehouseCode = warehouseById[movement.warehouseId]?.code ?: return@forEach
            val itemCode = itemById[movement.itemId]?.code ?: return@forEach
            val key = SnapshotKey(warehouseCode, itemCode, movement.lotNo, movement.expiryDate)
            val old = result[key] ?: SnapshotBalance(key, 0.0, 0.0)
            result[key] = old.copy(
                quantity = old.quantity + movement.quantityBase,
                value = old.value + movement.quantityBase * movement.unitCostBase,
            )
        }
        return result
    }

    /**
     * v166: field-level, non-destructive production conflict inspection.
     *
     * The v165 mirror compared one long canonical string. That protected data, but made a single
     * difference look like an unreadable full-document conflict. v166 compares the same business
     * scope field-by-field and classifies each difference so the operator can see whether it is a
     * stock/cost/quantity issue, a timing difference, or descriptive text only.
     */
    private suspend fun recipeDifferences(
        recipe: RecipeEntity,
        header: JSONObject,
        remote: RemoteData,
    ): List<InventoryProductionConflictDifference> {
        val differences = mutableListOf<InventoryProductionConflictDifference>()
        val productCode = db.itemDao().byId(recipe.productItemId)?.code.orEmpty()
        addTextDifference(differences, "product_item_code", productCode, header.optString("product_item_code"), SEVERITY_BUSINESS)
        addLongDifference(differences, "effective_from_ms", recipe.effectiveFrom, header.optLong("effective_from_ms"), SEVERITY_TIMING)
        addDoubleDifference(differences, "target_output_qty_base", recipe.targetOutputQtyBase, header.optDouble("target_output_qty_base", 0.0), SEVERITY_BUSINESS)
        addTextDifference(differences, "status", recipe.status, header.optString("status"), SEVERITY_BUSINESS)
        addTextDifference(differences, "notes", recipe.notes, header.optString("notes"), SEVERITY_DESCRIPTIVE)

        val localComponents = db.recipeDao().components(recipe.id)
        val remoteComponents = remote.recipeComponents
            .filter { it.optString("recipe_code").equals(recipe.code, true) && it.optInt("version_no") == recipe.versionNo }
            .sortedBy { it.optInt("component_no") }
        val maxComponents = maxOf(localComponents.size, remoteComponents.size)
        for (index in 0 until maxComponents) {
            val local = localComponents.getOrNull(index)
            val cloud = remoteComponents.getOrNull(index)
            val prefix = "component[${index + 1}]"
            if (local == null) {
                addPresenceDifference(differences, prefix, false, true, SEVERITY_BUSINESS)
                continue
            }
            if (cloud == null) {
                addPresenceDifference(differences, prefix, true, false, SEVERITY_BUSINESS)
                continue
            }
            val localItem = db.itemDao().byId(local.itemId)?.code.orEmpty()
            addTextDifference(differences, "$prefix.item_code", localItem, cloud.optString("item_code"), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.quantity_base", local.quantityBase, cloud.optDouble("quantity_base", 0.0), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.expected_loss_pct", local.expectedLossPct, cloud.optDouble("expected_loss_pct", 0.0), SEVERITY_BUSINESS)
            addTextDifference(differences, "$prefix.stage", local.stage, cloud.optString("stage"), SEVERITY_BUSINESS)
            addIntDifference(differences, "$prefix.sequence_no", local.sequenceNo, cloud.optInt("sequence_no"), SEVERITY_BUSINESS)
        }
        return differences
    }

    private suspend fun productionDifferences(
        order: ProductionOrderEntity,
        header: JSONObject,
        remote: RemoteData,
    ): List<InventoryProductionConflictDifference> {
        val differences = mutableListOf<InventoryProductionConflictDifference>()
        val recipe = db.recipeDao().byId(order.recipeId)
        val product = db.itemDao().byId(order.productItemId)?.code.orEmpty()
        val rawWarehouse = db.warehouseDao().byId(order.rawWarehouseId)?.code.orEmpty()
        val finishedWarehouse = db.warehouseDao().byId(order.finishedWarehouseId)?.code.orEmpty()

        addTextDifference(differences, "order.recipe_code", recipe?.code.orEmpty(), header.optString("recipe_code"), SEVERITY_BUSINESS)
        addIntDifference(differences, "order.recipe_version_no", recipe?.versionNo ?: 0, header.optInt("recipe_version_no"), SEVERITY_BUSINESS)
        addTextDifference(differences, "order.product_item_code", product, header.optString("product_item_code"), SEVERITY_BUSINESS)
        addDoubleDifference(differences, "order.planned_output_qty_base", order.plannedOutputQtyBase, header.optDouble("planned_output_qty_base", 0.0), SEVERITY_BUSINESS)
        addTextDifference(differences, "order.raw_warehouse_code", rawWarehouse, header.optString("raw_warehouse_code"), SEVERITY_BUSINESS)
        addTextDifference(differences, "order.finished_warehouse_code", finishedWarehouse, header.optString("finished_warehouse_code"), SEVERITY_BUSINESS)
        addLongDifference(differences, "order.planned_date_ms", order.plannedDate, header.optLong("planned_date_ms"), SEVERITY_TIMING)
        addTextDifference(differences, "order.status", order.status, header.optString("status"), SEVERITY_BUSINESS)
        addDoubleDifference(differences, "order.direct_labor_cost_base", order.directLaborCostBase, header.optDouble("direct_labor_cost_base", 0.0), SEVERITY_BUSINESS)
        addTextDifference(differences, "order.notes", order.notes, header.optString("notes"), SEVERITY_DESCRIPTIVE)
        addNullableLongDifference(differences, "order.closed_at_ms", order.closedAt, header.optNullableLong("closed_at_ms"), SEVERITY_TIMING)

        val localMaterials = db.productionDao().materialsForOrder(order.id)
        val remoteMaterials = remote.materials
            .filter { it.optString("order_no").equals(order.orderNo, true) }
            .sortedBy { it.optInt("material_no") }
        val maxMaterials = maxOf(localMaterials.size, remoteMaterials.size)
        val materialNoById = localMaterials.mapIndexed { index, row -> row.id to index + 1 }.toMap()
        for (index in 0 until maxMaterials) {
            val local = localMaterials.getOrNull(index)
            val cloud = remoteMaterials.getOrNull(index)
            val prefix = "material[${index + 1}]"
            if (local == null) {
                addPresenceDifference(differences, prefix, false, true, SEVERITY_BUSINESS)
                continue
            }
            if (cloud == null) {
                addPresenceDifference(differences, prefix, true, false, SEVERITY_BUSINESS)
                continue
            }
            val localItem = db.itemDao().byId(local.itemId)?.code.orEmpty()
            addTextDifference(differences, "$prefix.item_code", localItem, cloud.optString("item_code"), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.standard_qty_base", local.standardQtyBase, cloud.optDouble("standard_qty_base", 0.0), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.reserved_qty_base", local.reservedQtyBase, cloud.optDouble("reserved_qty_base", 0.0), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.issued_qty_base", local.issuedQtyBase, cloud.optDouble("issued_qty_base", 0.0), SEVERITY_BUSINESS)
            addDoubleDifference(differences, "$prefix.issue_cost_base", local.issueCostBase, cloud.optDouble("issue_cost_base", 0.0), SEVERITY_BUSINESS)
        }

        val localBatch = db.productionDao().batchForOrder(order.id)
        val cloudBatch = remote.batches.firstOrNull { it.optString("order_no").equals(order.orderNo, true) }
        when {
            localBatch == null && cloudBatch != null -> addPresenceDifference(differences, "batch", false, true, SEVERITY_BUSINESS)
            localBatch != null && cloudBatch == null -> addPresenceDifference(differences, "batch", true, false, SEVERITY_BUSINESS)
            localBatch != null && cloudBatch != null -> {
                addTextDifference(differences, "batch.batch_no", localBatch.batchNo, cloudBatch.optString("batch_no"), SEVERITY_BUSINESS)
                addLongDifference(differences, "batch.manufacture_date_ms", localBatch.manufactureDate, cloudBatch.optLong("manufacture_date_ms"), SEVERITY_BUSINESS)
                addLongDifference(differences, "batch.expiry_date_ms", localBatch.expiryDate, cloudBatch.optLong("expiry_date_ms"), SEVERITY_BUSINESS)
                addTextDifference(differences, "batch.status", localBatch.status, cloudBatch.optString("status"), SEVERITY_BUSINESS)
                addDoubleDifference(differences, "batch.actual_output_qty_base", localBatch.actualOutputQtyBase, cloudBatch.optDouble("actual_output_qty_base", 0.0), SEVERITY_BUSINESS)
                addDoubleDifference(differences, "batch.accepted_qty_base", localBatch.acceptedQtyBase, cloudBatch.optDouble("accepted_qty_base", 0.0), SEVERITY_BUSINESS)
                addDoubleDifference(differences, "batch.rejected_qty_base", localBatch.rejectedQtyBase, cloudBatch.optDouble("rejected_qty_base", 0.0), SEVERITY_BUSINESS)
                addDoubleDifference(differences, "batch.scrap_qty_base", localBatch.scrapQtyBase, cloudBatch.optDouble("scrap_qty_base", 0.0), SEVERITY_BUSINESS)
                addTextDifference(differences, "batch.notes", localBatch.notes, cloudBatch.optString("notes"), SEVERITY_DESCRIPTIVE)
            }
        }

        // Pair material issues by material/item/kind first, not by timestamp. This prevents a pure
        // issue-time difference from shifting every later issue and turning it into a false cascade.
        val localIssues = db.productionDao().issuesForOrder(order.id).map { issue ->
            LocalIssueView(
                materialNo = materialNoById[issue.materialId] ?: 0,
                itemCode = db.itemDao().byId(issue.itemId)?.code.orEmpty(),
                quantityBase = issue.quantityBase,
                unitCostBase = issue.unitCostBase,
                totalCostBase = issue.totalCostBase,
                lotNo = issue.lotNo,
                expiryDate = issue.expiryDate,
                issueKind = issue.issueKind,
                reason = issue.reason,
                issueDate = issue.issueDate,
            )
        }
        val cloudIssues = remote.issues
            .filter { it.optString("order_no").equals(order.orderNo, true) }
            .map { row ->
                RemoteIssueView(
                    materialNo = row.optInt("material_no"),
                    itemCode = row.optString("item_code"),
                    quantityBase = row.optDouble("quantity_base", 0.0),
                    unitCostBase = row.optDouble("unit_cost_base", 0.0),
                    totalCostBase = row.optDouble("total_cost_base", 0.0),
                    lotNo = row.optNullableString("lot_no"),
                    expiryDate = row.optNullableLong("expiry_date_ms"),
                    issueKind = row.optString("issue_kind"),
                    reason = row.optString("reason"),
                    issueDate = row.optLong("issue_date_ms"),
                )
            }
        val issueKeys = linkedSetOf<IssueGroupKey>().apply {
            localIssues.forEach { add(it.groupKey()) }
            cloudIssues.forEach { add(it.groupKey()) }
        }
        issueKeys.sortedWith(compareBy<IssueGroupKey>({ it.materialNo }, { it.itemCode }, { it.issueKind })).forEach { key ->
            val locals = localIssues.filter { it.groupKey() == key }.sortedWith(localIssueComparator)
            val clouds = cloudIssues.filter { it.groupKey() == key }.sortedWith(remoteIssueComparator)
            val maxIssues = maxOf(locals.size, clouds.size)
            for (index in 0 until maxIssues) {
                val local = locals.getOrNull(index)
                val cloud = clouds.getOrNull(index)
                val prefix = "issue[m${key.materialNo}:${key.itemCode}:${key.issueKind}#${index + 1}]"
                if (local == null) {
                    addPresenceDifference(differences, prefix, false, true, SEVERITY_BUSINESS)
                    continue
                }
                if (cloud == null) {
                    addPresenceDifference(differences, prefix, true, false, SEVERITY_BUSINESS)
                    continue
                }
                addDoubleDifference(differences, "$prefix.quantity_base", local.quantityBase, cloud.quantityBase, SEVERITY_BUSINESS)
                addDoubleDifference(differences, "$prefix.unit_cost_base", local.unitCostBase, cloud.unitCostBase, SEVERITY_BUSINESS)
                addDoubleDifference(differences, "$prefix.total_cost_base", local.totalCostBase, cloud.totalCostBase, SEVERITY_BUSINESS)
                addTextDifference(differences, "$prefix.lot_no", local.lotNo.orEmpty(), cloud.lotNo.orEmpty(), SEVERITY_BUSINESS)
                addNullableLongDifference(differences, "$prefix.expiry_date_ms", local.expiryDate, cloud.expiryDate, SEVERITY_BUSINESS)
                addTextDifference(differences, "$prefix.reason", local.reason, cloud.reason, SEVERITY_DESCRIPTIVE)
                addLongDifference(differences, "$prefix.issue_date_ms", local.issueDate, cloud.issueDate, SEVERITY_TIMING)
            }
        }
        return differences
    }

    private fun addTextDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        local: String,
        cloud: String,
        severity: String,
    ) {
        if (normalizeText(local) != normalizeText(cloud)) {
            rows += InventoryProductionConflictDifference(field, local.ifBlank { EMPTY_VALUE }, cloud.ifBlank { EMPTY_VALUE }, severity)
        }
    }

    private fun addDoubleDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        local: Double,
        cloud: Double,
        severity: String,
    ) {
        if (!nearlyEqual(local, cloud)) rows += InventoryProductionConflictDifference(field, fmt(local), fmt(cloud), severity)
    }

    private fun addLongDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        local: Long,
        cloud: Long,
        severity: String,
    ) {
        if (local != cloud) {
            rows += InventoryProductionConflictDifference(
                field,
                if (field.endsWith("_ms")) formatEpoch(local) else local.toString(),
                if (field.endsWith("_ms")) formatEpoch(cloud) else cloud.toString(),
                severity,
            )
        }
    }

    private fun addNullableLongDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        local: Long?,
        cloud: Long?,
        severity: String,
    ) {
        if (local != cloud) {
            rows += InventoryProductionConflictDifference(
                field,
                local?.let { if (field.endsWith("_ms")) formatEpoch(it) else it.toString() } ?: EMPTY_VALUE,
                cloud?.let { if (field.endsWith("_ms")) formatEpoch(it) else it.toString() } ?: EMPTY_VALUE,
                severity,
            )
        }
    }

    private fun addIntDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        local: Int,
        cloud: Int,
        severity: String,
    ) {
        if (local != cloud) rows += InventoryProductionConflictDifference(field, local.toString(), cloud.toString(), severity)
    }

    private fun addPresenceDifference(
        rows: MutableList<InventoryProductionConflictDifference>,
        field: String,
        localPresent: Boolean,
        cloudPresent: Boolean,
        severity: String,
    ) {
        rows += InventoryProductionConflictDifference(
            "$field.present",
            if (localPresent) context.getString(R.string.cloud_inventory_production_value_present) else context.getString(R.string.cloud_inventory_production_value_absent),
            if (cloudPresent) context.getString(R.string.cloud_inventory_production_value_present) else context.getString(R.string.cloud_inventory_production_value_absent),
            severity,
        )
    }

    private fun normalizeText(value: String): String = value.trim().replace(Regex("\\s+"), " ")
    private fun formatEpoch(value: Long): String = runCatching {
        java.time.Instant.ofEpochMilli(value)
            .atZone(java.time.ZoneId.of("Asia/Aden"))
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX"))
    }.getOrElse { value.toString() }

    private data class IssueGroupKey(val materialNo: Int, val itemCode: String, val issueKind: String)
    private data class LocalIssueView(
        val materialNo: Int,
        val itemCode: String,
        val quantityBase: Double,
        val unitCostBase: Double,
        val totalCostBase: Double,
        val lotNo: String?,
        val expiryDate: Long?,
        val issueKind: String,
        val reason: String,
        val issueDate: Long,
    ) {
        fun groupKey() = IssueGroupKey(materialNo, itemCode.trim().uppercase(), issueKind.trim().uppercase())
    }
    private data class RemoteIssueView(
        val materialNo: Int,
        val itemCode: String,
        val quantityBase: Double,
        val unitCostBase: Double,
        val totalCostBase: Double,
        val lotNo: String?,
        val expiryDate: Long?,
        val issueKind: String,
        val reason: String,
        val issueDate: Long,
    ) {
        fun groupKey() = IssueGroupKey(materialNo, itemCode.trim().uppercase(), issueKind.trim().uppercase())
    }
    private val localIssueComparator = compareBy<LocalIssueView>({ it.lotNo.orEmpty() }, { it.expiryDate ?: Long.MIN_VALUE }, { it.quantityBase }, { it.totalCostBase }, { it.issueDate })
    private val remoteIssueComparator = compareBy<RemoteIssueView>({ it.lotNo.orEmpty() }, { it.expiryDate ?: Long.MIN_VALUE }, { it.quantityBase }, { it.totalCostBase }, { it.issueDate })

    private suspend fun recipeCanonical(recipe: RecipeEntity): String {
        val productCode = db.itemDao().byId(recipe.productItemId)?.code.orEmpty()
        val components = db.recipeDao().components(recipe.id).map { c ->
            val itemCode = db.itemDao().byId(c.itemId)?.code.orEmpty()
            listOf(itemCode, fmt(c.quantityBase), fmt(c.expectedLossPct), c.stage, c.sequenceNo.toString()).joinToString("|")
        }.joinToString(";")
        return listOf(productCode, recipe.effectiveFrom.toString(), fmt(recipe.targetOutputQtyBase), recipe.status, recipe.notes, components).joinToString("~")
    }

    private fun remoteRecipeCanonical(header: JSONObject, remote: RemoteData): String {
        val code = header.optString("recipe_code")
        val version = header.optInt("version_no")
        val components = remote.recipeComponents.filter { it.optString("recipe_code").equals(code, true) && it.optInt("version_no") == version }
            .sortedBy { it.optInt("component_no") }
            .joinToString(";") { c -> listOf(c.optString("item_code"), fmt(c.optDouble("quantity_base")), fmt(c.optDouble("expected_loss_pct")), c.optString("stage"), c.optInt("sequence_no").toString()).joinToString("|") }
        return listOf(header.optString("product_item_code"), header.optLong("effective_from_ms").toString(), fmt(header.optDouble("target_output_qty_base")), header.optString("status"), header.optString("notes"), components).joinToString("~")
    }

    private suspend fun productionCanonical(order: ProductionOrderEntity): String {
        val recipe = db.recipeDao().byId(order.recipeId)
        val product = db.itemDao().byId(order.productItemId)?.code.orEmpty()
        val raw = db.warehouseDao().byId(order.rawWarehouseId)?.code.orEmpty()
        val finished = db.warehouseDao().byId(order.finishedWarehouseId)?.code.orEmpty()
        val materialParts = mutableListOf<String>()
        for (m in db.productionDao().materialsForOrder(order.id)) {
            val item = db.itemDao().byId(m.itemId)?.code.orEmpty()
            materialParts += listOf(item, fmt(m.standardQtyBase), fmt(m.reservedQtyBase), fmt(m.issuedQtyBase), fmt(m.issueCostBase)).joinToString("|")
        }
        val materials = materialParts.joinToString(";")
        val batch = db.productionDao().batchForOrder(order.id)?.let { b ->
            listOf(b.batchNo, b.manufactureDate.toString(), b.expiryDate.toString(), b.status, fmt(b.actualOutputQtyBase), fmt(b.acceptedQtyBase), fmt(b.rejectedQtyBase), fmt(b.scrapQtyBase), b.notes).joinToString("|")
        }.orEmpty()
        val issueParts = mutableListOf<String>()
        for (i in db.productionDao().issuesForOrder(order.id)) {
            val item = db.itemDao().byId(i.itemId)?.code.orEmpty()
            issueParts += listOf(item, fmt(i.quantityBase), fmt(i.unitCostBase), fmt(i.totalCostBase), i.lotNo.orEmpty(), i.expiryDate?.toString().orEmpty(), i.issueKind, i.reason, i.issueDate.toString()).joinToString("|")
        }
        val issues = issueParts.joinToString(";")
        return listOf(recipe?.code.orEmpty(), recipe?.versionNo?.toString().orEmpty(), product, fmt(order.plannedOutputQtyBase), raw, finished, order.plannedDate.toString(), order.status, fmt(order.directLaborCostBase), order.notes, order.closedAt?.toString().orEmpty(), materials, batch, issues).joinToString("~")
    }

    private fun remoteProductionCanonical(header: JSONObject, remote: RemoteData): String {
        val orderNo = header.optString("order_no")
        val materials = remote.materials.filter { it.optString("order_no").equals(orderNo, true) }.sortedBy { it.optInt("material_no") }
            .joinToString(";") { m -> listOf(m.optString("item_code"), fmt(m.optDouble("standard_qty_base")), fmt(m.optDouble("reserved_qty_base")), fmt(m.optDouble("issued_qty_base")), fmt(m.optDouble("issue_cost_base"))).joinToString("|") }
        val batch = remote.batches.firstOrNull { it.optString("order_no").equals(orderNo, true) }?.let { b ->
            listOf(b.optString("batch_no"), b.optLong("manufacture_date_ms").toString(), b.optLong("expiry_date_ms").toString(), b.optString("status"), fmt(b.optDouble("actual_output_qty_base")), fmt(b.optDouble("accepted_qty_base")), fmt(b.optDouble("rejected_qty_base")), fmt(b.optDouble("scrap_qty_base")), b.optString("notes")).joinToString("|")
        }.orEmpty()
        val issues = remote.issues.filter { it.optString("order_no").equals(orderNo, true) }.sortedBy { it.optInt("issue_no") }
            .joinToString(";") { i -> listOf(i.optString("item_code"), fmt(i.optDouble("quantity_base")), fmt(i.optDouble("unit_cost_base")), fmt(i.optDouble("total_cost_base")), i.optNullableString("lot_no").orEmpty(), i.optNullableLong("expiry_date_ms")?.toString().orEmpty(), i.optString("issue_kind"), i.optString("reason"), i.optLong("issue_date_ms").toString()).joinToString("|") }
        return listOf(header.optString("recipe_code"), header.optInt("recipe_version_no").toString(), header.optString("product_item_code"), fmt(header.optDouble("planned_output_qty_base")), header.optString("raw_warehouse_code"), header.optString("finished_warehouse_code"), header.optLong("planned_date_ms").toString(), header.optString("status"), fmt(header.optDouble("direct_labor_cost_base")), header.optString("notes"), header.optNullableLong("closed_at_ms")?.toString().orEmpty(), materials, batch, issues).joinToString("~")
    }

    private fun fetchAll(session: CloudSession): RemoteData {
        val state = fetchRows("fush_cloud_domain_state", session, "&domain=eq.${encode(DOMAIN)}")
        return RemoteData(
            initialized = state.isNotEmpty(),
            recipes = fetchRows("fush_md_recipes", session),
            recipeComponents = fetchRows("fush_md_recipe_components", session),
            orders = fetchRows("fush_tx_production_orders", session),
            materials = fetchRows("fush_tx_production_materials", session),
            batches = fetchRows("fush_tx_production_batches", session),
            issues = fetchRows("fush_tx_production_issues", session),
            inventory = fetchRows("fush_inventory_snapshot", session),
        )
    }

    private fun fetchRows(table: String, session: CloudSession, extraFilter: String = ""): List<JSONObject> {
        val org = encode(session.requireOrganizationId())
        val path = "/rest/v1/$table?select=*&organization_id=eq.$org$extraFilter&limit=20000"
        return when (val response = request("GET", path, null, session.accessToken, null)) {
            is HttpResult.Error -> {
                if (response.code == 404) error(context.getString(R.string.cloud_inventory_production_schema_missing))
                error(apiErrorMessage(response))
            }
            is HttpResult.Ok -> {
                val array = runCatching { JSONArray(response.body) }.getOrElse { error(context.getString(R.string.cloud_inventory_production_invalid_response)) }
                List(array.length()) { array.getJSONObject(it) }
            }
        }
    }

    private fun upsertBatch(table: String, conflictColumns: String, rows: List<JSONObject>, session: CloudSession) {
        if (rows.isEmpty()) return
        rows.chunked(150).forEach { chunk ->
            val payload = JSONArray().apply { chunk.forEach { put(it) } }.toString()
            val path = "/rest/v1/$table?on_conflict=${encodeConflictColumns(conflictColumns)}"
            when (val response = request("POST", path, payload, session.accessToken, "resolution=merge-duplicates,return=minimal")) {
                is HttpResult.Ok -> Unit
                is HttpResult.Error -> error(apiErrorMessage(response))
            }
        }
    }

    private fun request(method: String, path: String, body: String?, bearerToken: String?, prefer: String?): HttpResult {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            if (!bearerToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
            if (!prefer.isNullOrBlank()) setRequestProperty("Prefer", prefer)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) HttpResult.Ok(code, responseBody) else HttpResult.Error(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiErrorMessage(error: HttpResult.Error): String {
        val json = runCatching { JSONObject(error.body) }.getOrNull()
        return json?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
            ?: "Cloud API error (${error.code})"
    }

    private fun fmt(value: Double): String = java.math.BigDecimal.valueOf(normalizeZero(value)).stripTrailingZeros().toPlainString()
    private fun normalizeZero(value: Double): Double = if (kotlin.math.abs(value) <= EPS) 0.0 else value
    private fun nearlyEqual(a: Double, b: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(a), kotlin.math.abs(b))
        return kotlin.math.abs(a - b) <= 0.000001 * scale
    }
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodeConflictColumns(columns: String): String = columns.split(',').joinToString(",") { encode(it.trim()) }
    private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
    private fun JSONObject.optNullableLong(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)
    private fun JSONObject.optNullableInt(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)

    private data class SnapshotKey(val warehouseCode: String, val itemCode: String, val lotNo: String?, val expiryDate: Long?)
    private data class SnapshotBalance(val key: SnapshotKey, val quantity: Double, val value: Double)
    private data class InventoryReconcileResult(val adjusted: Int, val unchanged: Int, val absoluteQtyDelta: Double)
    private data class InventoryMergeResult(
        val published: Int,
        val adjusted: Int,
        val unchanged: Int,
        val conflicts: Int,
        val absoluteQtyDelta: Double,
    )
    private data class Counters(
        var downloadedOrders: Int = 0,
        var unchangedOrders: Int = 0,
        var productionConflicts: Int = 0,
        var skippedLocalOrders: Int = 0,
        var inventoryAdjusted: Int = 0,
        var inventoryUnchanged: Int = 0,
        var inventoryConflicts: Int = 0,
    )
    private data class RemoteData(
        val initialized: Boolean,
        val recipes: List<JSONObject>,
        val recipeComponents: List<JSONObject>,
        val orders: List<JSONObject>,
        val materials: List<JSONObject>,
        val batches: List<JSONObject>,
        val issues: List<JSONObject>,
        val inventory: List<JSONObject>,
    )
    private sealed interface HttpResult {
        data class Ok(val code: Int, val body: String) : HttpResult
        data class Error(val code: Int, val body: String) : HttpResult
    }

    private companion object {
        const val DOMAIN = "INVENTORY_PRODUCTION_V165"
        const val EPS = 1e-8
        const val SEVERITY_BUSINESS = "BUSINESS"
        const val SEVERITY_TIMING = "TIMING"
        const val SEVERITY_DESCRIPTIVE = "DESCRIPTIVE"
        const val EMPTY_VALUE = "∅"
    }
}
