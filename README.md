# Call_Record

무료·오픈소스 기반 안드로이드 자동 통화 녹음 앱.
**Cube ACR의 유료 기능(자동 통화 녹음 + 통화 내용 전사 + 클라우드 백업) 중 필요한 부분만** GPL 오픈소스 코드에서 재구성했습니다.

- 통화(수신/발신) 시 **자동으로 양방향 음성 녹음** — 별도 스위치를 켤 필요 없음(기본 ON)
- **카카오톡·텔레그램·LINE·WhatsApp 등 메신저 앱 통화도 녹음** (Android 12+)
- **녹음 고지음**: 녹음 시작 순간 상대방도 들을 수 있는 신호음 재생 (기본 ON)
- 통화가 끝나면 같은 폴더에 **검증 리포트 `.txt` / `.json`** 자동 생성
- **온디바이스 음성 인식(Vosk)** 으로 통화 내용을 텍스트로 전사 (`.srt` 포함)
- **Google Drive 폴더로 자동 백업** (SAF 폴더 선택 방식, OAuth·계정 API 불필요)
- 결제·계정·광고·데이터 전송 없음. 음성 데이터는 기기 안에서만 처리

>법적 주의: 본 앱은 "자신이 참여하는 통화를 개인적으로 기록"하는 용도를 전제로 합니다.
> 통신비밀보호법상 **상대방 동의가 없으면 1:1 통화라도 형사처벌 가능**하므로, 통화 녹음 사실을 양쪽에 알리십시오(직접 사전에 알려주고, 고지음도 켜 두는 것을 권장합니다).

---

## 1. 무엇을 어디에서 가져왔나

