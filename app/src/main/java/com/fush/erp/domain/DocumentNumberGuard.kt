package com.fush.erp.domain

sealed interface DocumentNumberReservationResult {
    data class Reserved(val reservationId: String) : DocumentNumberReservationResult
    data object LocalOnly : DocumentNumberReservationResult
    data class InUse(val reservedBy: String?, val reservedDevice: String?) : DocumentNumberReservationResult
    data class Failure(val message: String) : DocumentNumberReservationResult
}

interface DocumentNumberGuard {
    suspend fun reserve(localUserId: Long, documentType: String, documentNo: String): DocumentNumberReservationResult
}

object LocalOnlyDocumentNumberGuard : DocumentNumberGuard {
    override suspend fun reserve(localUserId: Long, documentType: String, documentNo: String) =
        DocumentNumberReservationResult.LocalOnly
}
