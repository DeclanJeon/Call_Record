# Call_Record — 설계 문서

> 목표: Cube ACR 유료 기능(자동 통화 녹음) 중 **"통화 시 자동 녹음 → 녹음 파일 생성 → 파일을 읽어 내용(테스트 리포트/전사)을 텍스트 파일로 남김"** 만을
> 무료·오픈소스(GPL) 기반으로 재구성한 자체 앱 **Call_Record**.

---

## 0. 결론 요약 (TL;DR)

| 항목 | 결정 |
|---|---|
| 베이스 코드 | **ShizuCallRecorder** (GPL-3.0) — 양방향(상대+내 음성) 녹음, 루트 불필요, Shizuku로 ADB 권한 사용 |
| 루트 필요? | **아니오** (Shizuku 사용 시) |
| 유료 API? | **없음** (전부 로컬 처리) |
| 새로 개발하는 부분 | ① 앱 이름/패키지 정리 ② **녹음 후 자동 "테스트 파일 생성" 파이프라인** ③ 최소 UI |
| 빌드 | **GitHub Actions** (로컬에 JDK/SDK 없음 → fork + workflow로 APK 자동 빌드) |
| 배포 | 자체 빌드 APK 직접 설치(사이드로드). F-Droid 배포도 가능 |
| 남는 확인 필요 | ① 기기 모델/안드로이드 버전 ② 루트 여부 ③ Shizuku 사용 가능 여부 ④ "내용을 파일로"의 정확한 의미(통역 텍스트 vs 음성 데이터) |

---

## 1. 목표 / 비목표

### 목표 (Must)
1. 안드로이드 폰에서 통화(수신·발신)하면 **자동으로** 녹음 시작/종료.
2. 녹음 결과가 **파일**로 남음 (Opus/OGG 또는 AAC/M4A).
3. 녹음 종료 후 앱이 해당 파일을 **읽어** 요약/통역/통계 같은 **텍스트 파일**을 함께 생성 ("테스트 파일").
4. **무료·무결제·무계정**, 데이터는 폰 밖으로 나가지 않음.
5. **단순한 시스템**: 사용자 관점 복잡도 최소화(설정 3~4개, 화면 1~2개).

### 비목표 (Won't)
- 클라우드 백업, 계정, 결제, 광고, 통계/크립토(Block-Call) 같은 Cube ACR의 유료 기능
- VoIP/WhatsApp 앱 통화 녹음 (선택적으로 확장 가능하나 v1 제외)
- 통화 내용 요약/AI 요약 (v1 제외, v3 선택)
- 통화 자동 answering(자동 응답) 기능

---

## 2. 레퍼런스 분석 — Cube ACR XAPK 에서 가져올 것/버릴 것

XAPK 구조(확인 완료): `com.catalinagroup.callrecorder.apk`(24MB) + 언어/ABI split 18개. 버전 2.4.289, minSdk 29, targetSdk 36.

### 요청 권한 분석 (Cube ACR)
| 권한 | 용도 | 우리 앱 판단 |
|---|---|---|
| `RECORD_AUDIO` | 마이크/통화음 녹음 | **필요** |
| `READ_PHONE_STATE` | 통화 상태 감지 | **필요** |
| `READ_CALL_LOG` | 발신/수신 번호, 통화내역 | 필요(번호 표시용). Android 10+ 하드 제한 존재 → 아래 3장 참조 |
| `MANAGE_OWN_CALLS` / `InCallService` | 전화번호 확보 | **권장** (Shell appops로 권한 부여) |
| `FOREGROUND_SERVICE_MICROPHONE` | 녹음 FGS | **필요** (Android 14+ FGS 타입 제한) |
| `POST_NOTIFICATIONS` | 녹음 중 알림 | 필요 |
| `INTERNET` / `BLUETOOTH` / `LOCATION` / `NFC` / `AD_ID` / `VENDING.BILLING` | 광고·분석·결제·보조 기능 | **전부 제거** |
| `SYSTEM_ALERT_WINDOW` | 플로팅 컨트롤 | 제거(선택) |
| `ACCESS_FINE_LOCATION` 등 | 불필요 | 제거 |

