package com.example.myapplication.util

import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * serverErrorMessage 유닛 테스트
 *
 * 서버 에러 본문의 원문(JSON·영어·에러 코드)이 화면에 그대로 나오지 않고,
 * 한국어 문장만 골라 보여주는지 확인한다.
 */
class ServerErrorMessageTest {

    @Test
    fun `message 필드의 한국어 문장을 꺼낸다`() {
        val body = """{"error":"daily_limit_exceeded","message":"오늘의 무료 합성 횟수(3회)를 모두 사용했습니다.","reset_at":1760972400}"""
        assertEquals("오늘의 무료 합성 횟수(3회)를 모두 사용했습니다.", serverErrorMessage(body, 429))
    }

    @Test
    fun `detail 문자열을 꺼낸다`() {
        val body = """{"detail":"합성 요청이 이미 처리 중입니다."}"""
        assertEquals("합성 요청이 이미 처리 중입니다.", serverErrorMessage(body, 409))
    }

    @Test
    fun `detail 객체 안의 message를 꺼낸다`() {
        val body = """{"detail":{"error":"synthesis_failed","message":"합성에 실패했습니다. 크레딧은 환불되었습니다.","refunded":true}}"""
        assertEquals("합성에 실패했습니다. 크레딧은 환불되었습니다.", serverErrorMessage(body, 422))
    }

    @Test
    fun `slowapi 영어 원문은 보여주지 않는다`() {
        val body = """{"error":"Rate limit exceeded: 5 per 1 minute"}"""
        assertEquals("요청이 너무 많습니다. 잠시 후 다시 시도해주세요", serverErrorMessage(body, 429))
    }

    @Test
    fun `FastAPI 검증 오류 리스트는 보여주지 않는다`() {
        val body = """{"detail":[{"loc":["body","file"],"msg":"field required","type":"value_error.missing"}]}"""
        assertEquals("요청을 처리하지 못했습니다. 다시 시도해주세요", serverErrorMessage(body, 422))
    }

    @Test
    fun `JSON이 아닌 본문과 빈 본문은 상태 코드별 안내로 대신한다`() {
        assertEquals(
            "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요",
            serverErrorMessage("Internal Server Error", 502)
        )
        assertEquals(
            "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요",
            serverErrorMessage(null, 500)
        )
    }

    @Test
    fun `에러 코드만 있는 메시지는 기본 안내로 대신한다`() {
        assertEquals("요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요", displayableOrFallback("nofacedetected", 200))
        assertEquals("얼굴을 찾을 수 없습니다", displayableOrFallback("얼굴을 찾을 수 없습니다", 200))
    }
}
