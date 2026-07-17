package com.example.myapplication.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 분석 히스토리 DAO (Data Access Object)
 *
 * Room Database와 상호작용하는 인터페이스
 */
@Dao
interface AnalysisHistoryDao {

    /**
     * 새로운 분석 결과 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(history: AnalysisHistoryEntity): Long

    /**
     * 모든 분석 히스토리 조회 (최신순)
     */
    @Query("SELECT * FROM analysis_history ORDER BY timestamp DESC")
    fun getAllAnalysisHistory(): Flow<List<AnalysisHistoryEntity>>

    /**
     * 특정 analysisId로 조회
     */
    @Query("SELECT * FROM analysis_history WHERE analysisId = :analysisId LIMIT 1")
    suspend fun getAnalysisByServerId(analysisId: String): AnalysisHistoryEntity?

    /**
     * 가장 최근 분석 결과 조회
     */
    @Query("SELECT * FROM analysis_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAnalysis(): AnalysisHistoryEntity?

    /**
     * 특정 분석 삭제
     */
    @Delete
    suspend fun deleteAnalysis(history: AnalysisHistoryEntity)

    /**
     * 모든 히스토리 삭제
     */
    @Query("DELETE FROM analysis_history")
    suspend fun deleteAllHistory()

    /**
     * 오래된 히스토리 삭제 (30일 이상)
     */
    @Query("DELETE FROM analysis_history WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHistory(cutoffTime: Long)

    /**
     * 히스토리 개수 조회
     */
    @Query("SELECT COUNT(*) FROM analysis_history")
    fun getHistoryCount(): Flow<Int>
}