### 가져올 기능 (v1 스코프)
- 자동 녹음 on/off, 제외 번호 규칙(선택)
- 파일명 규칙: `yyyyMMdd_HHmmss_방향_번호`
- 상시 알림 없이 동작(녹음 중에만 알림) — BCR/CallVault와 동일한 UX 철학
- 녹음 목록 + 재생 + "테스트 파일 만들기" 버튼
- 화면 잠금/보안은 v1 제외(단, 통화 녹음은 민감 → 앱 잠금 옵션은 v2)

### 버릴 기능
결제(IAP), 광고 SDK, Google Play Billing, 위치, NFC, 크립토, 드라이브 백업, 인앱 업데이트 서버, 원격 분석

---

## 3. 오픈소스 후보 평가 (GitHub 실측, 2026 기준)

| 저장소 | ★ | 방식 | 양방향 음성 | 복잡도 | 비고 |
|---|---|---|---|---|---|
| **chenxiaolong/BCR** | 2951 | 루트/시스템앱 | O (VOICE_CALL) | 낮음 | Android 9+, 형식 다중(OGG/FLAC/WAV/M4A/AMR), **루트 필수** |
| **kitsumed/ShizuCallRecorder** | 1786 | Shizuku(ADB) | O | 낮음 | GPL-3.0, 스크립피 서버 내장, `appops`/`cmd role` 자동 부여, UI 단순. **베이스 후보** |
| **madkongo/CallVault** | 145 | 내장 ADB 페어링 / Shizuku | O | **높음** | ShizuCallRecorder 포크 + on-device Whisper.cpp 전사 + LLM 요약 + Drive 백업. 빌드에 NDK/CMake 필요(무거움) |
| aykuttasil/CallRecorder | 790 | 일반앱(MIC/VOICE_COMMUNICATION) | △ (기기 의존) | 매우 낮음 | 2023년 이후 미갱신, Android 10+ 제약 미해결 → 참고자료로만 |
| rjeffm/CallRecorder | 57 | 일반앱 | △ | 낮음 | 2016~2022, 옛 API → 참고자료로만 |

**선택: ShizuCallRecorder를 베이스로 "Call_Record"로 재구성.**
이유: (1) 루트 불필요 (2) GPL-3.0 (3) 코드 규모가 적어 유지보수·이해 가능 (4) 이미 "양방향 + 자동"이라는 핵심 요구 충족 (5) 네이티브 코드·NDK 불필요 → 빌드/수정 용이.

> CallVault의 전사(Whisper.cpp) 기능은 v2에서 **참고 구현**으로 가져오되, NDK/CMake + 모델(수백 MB) 부담 때문에 v1에는 넣지 않는다.

---

## 4. 기술 제약 (왜 "일반 앱"으로는 안 되는가) — 설계의 핵심 근거

1. **Android 10(API 29) 이후**, 일반 앱은 `MediaRecorder.AudioSource.VOICE_CALL / VOICE_DOWNLINK / VOICE_UPLINK` 를 사용할 수 없다.
   - 필요한 권한 `CAPTURE_AUDIO_OUTPUT` 은 `signature|privileged` 등급 → 서명된 시스템 앱 또는 **shell(uid 2000)** 만 보유.
   - 그래서 **루트(시스템앱)** 또는 **ADB/Shizuku(shell 권한)** 가 필수. 이것이 사실상 표준 해법이다.
2. **VOICE_COMMUNICATION/MIC 만 쓰는 일반앱**은 마이크만 잡고 상대 음은 잡히지 않거나(에코 상쇄로 소거) 품질이 급락한다. 일부 기기(특히 Pixel 계열)만 양방향.
3. **번호(전화번호) 확보**: Android 10+ 에서는 `READ_CALL_LOG` 이 하드 제한으로 일반 앱이 못 읽는다.
   → 해결책: `InCallService`(电信 `MANAGE_ONGOING_CALLS`)를 사용하고, Shell 권한으로 `appops set <pkg> MANAGE_OWN_CALLS allow` + `cmd role add-role-holder ... DIALER` 등을 부여(ShizuCallRecorder가 이미 구현).
