package com.fush.erp.data

import android.content.Context
import androidx.room.Room

/**
 * Opens the production Room database once during application bootstrap so every historical
 * migration is completed before AppContainer exposes the database to any service or UI path.
 *
 * This is intentionally separate from AppContainer: Wave B revalidation can add the 35->38
 * migration chain without rewriting the large Central-owned container source.
 */
object AccountingWaveBRoomBootstrap {
    fun migrateBeforeContainer(context: Context) {
        val db = Room.databaseBuilder(
            context.applicationContext,
            FushDatabase::class.java,
            "fush_erp.db"
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33_SECURITY,
            MIGRATION_33_34_FIXED_ASSETS,
            MIGRATION_34_35_ACCOUNTING_P1,
            MIGRATION_35_36_ACCOUNTING_PRECISION,
            MIGRATION_36_37_JOURNAL_LINE_SEMANTICS,
            MIGRATION_37_38_INVENTORY_COST_LAYERS,
            MIGRATION_38_39_CUSTOMER_SETTLEMENT_DISCOUNT,
            MIGRATION_39_40_SALES_FREE_QUANTITY,
            MIGRATION_40_41_SUPPORT_MAINTENANCE_MODE,
            MIGRATION_41_42_SUPPORT_JOURNAL_PROVENANCE,
            MIGRATION_42_43_VENDOR_SUPPORT_PROVISIONING,
            MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE,
            MIGRATION_44_45_FOREIGN_KEY_INDEX_HARDENING,
            MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS,
            MIGRATION_46_47_SALES_ADDITIONAL_CHARGES,
            MIGRATION_47_48_SHIPMENT_COST_ALLOCATION,
            MIGRATION_48_49_SHIPMENT_SALES_LINE_LINK,
            MIGRATION_49_50_LOCAL_FX_ENGINE,
            MIGRATION_50_51_GEOGRAPHY_HIERARCHY,
            MIGRATION_51_52_MULTI_CURRENCY_TREASURY,
            MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS,
            MIGRATION_53_54_COMMERCIAL_TENANT_BINDING
        ).build()
        try {
            // Room is lazy; touching the writable handle forces validation/migration now.
            db.openHelper.writableDatabase
        } finally {
            db.close()
        }
    }
}
