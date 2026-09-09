package com.fush.erp.domain

object RiskControlMath {
    fun score(likelihood: Int, impact: Int): Int {
        require(likelihood in 1..5) { "Likelihood must be 1..5" }
        require(impact in 1..5) { "Impact must be 1..5" }
        return likelihood * impact
    }

    fun band(score: Int): String = when {
        score >= 20 -> "CRITICAL"
        score >= 15 -> "HIGH"
        score >= 8 -> "MEDIUM"
        else -> "LOW"
    }

    fun needsEscalation(score: Int): Boolean = score >= 15

    fun canApprove(requestedBy: Long, decisionBy: Long): Boolean = requestedBy != decisionBy
}
