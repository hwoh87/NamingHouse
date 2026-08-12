# 앱은 리플렉션을 쓰지 않아 Compose·Kotlin 기본 규칙으로 충분하다.

# 삼라에서 이식한 사주 엔진 — 크래시 리포트에서 모델 이름이 읽히도록 이름만 유지.
-keepnames class com.samramanshang.manseryeok.orrery.model.** { *; }

# kotlinx-datetime 이 참조하는 선택적 클래스 경고 억제
-dontwarn kotlinx.datetime.**