4. **Foreground Service**: Android 14+ 는 `FOREGROUND_SERVICE_MICROPHONE` / `specialUse` 사유 명시 필수. 녹음 중 반드시 FGS.
5. **직접 부팅(Direct Boot)**: 재부팅 직후 잠금화면 상태에서의 통화 녹음은 `deviceProtectedStorage` + `directBootAware` 필요(BCR가 구현, v1은 선택).

→ 이 제약이 곧 "루트 vs Shizuku"라는 분기를 결정하며, 사용자에게는 **무료 Shizuku 설치(1회, 무선 ADB 페어링)** 만 요구한다.

---

## 5. Call_Record 아키텍처

### 5.1 전체 흐름

```
[통화 발생]
   │
   ├─ (A) PhoneStateReceiver  : PHONE_STATE 브로드캐스트 (RINGING/OFFHOOK/IDLE)
   └─ (B) InCallService       : Telecom 으로부터 통화번호·방향 획득 (Android 10+ 필수 경로)
   │
   ▼
[CallDetectionOrchestrator]  ← 중복 이벤트 제거, 세션 관리(발신/수신, 통화중 재시작)
   │  ※ 화면 잠금 상태에서도 동작해야 함 → Shizuku/Shell 프로세스가inz의 InCallService 사용
   ▼
[RecordingForegroundService]  (FGS, type=mediaProjection/microphone|specialUse)
   │  시작: ShellService(uid 2000) 경유 → scrcpy-server.audio_direct_capture
   │        audio_source=voice-call (양방향) / mic-voice-communication (폴백)
   │  저장: SAF(SAF) 선택 폴더 → CallRecord_YYYYMMDD_HHmmss_방향_번호.ogg (Opus 24kbps) 또는 .m4a
   ▼
[통화 종료] → 파일 finalize (헤더 패치, 길이 확정)
   │
   ▼
[VerificationStage]  ★ 우리 앱이 새로 만드는 핵심 기능
   │ 1) 녹음 파일을 디코딩(로컬)하여 메타데이터 추출
   │ 2) 음성 구간(voice activity) 검출 + RMS/피크 통계
   │ 3) 텍스트 파일 생성  →  CallRecord_..._.txt  (사람이 읽는 리포트)
   │ 4) (v2, 옵션) Whisper.cpp on-device 전사 → .srt / .txt
   ▼
[Recents 화면] : 녹음 목록 + 재생 + "테스트 파일 열기" + "다시 생성"
```

### 5.2 모듈 구성 (패키지 `com.declan.callrecord`)

| 모듈 | 역할 | 베이스 출처 |
|---|---|---|
| `core/call` | 통화 이벤트 감지 오케스트레이터, 세션 상태 머신 | ShizuCallRecorder `services/callDetection/*` |
| `core/record` | FGS, 녹기 엔진, 파일 muxing(OGG/Opus, M4A/AAC) | `services/recording/*`, `integrations/scrcpy/*` |
| `core/shell` | Shizuku 사용자 서비스 바인딩, `appops`/`cmd role` 실행 | `services/shell/*` + AIDL |
| `feature/verify` | ★ 녹음 파일 분석 + 테스트 파일 생성 | **신규 개발** |
| `feature/recents` | 녹음 목록/재생/내보내기 | ShizuCallRecorder `ui/screens/*` 경량 재작성 |
| `feature/settings` | 저장폴더, 자동녹음 on/off, 통화녹음 고지음, 알림, 언어 | 경량 재작성 |
| `core/media` | MediaPlayer 재생, SAF helper | `system/storage/SafHelper.kt` |

