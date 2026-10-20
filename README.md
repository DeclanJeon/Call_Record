# Call_Record

무료·오픈소스 기반 안드로이드 자동 통화 녹음 앱.
**Cube ACR의 유료 기능(자동 통화 녹음 + 통화 내용 전사 + 클라우드 백업) 중 필요한 부분만** GPL 오픈소스 코드에서 재구성했습니다.

- 통화(수신/발신) 시 **자동으로 양방향 음성 녹음** (루트 불필요, Shizuku 사용)
- 통화가 끝나면 같은 폴더에 **검증 리포트 `.txt` / `.json`** 자동 생성
- **온디바이스 음성 인식(Vosk)** 으로 통화 내용을 텍스트로 전사 (`.srt` 포함)
- **Google Drive 폴더로 자동 백업** (SAF 폴더 선택 방식, OAuth·계정 API 불필요)
- 결제·계정·광고·데이터 전송 없음. 음성 데이터는 기기 안에서만 처리

>법적 주의: 본 앱은 "자신이 참여하는 통화를 개인적으로 기록"하는 용도를 전제로 합니다.
> 통신비밀보호법상 **상대방 동의가 없으면 1:1 통화라도 형사처벌 가능**하므로, 통화 녹음 사실을 양쪽에 알리십시오(녹음 사실을 양쪽이 알 수 있도록你自己가 사전에 알려주십시오).

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

## 3. 설치 & 사용 (휴대폰)

1. **Shizuku 설치** (루트 불필요)
   - 설치: [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) (F-Droid / GitHub 릴리스)
   - 개발자 옵션 → "무선 디버깅" 켜고, Shizuku 앱에서 "pairing via Wi-Fi" 로 기기와 페어링
   - ※ Shizuku는 재부팅 후 종료되므로, 필요할 때 "부팅 시 자동 시작" 옵션을 켜거나 개발자옵션에서 Shizuku를 부팅 앱으로 지정
2. **Call_Record 설치**: `app-release.apk` 를 기기에 설치(설정 → 알 수 없는 앱 허용)
3. 앱을 열고:
   - 저장 폴더 지정(기본: `Documents/CallRecord`)
   - 권한 부여(알림, 전화 상태 등)
   - 필요하면 `설정 → Call Record` 에서
     - "검증 리포트 자동 생성" ON
     - "통화 내용 자동 전사" ON + **음성 인식 모델 받기(약 40MB, 최초 1회)**
     - "Google Drive 백업" ON + 백업 폴더로 **Google Drive** 선택
4. 상대방에게 녹음 사실을 알리고 통화를 합니다.
5. 통화가 끝나면 같은 폴더에 `녹음파일.ogg`, `녹음파일.txt`(검증 리포트), `녹음파일.json`, `녹음파일.srt`(전사)가 생깁니다.

---

## 4. 생성되는 파일 예시

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
네, 오후 3시에 可以합니다.
```

---

## 5. 호환성 (중요)

| 항목 | 상태 |
|---|---|
| **Android 11 (API 30) 이상** | ✅ 지원 (양방향 녹음, scrcpy 직접 캡처) |
| **Android 10 (API 29) 이하** | ❌ 미지원 — scrcpy 기반 오디오 캡처가 API 30 미만에서 비활성화됨. 2020년형 기종이라도大部分는 지금 Android 12+ 로 업데이트되어 있음 |
| **루트 기기** | 불필요 (Shizuku 사용). 루트 보유 시 `chenxiaolong/BCR` 경로가 대안 |
| **일부 제조사** | `voice-call` 소스를 지원하지 않는 기기가 있어 마이크 폴백(`mic-voice-communication`)으로 자동 전환될 수 있음. 이 경우 상대 음성 품질이 낮아질 수 있음(리포트의 "음성 미검출" 경고로 확인) |

---

## 6. 폴더 구조

```
app/src/main/java/com/declan/callrecord/
├── services/…            (기존) 통화 감지 · Shizuku 연동 · 녹음 엔진
├── verify/                ★ 검증 파이프라인 (PcmDecoder · AudioAnalyzer · ReportWriter)
├── transcription/         ★ 온디바이스 음성 인식 (Vosk + 모델 관리)
├── backup/                ★ Google Drive 백업 (SAF 미러링)
└── ui/screens/            ★ 녹음 목록 화면 · Call Record 설정 섹션
```

---

## 7. 아직 남은 작업

- [ ] 실기기 검증 (통화 → 녹음 → 리포트 → 전사 → Drive 백업)
- [ ] 통화 녹음 고지음(비프) 기능
- [ ] Android 10 지원(쉘 권한 기반 자체 캡처 구현)
- [ ] 화질/정확도 개선 옵션: Whisper.cpp 엔진(현재는 Vosk 소형 모델)
