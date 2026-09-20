# 팀 규칙

## 패키지 구조

기준 패키지는 `likelion.yacha_backend` 이고, **도메인(기능) 단위로 나눈 뒤 그 안을 계층으로 나눈다.**
도메인은 `docs/야차철학_ERD_API명세_Final_v2.md` 의 API 묶음(2-1 ~ 2-8)과 1:1 로 대응한다.

```
likelion.yacha_backend
├── global/                        # 도메인 공통
│   ├── config/                    # 각종 설정 (Security, Redis, 비동기 등)
│   ├── security/
│   │   ├── jwt/                   # JWT 인증
│   │   └── session/               # 세션(쿠키) 인증
│   ├── response/                  # 응답 래퍼 { success, data, error }
│   ├── exception/                 # 에러 코드 enum, 전역 예외 핸들러
│   └── entity/                    # BaseEntity (created_at, updated_at 등)
├── domain/
│   ├── auth/                      # 회원가입 · 로그인 · 게스트 · 토큰
│   ├── user/                      # users
│   ├── topic/                     # daily_topic · 카테고리
│   ├── session/                   # debate_session · debate_participant
│   ├── argument/                  # argument
│   ├── evidence/                  # evidence (LINER 근거)
│   ├── result/                    # debate_result (판정 · 철학자 판독)
│   ├── publicpage/                # 공개 페이지 (조회 전용)
│   └── admin/                     # 관리자
└── infra/
    ├── liner/                     # LINER 검색 연동
    └── llm/                       # AI 반박 · 판정 · compose
```

### 도메인 내부 구성

각 도메인은 아래 5개 패키지로 구성한다.

```
domain/{도메인}/
├── controller/
├── service/
├── repository/
├── entity/
└── dto/
```

- 자체 테이블이 없는 `auth`, `publicpage`, `admin` 은 `entity`, `repository` 를 두지 않는다.
- 필요 없는 패키지를 미리 만들지 않는다. 필요해지면 그때 추가한다.

### 계층 규칙

- 호출 방향은 `controller → service → repository` 이다. controller 가 repository 를 직접 호출하지 않는다.
- `entity` 를 API 응답으로 직접 반환하지 않는다. 요청 · 응답은 `dto` 를 사용한다.
- enum 은 해당 도메인의 `entity` 패키지에 둔다.
- 다른 도메인의 데이터가 필요하면 그 도메인의 `service` 를 통해 가져온다.

### global / infra 규칙

- `global` 은 도메인 공통 코드만 둔다. `global` 이 `domain` 을 참조하지 않는다.
- `infra` 는 외부 API 클라이언트(요청 · 응답 변환)만 둔다. 비즈니스 로직은 `domain` 의 `service` 에서 처리한다.
- 외부 API 키는 설정 파일과 환경 변수로만 다룬다. 코드에 넣거나 프론트에 노출하지 않는다.

### 빈 폴더 (`.gitkeep`)

git 은 빈 폴더를 추적하지 않아서, 구조를 공유하려고 빈 폴더에 `.gitkeep` 을 넣어 두었다.
**폴더에 실제 파일을 추가할 때 해당 `.gitkeep` 은 삭제한다.**