### 5.3 권한 / 컴포넌트 (우리 앱 매니페스트 초안)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" tools:ignore="SmsAndCallLogPolicy" />
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />   <!-- InCallService용, appops로 부여 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="PackageVisibilityPolicy" /> <!-- 상대방拨号앱 판별 -->
<!-- 없음: INTERNET / LOCATION / BLUETOOTH / NFC / AD_ID / BILLING -->
```

컴포넌트: `MainActivity`(단일 화면 + Compose Navigation), `RecordingForegroundService`, `InCallService`, `PhoneStateReceiver`, `ShellService`(Shizuku user service, `@Keep` 필수), SAF `DocumentFile` provider.

### 5.4 저장 산출물 규약

```
/storage/emulated/0/CallRecord/            (SAF로 사용자가 선택, 기본: Documents/CallRecord)
├── 20261020_231405_in_01012345678.ogg    (Opus 24kbps mono 48kHz)   ← 원본 녹음
├── 20261020_231405_in_01012345678.txt    ← ★ 테스트 리포트 (자동 생성)
├── 20261020_231405_in_01012345678.json   ← ★ 기계용 메타 (BCR 호환 옵션)
└── 20261020_231405_in_01012345678.srt    ← (v2) 전사 결과
```

**★ 테스트 리포트(`.txt`) 스펙** — 사람이 눈으로 확인하는 것이 목적:
```
Call_Record — 녹음 검증 리포트
========================================
파일        : 20261020_231405_in_01012345678.ogg
생성        : 2026-10-20 23:14:05
통화        : 수신(incoming) / 010-1234-5678
통화길이    : 182 초
녹음길이    : 179.4 초 (헤더 보정 후)
포맷        : OGG/Opus, 48000 Hz, 1 ch, 24 kbps
파일크기    : 538,104 bytes

