package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * HairMe 앱의 Room Database
 *
 * @Database: Room이 이 클래스를 데이터베이스로 인식
 * - entities: 데이터베이스에 포함될 테이블 (Entity 클래스)
 * - version: 데이터베이스 버전 (스키마 변경 시 증가)
 * - exportSchema: 스키마를 파일로 내보낼지 여부
 */
@Database(
    entities = [AnalysisHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HairMeDatabase : RoomDatabase() {

    /**
     * DAO 인스턴스를 제공하는 추상 메서드
     * Room이 자동으로 구현
     */
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
}
