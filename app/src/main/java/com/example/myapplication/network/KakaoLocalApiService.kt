package com.example.myapplication.network

import androidx.annotation.Keep
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 카카오 로컬 API 서비스
 *
 * 주변 미용실 검색을 위한 API 인터페이스
 *
 * API 문서: https://developers.kakao.com/docs/latest/ko/local/dev-guide
 */
interface KakaoLocalApiService {

    /**
     * 키워드로 장소 검색
     *
     * Authorization 헤더는 RetrofitClient의 인터셉터에서 자동으로 추가됩니다.
     *
     * @param query 검색 키워드 (예: "미용실", "헤어샵")
     * @param x 중심 좌표의 X (경도, longitude)
     * @param y 중심 좌표의 Y (위도, latitude)
     * @param radius 검색 반경 (미터 단위, 최대 20000)
     * @param category 카테고리 그룹 코드 (MT1: 대형마트, CS2: 편의점, HP8: 병원, BK9: 은행, etc.)
     * @param size 결과 페이지 크기 (1~15, 기본값 15)
     * @param sort 정렬 방식 (distance: 거리순, accuracy: 정확도순)
     */
    @GET("v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Query("query") query: String,
        @Query("x") x: Double? = null,
        @Query("y") y: Double? = null,
        @Query("radius") radius: Int? = null,
        @Query("category_group_code") category: String? = null,
        @Query("size") size: Int = 15,
        @Query("sort") sort: String = "distance" // distance 또는 accuracy
    ): KakaoLocalSearchResponse
}

/**
 * 카카오 로컬 API 응답 데이터
 */
@Keep
data class KakaoLocalSearchResponse(
    val meta: Meta,
    val documents: List<Place>
)

@Keep
data class Meta(
    val total_count: Int,           // 검색된 전체 문서 수
    val pageable_count: Int,        // 페이징 가능한 문서 수
    val is_end: Boolean,            // 현재 페이지가 마지막인지 여부
    val same_name: RegionInfo?      // 질의어의 지역 정보
)

@Keep
data class RegionInfo(
    val region: List<String>,       // 질의어에서 인식된 지역 리스트
    val keyword: String,            // 질의어에서 지역 정보를 제외한 키워드
    val selected_region: String     // 인식된 지역 리스트 중 선택된 지역
)

@Keep
data class Place(
    val id: String,                 // 장소 ID
    val place_name: String,         // 장소명, 업체명
    val category_name: String,      // 카테고리 이름
    val category_group_code: String,// 중요 카테고리만 그룹핑한 코드
    val category_group_name: String,// 중요 카테고리만 그룹핑한 이름
    val phone: String,              // 전화번호
    val address_name: String,       // 전체 지번 주소
    val road_address_name: String,  // 전체 도로명 주소
    val x: String,                  // X 좌표 (경도, longitude)
    val y: String,                  // Y 좌표 (위도, latitude)
    val place_url: String,          // 장소 상세페이지 URL (카카오맵)
    val distance: String            // 중심좌표까지의 거리 (미터 단위, x,y 파라미터를 준 경우에만 존재)
)