[음성 분석]
전체 RMS(dBFS) : -26.4
최대 피크      : -3.1 dBFS
음성 구간      : 23 / 179.4 초 (12.8%)  [임계 -45 dBFS, 300ms 홀드]
최대 연속무음  : 41.2 초 (구간 00:52.3 ~ 01:33.5)
[판정] 양방향 통화 음성으로 보임 (음성 구간 검출됨)
※ 상대 음성 직접 분리 판정은 불가(단일 믹스). 스테레오 분리는 미지원.
```
※ "양방향" 판정은 **원본 오디오가 마이크/통화 mixtures인지**에 의존. 루트(BCR) 경로에서는 채널 분리 가능.

---

## 6. 구현 계획 (Milestone)

### M0 — 사전 준비 (0.5일)
- [ ] 기기 정보 수집: 모델, Android 버전, 루트 여부, Shizuku 설치 가능 여부
- [ ] Shizuku 설치(루트면 `su`로, 아니면 개발자옵션 → wireless debugging → Shizuku 페어링)
- [ ] 참고 저장소 3종 클론(설계/비교용): BCR, ShizuCallRecorder, CallVault
- [ ] 신규 Git 저장소 생성: `Call_Record` (package `com.declan.callrecord`)

### M1 — 포크 & 정리 (1~2일)
- [ ] ShizuCallRecorder → Call_Record 포크 (패키지명/앱명/아이콘/색상 교체)
- [ ] 제거: 스폰서 화면, 연락처 선택기(선택적 유지), 파일명 포맷 다이얼로그, 오버레이(플로팅 창), 연락처 퍼미션(READ_CONTACTS)
- [ ] 제거: `INTERNET` 등 불필요 퍼미션, `QUERY_ALL_PACKAGES` 최소화
- [ ] 유지: `scrcpy-server` 자동 다운로드/무결성 검증, ShellService, `appops` 부여 로직
- [ ] 문서/라이선스: GPL-3.0 유지, 원저작자 크레딧 명시(NOTICE)

### M2 — 검증/테스트 파일 파이프라인 (핵심 신규, 2~3일)
- [ ] `AudioProbe`: OGG/Opus·M4A 파일 헤더 파싱 (컨테이너/코덱/샘플레이트/채널/길이) — 외부 라이브러리 없이 직접 구현
- [ ] `WavDecoder` → 16kHz mono PCM로 디코딩 (Opus: opusdec JNI 필요 시 **대신 시스템 MediaCodec/Extractor 경유** 사용 → NDK 회피)
  - ※ 설계 결정: **Opus 디코딩에 NDK를 쓰지 않고**, `MediaExtractor`+`MediaCodec`(시스템 제공)로 16k PCM 추출한다. 불필요한 네이티브 빌드 비용 0.
- [ ] `VoiceActivityDetector`: 20ms 프레임 RMS → 임계 기반 음성/무음 구간 판정, 요약 통계
- [ ] `ReportWriter`: 위 스펙의 `.txt` + `.json` 생성 (녹음과 같은 폴더, 동일 basename)
- [ ] 파이프라인 트리거: (a) 녹음 종료 자동 (b) 목록 화면의 "다시 생성" 버튼 (c) 외부 파일 가져오기(ShareImport)

### M3 — UI/설정 최소화 (1~2일)
- [ ] 단일 화면: 홈(자동 녹음 토글, 상태) / 목록(녹음 + 테스트 파일 배지) / 설정(저장 폴더, 고지음, 포맷)
- [ ] 통화 시작 시 상대방에게 "녹음 중" 고지음(1회) 옵션 — **법적·예의상 기본 ON 권장**
- [ ] 녹음 중 알림(상시 알림 아님) + "아직 녹음 중" 상태 표시
- [ ] 재부팅 후에도 동작(직접 부팅 대응은 v2)

### M4 — 빌드 & 배포 (0.5일)
- [ ] GitHub Actions 워크플로우: JDK 17 + Android SDK 36 + Gradle Wrapper → `app-release.apk` 자동 생성(무료)
- [ ] 로컬 키 생성 + 서명, 키는 로컬 보관(서명키 유실 시 업데이트 불가)
- [ ] 설치 스크립트/안내 문서 작성

### M5 — 검증 (0.5~1일)
- [ ] 테스트 시나리오 8종 통과 (7장)

### M6 (선택) — v2: 온디바이스 전사
- [ ] Whisper.cpp JNI 도입(CallVault의 `whispercv.cpp` 참고) + 모델 파일 관리(설치 후 다운로드)
- [ ] 전사 → `.srt`/`.txt` 자동 생성, 요약은 v3로 보류

---

## 7. 테스트 시나리오 (M5)

| # | 시나리오 | 기대 결과 |
|---|---|---|
| 1 | 수신 통화 연결 | 자동 녹음 시작, 파일 생성 |
| 2 | 발신 통화 연결 | 자동 녹음 시작, 파일명에 `out` |
| 3 | 통화 중 잠금화면 전환 | 녹음 유지 |
| 4 | 통화 종료 | 파일 finalize + **`.txt` 리포트 자동 생성** |
| 5 | 상대가 30초 이상 무응답 | 리포트에 "무음 구간 30초 초과" 경고 표기 |
| 6 | 앱 강제 종료 후 재실행 | 서비스 재시작, 다음 통화 정상 녹음 |
| 7 | 재부팅 후 첫 통화(잠금 상태) | v1: 수동 시작 필요 안내 / v2: 자동 |
| 8 | Shizuku 미실행 상태 | "Shizuku 필요" 안내 화면 + 설치 가이드 |
| 9 | 블루투스 헤드셋 통화 | 양방향 녹음 확인 |
| 10 | 저장 폴더 접근 불가(SAF 취소) | 재시도 UI, 크래시 없음 |

---

## 8. 빌드 & 배포 세부

### 8.1 로컬 빌드 (구축 완료 ✅)
루트 권한 없이 사용자 홈에 설치했다.
- JDK 17 (Temurin) → `~/tools/jdk-17.0.20.1+1`
- Android SDK 36 (platform-tools, platforms;android-36, build-tools;36.0.0, ndk;27.2.12479018, cmake;3.22.1) → `~/Android/Sdk`
- Gradle 9.4.1 → `~/tools/gradle-9.4.1` (래퍼 JAR는 저장소에 포함)
- 환경변수는 `source ~/tools/env.sh`
- 빌드: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (약 22.7MB, 서명됨)
- 서명 키: `keystore/call-record.jks` (저장소 미커밋, **분실 시 업데이트 불가**)
- 대안(GitHub Actions)은 그대로 사용 가능 — CI 워크플로는 후속 추가 예정

### 8.2 서명
- 자체 debug/release 키 생성(keystore). 분실 대비 백업 필수(업데이트 시 동일 서명 필요).
- 서드파티 라이브러리: Shizuku API(AGPL-3.0), scrcpy-server(Apache-2.0, 바이너리 SHA256 검증), libphonenumber(Apache-2.0, 선택). GPL-3.0 본체 배포 시 소스 공개 의무 → 개인 사용 범위에서는 문제 없으나, APK 공개 배포 시 저장소 공개.

### 8.3 설치 경로
- 직접 APK 설치(사이드로드) — Play 정책 무관
- F-Droid 제출(선택, 메타데이터 작성 필요)

---

## 9. 리스크 & 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| Shizuku가 안 돌아감(재부팅 후 종료) | 자동 녹음 안 됨 | 앱 내 상태 감지 배너 + `adb shell`/무선 ADB 재페어링 가이드 |
| 기기 제조사 롤(拨号앱) 충돌 | InCallService가 호출되지 않음 | PhoneStateReceiver 폴백 경로 유지(양쪽 다 구현) |
| 상대 음성이 파일에 안 잡힘 | 핵심 기능 실패 | audio_source 폴백 체인: `voice-call` → `mic-voice-communication` → `mic`; 리포트로 "음성 미검출" 경고 |
| 배터리 최적화로 FGS 종료 | 녹음 중 끊김 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + 제조사별 최적화 해제 안내 |
| OEM 킬러앱 처리 | 통화 중 프로세스 종료 | Shizuku 쉘 프로세스(별도 프로세스)에서 녹음 → 앱 죽어도 녹음 유지 |
| Android 16/17 API 변경 | 동작 불가 | ShizukuCallRecorder가 지속 업데이트 중 → regularly merge |
| **법적: 통신비밀보호법** | 형사처벌 가능성 | 아래 10장 참고 — **본인/상대 동의 필수**, 고지음 기본 ON |

---

## 10. 법적·윤리적 유의 (한국)

- **통신비밀 보호법 제3조**: 대화 내용 축하·감청 시 당사자 중 1인의 동의가 있어야 하고, **3인 이상**(발신자·수신자·제3자)이 있으면 동의 없이는 punishes 가능(5년 이하 징역 또는 1천만원 이하 벌금). 즉 **1:1 통화라도 상대방 동의가 없으면 형사처벌 가능** → 앱 사용 시 **양쪽 모두 녹음 사실을 인지**하는 것이 원칙(통화 시작 시 고지음/안내).
- 일부 업체(통신사 유심 앱 등)는 통화 녹음을 **탐지하면 통화 종료**하거나 약관상 금지 → 실사용 통화 대상을 고려.
- 앱 자체는 "합법적 용도(내 통화 메모 등)"를 전제로 설계하고, **녹음 고지음/화면 안내를 기본 ON**으로 둔다.
- 저장 파일은 개인정보(통화 내용) → 앱 잠금(생체/ PIN) 옵션을 v2에 추가.
- 저장 위치는 앱 전용 폴더가 아니라 **사용자 지정 폴더(SAF)** → 사용자가 직접 백업/삭제/회수 가능.

---

## 11. 확정된 결정 (2026-10-21 사용자 확정)

| # | 질문 | 결정 | 반영 |
|---|---|---|---|
| 1 | 호환 범위 | **Android 11+ 전부**(2020년형 기종 대부분이 Android 12+ 로 업데이트됨) | `minSdk = 30` 유지. Android 10은 scrcpy 오디오 캡처가 API 30 미만에서 비활성되어 **미지원**(v1.1에서 자체 캡처 구현 검토) |
| 2 | 루트 | **루트 불필요**, Shizuku 사용 | ShizuCallRecorder 기반 그대로 |
| 3 | Shizuku | **가능** | 베이스 확정 |
| 4 | "내용을 파일로" | **통화 내용을 텍스트로 전사** | v1에 포함. 엔진은 **Vosk**(`vosk-model-small-ko-0.22`, 40MB, 네이티브 빌드 불필요) 채택 — Whisper.cpp는 정확도 상향 옵션으로 v2 |
| 5 | 백업 | **Google 계정(Google Drive) 자동 백업** | **SAF 폴더 선택 방식** 채택 — 시스템 폴더 선택기에서 "Google Drive"를 고르면 끝나며 OAuth/클라우드 프로젝트 불필요. v1은 `DriveBackup`(파일 복사 미러링) |
| 6 | 양방향 | **양방향 필수** | `voice-call` 소스(상대+내음) 기본값, 실패 시 `mic-voice-communication` 폴백 |

### v1에 포함된 것
- 자동 통화 녹음(수신/발신) + 양방향 캡처
- 녹음 종료 → 자동 검증 리포트(`.txt` + `.json`) 생성
- 온디바이스 전사(Vosk) → 텍스트 + `.srt`
- Google Drive 폴더 자동 백업
- 녹음 목록 화면(검증/전사 재실행 버튼)

### v1에서 제외(향후)
- 통화 녹음 고지음(비프) — **법적 안전을 위해 다음 버전에서 기본 ON으로 추가 예정**
- 앱 잠금(생체/PIN) — 통화 내용은 민감정보
- Whisper.cpp 엔진(전사 정확도 상향)
- Android 10 대응 자체 캡처 구현

---

## 12. 부록: 참고 URL

- ShizuCallRecorder: https://github.com/kitsumed/ShizuCallRecorder (GPL-3.0)
- BCR (루트용): https://github.com/chenxiaolong/BCR (GPL-3.0)
- CallVault (포크·전사): https://github.com/madkongo/CallVault (GPL-3.0)
- Shizuku: https://github.com/RikkaApps/Shizuku / thedjchi 포크 https://github.com/thedjchi/Shizuku
- scrcpy (audio_direct_capture): https://github.com/Genymobile/scrcpy
- Android AudioSource 제약: https://developer.android.com/reference/android/media/MediaRecorder.AudioSource
- FGS 타입 규칙: https://developer.android.com/about/versions/14/changes/fgs-types-required


---

## 13. 구현 현황 (2026-10-21)

### 완료
| 항목 | 위치 | 상태 |
|---|---|---|
| 리브랜딩 (패키지 `com.declan.callrecord`, 앱명 "Call Record") | 전체 | ✅ 빌드/서명 완료 |
| 검증 파이프라인: MediaCodec PCM 디코딩 → 모노 16kHz → VAD 분석 | `verify/PcmDecoder.kt`, `verify/AudioAnalyzer.kt` | ✅ |
| 리포트 작성(사람용 `.txt` / 기계용 `.json`) | `verify/ReportWriter.kt` | ✅ |
| 녹음 종료 자동 후처리 훅 | `services/recording/RecordingForegroundService.kt#runPostProcessing` | ✅ |
| 온디바이스 전사(Vosk) + 모델 다운로드/관리 | `transcription/*` | ✅ (모델 40MB 최초 1회 다운로드) |
| 자막(`srt`) 출력 | `verify/RecordingPostProcessor.kt` | ✅ |
| Google Drive 백업(SAF 미러링) | `backup/DriveBackup.kt` | ✅ |
| 녹음 목록 화면(검증/전사 재실행) | `ui/screens/RecordingsScreen.kt` | ✅ |
| 설정 섹션(리포트/전사/백업 토글, 모델 받기) | `ui/screens/CallRecordSection.kt` | ✅ |
| ABI 필터(arm64-v8a, armeabi-v7a)로 APK 크기 축소 | `app/build.gradle.kts` | ✅ 105MB → 22.7MB |

