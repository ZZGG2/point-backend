# point-backend

금융 포인트 플랫폼 백엔드 — **모듈러 모놀리스 + 이벤트 MSA** (Java 21 / Spring Boot 3)

| 모듈 | 역할 | 포트 | DR 배포 |
|---|---|---|---|
| `monolith` | user / asset / point / cs | 8080 | ✅ |
| `event-service` | 출석·퀴즈·대기열 (Redis + Kafka) | 8081 | ❌ (DR 시 점검) |

- 설계 문서·ADR: [point-infra/docs](https://github.com/CHANGE_ME_ORG/point-infra/tree/main/docs)
- API 계약: [`api/openapi.yaml`](api/openapi.yaml) · Kafka 스키마: [`api/events/`](api/events/)
- 작업 규칙: **[AGENTS.md](AGENTS.md)** (사람·AI 공통), [CONTRIBUTING.md](CONTRIBUTING.md)

## 로컬 실행
상세 가이드 (Windows, 데모 시나리오 포함): 상위 폴더의 `LOCAL-DEV.md`
```bash
docker compose up -d                      # PostgreSQL, Redis, Kafka
./gradlew :monolith:bootRun               # :8080  (Windows: .\gradlew.bat)
./gradlew :event-service:bootRun          # :8081
```

## 현재 구현 범위
| 기능 | 상태 |
|---|---|
| 포인트 잔액/내역/사용 API (멱등키, 행 잠금, 음수 잔액 차단) | ✅ |
| 퀴즈 선착순 (Redis Lua: 마감·중복·재고 원자 처리) | ✅ |
| 당첨 → Kafka → 원장 멱등 반영 | ✅ |
| 로컬 데모 데이터 / 데모 이벤트 자동 생성 | ✅ (`local` 프로필) |
| 운영·인프라 검증 API (`/api/system/{info,whoami,ledger}`, `/api/events/system/{info,whoami}`) — Pod·DB·Redis·Kafka 상태, 원장 집계 | ✅ (`OPS_API_ENABLED=false` 로 끔) |
| 인증 | ⏳ 임시 `X-User-Id` 헤더 (JWT 예정) |
| 회원가입·로그인, 자산조회, 고객센터, 출석체크, 대기열 | ⏳ |
| DLQ, 대사 배치, Testcontainers 통합 테스트 | ⏳ |

## 배포 흐름
`main` 머지 → 이미지 빌드·스캔 → ECR + Artifact Registry 이중 푸시 → **point-infra에 태그 bump PR 자동 생성** → 머지 시 ArgoCD 배포
