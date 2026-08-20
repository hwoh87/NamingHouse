# 앱 코드는 리플렉션을 쓰지 않아 Compose·Kotlin 기본 규칙으로 충분하다 — 다만 의존성은 쓴다(아래 Room).

# 삼라에서 이식한 사주 엔진 — 크래시 리포트에서 모델 이름이 읽히도록 이름만 유지.
-keepnames class com.samramanshang.manseryeok.orrery.model.** { *; }

# kotlinx-datetime 이 참조하는 선택적 클래스 경고 억제
-dontwarn kotlinx.datetime.**

# AdMob 이 끌고 오는 WorkManager 는 Room 2.2.5 를 쓰는데, 그 시절 Room 규칙은
#   -keep class * extends androidx.room.RoomDatabase
# 로 클래스만 남기고 기본 생성자는 남기지 않는다. Room 은 _Impl 을 리플렉션으로
# newInstance() 하므로 R8 이 생성자를 지우면 InstantiationException 이 나고,
# WorkManagerInitializer → androidx.startup 이 앱 시작 자체를 죽인다.
# Room 2.4 부터의 규칙과 같게 생성자를 명시적으로 남긴다.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