### 남은 작업
1. **실기기 검증** (핵심): Shizuku 설치 → 통화 → 녹음 → `.txt`/`.srt` 생성 → Drive 백업 확인
2. 통화 녹음 **고지음(비프)** 구현 (법적 안전, 기본 ON)
3. Android 10 대응(쉘 권한 기반 자체 AudioRecord + `VOICE_CALL` 캡처)
4. app 잠금(생체/PIN) — 통화 내용 보호
5. Whisper.cpp 엔진(선택) — 전사 정확도 상향
6. CI 워크플로(GitHub Actions) 및 F-Droid 메타데이터


---

## 14. v0.2.0 추가 기능 (사용자 요청 반영)

### 14.1 녹음 고지음
| 항목 | 내용 |
|---|---|
| 목적 | 상대방이 "녹음 중"을 인지하게 하여 통신비밀보호법상 동의 요건을 충족 |
| 구현 | `notify/AnnouncementTone` — `AudioTrack`(`MODE_IN_COMMUNICATION`, `USAGE_VOICE_COMMUNICATION`)으로 **통화 경로에 주입**. VoIP 앱이 통화 중 소리를 전달하는 경로와 동일하므로 상대방도 들음 |
| 폴백 | 통화 경로 재생 실패 시 `ToneGenerator(STREAM_VOICE_CALL)` 스피커 재생 |
| 종류 | `short`(1회 삐) / `double`(2회 삐) / `chime`(띵동) — 설정에서 선택 + 미리듣기 버튼 |
| 기본값 | ON (법적 안전을 위해 끄지 않기를 권장) |
| 타이밍 | 녹음 파이프라인 시작 후 400ms 지연 (통화 오디오 안정화 대기) |
| 부수 효과 | 고지음이 녹음 파일에 함께 담김 → "녹음이 실제로 시작되었는지" 확인할 수 있는 증거 |