| 구성요소 | 출처 | 라이선스 |
|---|---|---|
| 통화 감지·녹음 파이프라인·Shizuku 연동·UI 뼈대 | [kitsumed/ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) | GPL-3.0 (원저작자 kitsumed, GPL §7 추가 조항) |
| 양방향 통화 음성 캡처 | [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) `scrcpy-server` (앱 빌드 시 SHA256 검증 후 포함) | Apache-2.0 |
| Shizuku 권한 상승 | [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) | AGPL-3.0 |
| 온디바이스 음성 인식 | [alphacephei/vosk-android](https://github.com/alphacephei/vosk) + `vosk-model-small-ko-0.22` | Apache-2.0 |
| **Call_Record 고유 기능** (검증 리포트·VAD 분석·전사·Drive 백업·녹음 목록 UI) | 이 저장소 | GPL-3.0 |

전체 라이선스는 GPL-3.0입니다. 원저작자 저작권 표시는 모두 파일에 유지되어 있습니다.

---

## 2. 빌드 방법 (이 PC에 이미 구성됨)

```bash
source ~/tools/env.sh          # JAVA_HOME(Temurin 17) + ANDROID_HOME(Sdk 36 + NDK + CMake)
cd ~/Documents/Projects/Products/01_Persnal_Work/call_record
./gradlew :app:assembleRelease  # 결과: app/build/outputs/apk/release/app-release.apk
```

- 서명 키: `keystore/call-record.jks` (비밀번호 `callrecord`, **분실 시 업데이트 불가** → 백업 권장)
- 로컬 빌드 도구 설치 위치(루트 불필요):
  - JDK: `~/tools/jdk-17.0.20.1+1`
  - Android SDK: `~/Android/Sdk` (platform-tools, platforms;android-36, build-tools 36, ndk 27.2, cmake 3.22.1)
  - Gradle: `~/tools/gradle-9.4.1` (`./gradlew` 래퍼가 자동 사용)

---

## 3. 문서

| 문서 | 내용 |
|---|---|
| [설치 가이드](docs/설치가이드.md) | Shizuku 설치 → APK 설치 → 초기 설정 → 첫 통화 테스트까지 |
| [사용 가이드](docs/사용가이드.md) | 기능 상세, 생성 파일 해설, 통화 종류별 동작, FAQ |
| [개발 가이드](docs/개발가이드.md) | 소스 구조, 빌드/서명, 동작 흐름, 확장 아이디어 |
| [법적 안내](docs/legal/법적안내.md) | 통신비밀보호법 요약과 고지음의 법적 의미 |
| [설계 문서](DESIGN.md) | 요구사항 분석, 후보 비교, 기술 제약과 그 근거 |

---

## 4. 설치 & 사용 (휴대폰)

1. **Shizuku 설치** (루트 불필요)
   - 설치: [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) (F-Droid / GitHub 릴리스)
   - 개발자 옵션 → "무선 디버깅" 켜고, Shizuku 앱에서 "pairing via Wi-Fi" 로 기기와 페어링
   - ※ Shizuku는 재부팅 후 종료되므로, 필요할 때 "부팅 시 자동 시작" 옵션을 켜거나 개발자옵션에서 Shizuku를 부팅 앱으로 지정
2. **Call_Record 설치**: `app-release.apk` 를 기기에 설치(설정 → 알 수 없는 앱 허용)
3. 앱을 열고:
   - 저장 폴더 지정(기본: `Documents/CallRecord`)
   - 권한 부여(알림, 전화 상태 등)
   - **그냥 쓰세요.** 통화가 시작되면 자동으로 녹음되고, 고지음이 울리고, 끝나면 리포트·전사·백업까지 자동으로 됩니다.
   - 필요하면 `설정 → Call Record` 에서 세부 조정:
     - "녹음 고지음" 종류(짧은 삐 / 두 번 삐 / 차임) + 미리듣기
     - "모든 앱 통화 녹음" 또는 앱별 선택(카카오톡, 텔레그램, LINE, WhatsApp ...)
     - 앱 통화 녹음 소스(마이크 / 스피커 출력 / 앱 재생음)
     - "통화 내용 자동 전사" + **음성 인식 모델 받기(약 40MB, 최초 1회)**
     - "Google Drive 백업" ON + 백업 폴더로 **Google Drive** 선택
4. 상대방에게 녹음 사실을 알리고 통화를 합니다.
5. 통화가 끝나면 같은 폴더에 `녹음파일.ogg`, `녹음파일.txt`(검증 리포트), `녹음파일.json`, `녹음파일.srt`(전사)가 생깁니다.

---

## 5. 생성되는 파일 예시

```
Documents/CallRecord/
├── 20261020_231405_in_01012345678.ogg   ← 원본 녹음 (Opus 24kbps, 양방향)
├── 20261020_231405_in_01012345678.txt   ← ★ 검증 리포트 (사람이 읽는 요약)
├── 20261020_231405_in_01012345678.json  ← ★ 기계용 메타데이터
└── 20261020_231405_in_01012345678.srt   ← ★ 전사 결과 (자막)
```

`*.txt` 내용 예시:

```
Call_Record — 녹음 검증 리포트
================================================
녹음 파일    : 20261020_231405_in_01012345678.ogg
생성 시각    : 2026-10-20 23:14:05
통화 방향    : 수신 (incoming)
전화번호     : 01012345678
녹음 길이    : 03:02 (182000 ms)
포맷        : OGG/Opus
샘플레이트   : 48000 Hz
채널        : 1
...
[음성 분석]
전체 RMS     : -26.4 dBFS
최대 피크    : -3.1 dBFS
음성 구간    : 23 개 / 총 12.8 %
최장 무음    : 00:41
판정        : 통화 음성으로 보임 — 녹음에 대화 내용이 포함되어 있습니다

[통화 내용 전사]
안녕하세요, 내일 회의 몇 시예요?
네, 오후 3시에 가능합니다.
```

---

## 6. 호환성 (중요)

| 항목 | 상태 |
|---|---|
| **Android 11 (API 30) 이상** | ✅ 지원 (양방향 녹음, scrcpy 직접 캡처) |
| **Android 10 (API 29) 이하** | ❌ 미지원 — scrcpy 기반 오디오 캡처가 API 30 미만에서 비활성화됨. 2020년형 기종이라도 대부분은 지금 Android 12+ 로 업데이트되어 있음 |
| **루트 기기** | 불필요 (Shizuku 사용). 루트 보유 시 `chenxiaolong/BCR` 경로가 대안 |
| **일부 제조사** | `voice-call` 소스를 지원하지 않는 기기가 있어 마이크 폴백(`mic-voice-communication`)으로 자동 전환될 수 있음. 이 경우 상대 음성 품질이 낮아질 수 있음(리포트의 "음성 미검출" 경고로 확인) |

---

## 7. 폴더 구조

```
app/src/main/java/com/declan/callrecord/
├── services/…            (기존) 통화 감지 · Shizuku 연동 · 녹음 엔진
├── verify/                ★ 검증 파이프라인 (PcmDecoder · AudioAnalyzer · ReportWriter)
├── transcription/         ★ 온디바이스 음성 인식 (Vosk + 모델 관리)
├── backup/                ★ Google Drive 백업 (SAF 미러링)
└── ui/screens/            ★ 녹음 목록 화면 · Call Record 설정 섹션
```

---

## 8. 녹음 고지음 (v0.2.0)

- 녹음이 시작되면 **통화 경로(AudioTrack `MODE_IN_COMMUNICATION`)** 에 짧은 신호음을 넣습니다.
  - VoIP 앱이 통화 중 소리를 전달하는 것과 같은 경로이므로 **상대방도 들을 수 있습니다.**
  - 실패하면 스피커 재생으로 자동 폴백합니다.
- 종류: `짧은 삐` / `두 번 삐` / `차임` (설정에서 선택, 미리듣기 버튼 제공)
- 고지음은 녹음 파일에도 함께 담겨 "녹음이 실제로 시작되었는지" 확인할 수 있는 흔적이 됩니다.
- 법적 근거: 통신비밀보호법상 상대방 동의가 필요한 상황을 줄이기 위한 장치입니다. **고지음을 끄지 않는 것을 권장합니다.**

## 9. 메신저 앱(OTT) 통화 녹음 (v0.2.0)

| 항목 | 내용 |
|---|---|
| 감지 방식 | `InCallService` + `INCLUDE_SELF_MANAGED_CALLS` → **Android 12 이상 필요** (Android 11은 앱 통화를 감지할 수 없음) |
| 기본 소스 | `mic-voice-communication` (마이크 기반) — 상대 음성이 약하게 잡힐 수 있음 |
| 선택 가능 소스 | `output`(REMOTE_SUBMIX, 스피커 출력) / `playback`(앱 재생음 캡처) / `mic-voice-recognition` |
| 앱 선택 | "모든 앱 통화 녹음" ON(기본) 또는 설치된 앱을 개별 체크 (카카오톡, 카카오워크, 텔레그램, LINE, WhatsApp, WhatsApp Business, Skype, Google Meet, Zoom, MS Teams, Signal, Viber, Discord, Facebook Messenger, WeChat, QQ, imo, Google Voice) |
| 현실적 한계 | 앱 통화는 통신사 통화와 같은 오디오 경로를 쓰지 않으므로 **상대 음성 품질은 기기·앱·통화 방식(스피커폰 여부)에 따라 달라집니다.** 스피커폰 통화라면 "스피커 출력" 소스를 선택하면 훨씬 선명합니다. |

## 10. 아직 남은 작업

- [ ] 실기기 검증 (통신사 통화 → 녹음 → 고지음 → 리포트 → 전사 → Drive 백업)
- [ ] 실기기 검증 (카카오톡/WhatsApp 통화 → 앱 통화 녹음 소스별 음질 비교)
- [ ] Android 10 지원(쉘 권한 기반 자체 캡처 구현) — 앱 통화는 Android 12+ 부터
- [ ] 화질/정확도 개선 옵션: Whisper.cpp 엔진(현재는 Vosk 소형 모델)
- [ ] 앱 잠금(생체/PIN) — 통화 내용 보호
