package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun byUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY isActive DESC, displayName, username")
    fun observeAll(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND isActive = 1")
    suspend fun activeAdminCount(): Int

    @Query("UPDATE users SET sessionVersion = sessionVersion + 1, mfaVerifiedSessionVersion = -1, updatedAt = :updatedAt WHERE role = :roleCode")
    suspend fun invalidateSessionsForRole(roleCode: String, updatedAt: Long)
}

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE isActive = 1 ORDER BY isBase DESC, code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE isActive = 1 ORDER BY isBase DESC, code")
    suspend fun allActive(): List<CurrencyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultsIgnore(rows: List<CurrencyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRate(rate: ExchangeRateEntity)

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :currencyCode AND effectiveAt <= :at ORDER BY effectiveAt DESC LIMIT 1")
    suspend fun latestRateAt(currencyCode: String, at: Long): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates ORDER BY effectiveAt DESC, currencyCode LIMIT :limit")
    fun observeRateHistory(limit: Int = 100): Flow<List<ExchangeRateEntity>>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY code")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY code")
    suspend fun allActive(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<AccountEntity>)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units WHERE isActive = 1 ORDER BY id")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units ORDER BY isActive DESC, id")
    fun observeAllIncludingInactive(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE isActive = 1 ORDER BY id")
    suspend fun allActive(): List<UnitEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<UnitEntity>): List<Long>

    @Query("SELECT * FROM units WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): UnitEntity?

    @Query("SELECT * FROM units WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): UnitEntity?

    @Query("SELECT COUNT(*) FROM items WHERE baseUnitId = :unitId AND isActive = 1")
    suspend fun activeBaseItemCount(unitId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: UnitEntity): Long

    @Update
    suspend fun update(row: UnitEntity)
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses WHERE isActive = 1 ORDER BY id")
    fun observeAll(): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses ORDER BY isActive DESC, id")
    fun observeAllIncludingInactive(): Flow<List<WarehouseEntity>>

    @Query("SELECT * FROM warehouses WHERE isActive = 1 ORDER BY id")
    suspend fun allActive(): List<WarehouseEntity>

    @Query("SELECT * FROM warehouses WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): WarehouseEntity?

    @Query("SELECT * FROM warehouses WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): WarehouseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: WarehouseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<WarehouseEntity>)

    @Update
    suspend fun update(row: WarehouseEntity)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE isActive = 1 ORDER BY nameAr")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY isActive DESC, nameAr")
    fun observeAllIncludingInactive(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE isActive = 1 ORDER BY nameAr")
    suspend fun allActive(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE isActive = 1")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes WHERE productItemId = :itemId AND status = 'ACTIVE'")
    suspend fun activeRecipeProductCount(itemId: Long): Int

    @Query("""
        SELECT COUNT(*)
        FROM recipe_components rc
        JOIN recipes r ON r.id = rc.recipeId
        WHERE rc.itemId = :itemId AND r.status = 'ACTIVE'
    """)
    suspend fun activeRecipeComponentCount(itemId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ItemEntity): Long

    @Update
    suspend fun update(row: ItemEntity)
}

@Dao
interface NumberSequenceDao {
    @Query("SELECT * FROM number_sequences WHERE sequenceKey = :key LIMIT 1")
    suspend fun byKey(key: String): NumberSequenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: NumberSequenceEntity)
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(row: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(rows: List<JournalLineEntity>)

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE sourceType = :sourceType AND sourceId = :sourceId AND status = 'POSTED' ORDER BY id DESC LIMIT 1")
    suspend fun bySource(sourceType: String, sourceId: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId ORDER BY id")
    suspend fun linesForEntry(entryId: Long): List<JournalLineEntity>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE sourceType = 'REVERSAL' AND sourceId = CAST(:entryId AS TEXT) AND status = 'POSTED'")
    suspend fun reversalCount(entryId: Long): Int

    @Query("""
        SELECT je.id AS id, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, je.currencyCode AS currencyCode,
               je.exchangeRate AS exchangeRate, je.sourceType AS sourceType,
               je.sourceId AS sourceId, je.createdBy AS createdBy,
               COALESCE(SUM(jl.debit), 0) AS debitTotal,
               COALESCE(SUM(jl.credit), 0) AS creditTotal,
               CASE WHEN EXISTS(
                   SELECT 1 FROM journal_entries r
                   WHERE r.sourceType = 'REVERSAL' AND r.sourceId = CAST(je.id AS TEXT) AND r.status = 'POSTED'
               ) THEN 1 ELSE 0 END AS isReversed
        FROM journal_entries je
        LEFT JOIN journal_lines jl ON jl.entryId = je.id
        WHERE je.status = 'POSTED'
        GROUP BY je.id
        ORDER BY je.entryDate DESC, je.id DESC
        LIMIT 500
    """)
    fun observeHeaders(): Flow<List<JournalHeaderRow>>

    @Query("""
        SELECT je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, je.sourceType AS sourceType,
               a.id AS accountId, a.code AS accountCode, a.nameAr AS accountNameAr,
               a.type AS accountType, jl.debit AS debit, jl.credit AS credit, jl.memo AS memo
        FROM journal_lines jl
        JOIN journal_entries je ON je.id = jl.entryId
        JOIN accounts a ON a.id = jl.accountId
        WHERE je.id = :entryId AND je.status = 'POSTED'
        ORDER BY jl.id
    """)
    suspend fun detailsForEntry(entryId: Long): List<JournalDetailRow>

    @Query("""
        SELECT je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, je.sourceType AS sourceType,
               a.id AS accountId, a.code AS accountCode, a.nameAr AS accountNameAr,
               a.type AS accountType, jl.debit AS debit, jl.credit AS credit, jl.memo AS memo
        FROM journal_lines jl
        JOIN journal_entries je ON je.id = jl.entryId
        JOIN accounts a ON a.id = jl.accountId
        WHERE je.status = 'POSTED' AND je.entryDate BETWEEN :fromDate AND :toDate
        ORDER BY je.entryDate, je.id, jl.id
    """)
    suspend fun reportDetails(fromDate: Long, toDate: Long): List<JournalDetailRow>

    @Query("""
        SELECT je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, je.sourceType AS sourceType,
               a.id AS accountId, a.code AS accountCode, a.nameAr AS accountNameAr,
               a.type AS accountType, jl.debit AS debit, jl.credit AS credit, jl.memo AS memo
        FROM journal_lines jl
        JOIN journal_entries je ON je.id = jl.entryId
        JOIN accounts a ON a.id = jl.accountId
        WHERE je.status = 'POSTED'
          AND je.entryDate BETWEEN :fromDate AND :toDate
          AND je.sourceType <> 'YEAR_END_CLOSE'
          AND NOT (
              je.sourceType = 'REVERSAL'
              AND EXISTS (
                  SELECT 1 FROM journal_entries original
                  WHERE original.id = CAST(je.sourceId AS INTEGER)
                    AND original.sourceType = 'YEAR_END_CLOSE'
              )
          )
        ORDER BY je.entryDate, je.id, jl.id
    """)
    suspend fun profitLossDetails(fromDate: Long, toDate: Long): List<JournalDetailRow>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE status = 'POSTED'")
    fun observePostedCount(): Flow<Int>
}