### 14.2 자동 녹음 기본 ON
- `AUTO_RECORD_INCOMING/OUTGOING`, `RECORD_THIRD_PARTY_CALLS` 기본값을 `true`로 변경
- `AppPreferences.applyCallRecordDefaultsOnce()` — 최초 실행(및 업데이트 후 첫 실행)에 스위치를 강제 ON → **"녹음 기능을 켜야 녹음되는" UX 제거**
- 사용자가 의도적으로 끈 경우에는 그대로 유지(1회만 강제)

### 14.3 메신저 앱(OTT) 통화 녹음
| 계층 | 내용 |
|---|---|
| 감지 | `InCallService` + `INCLUDE_SELF_MANAGED_CALLS=true` (업스트림 기능) → **Android 12+ 필수**. Android 11은 `MANAGE_ONGOING_CALLS` AppOps가 없어 앱 통화 감지 불가 |
| 필터 | `CallAppRegistry`(카카오톡/카카오워크/텔레그램/LINE/WhatsApp/WhatsApp Business/Skype/Meet/Zoom/Teams/Signal/Viber/Discord/Messenger/WeChat/QQ/imo/Google Voice) — "모든 앱" 토글 또는 개별 체크 |
| 오디오 소스 | 통신사 통화는 `voice-call`(양방향), 앱 통화는 별도 소스: `mic-voice-communication`(기본) / `output`(REMOTE_SUBMIX, 스피커 출력) / `playback`(AudioPlaybackCapture) / `mic-voice-recognition` |
| 현실적 한계 | 앱 통화는 통화 신호 경로(telephony call audio)를 쓰지 않으므로 **상대 음성 잡음은 기기·앱·통화 방식에 의존**. 스피커폰 통화 + `output` 소스가 가장 선명 |
| 리포트 | `.txt`에 "양방향/단방향 추정" 대신 음성 구간 통계로 판정 — 앱 통화는 상대 음성이 약해 "음성 미검출"이 나올 수 있음(리포트에 경고 표기) |
