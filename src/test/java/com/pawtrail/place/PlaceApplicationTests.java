package com.pawtrail.place;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 애플리케이션 컨텍스트가 뜨는지만 확인하는 검사임
// 본문이 비어 있어도 @SpringBootTest 가 앱을 통째로 한 번 띄워보므로
// 빈 배선이 깨졌거나 자동 설정이 안 켜졌으면 여기서 드러남
//
// * 데이터베이스를 컨테이너로 직접 띄우는 이유
//   DataSource 주소는 설정 서버에서 내려오는데 spring.config.import 가 optional 이라
//   설정 서버가 없어도 조용히 넘어간 뒤 DataSource 를 만들다 실패함
//   설정 서버가 떠 있는지에 따라 결과가 갈리면 검사로서 의미가 없으므로
//   외부에 의존하지 않도록 테스트가 스스로 준비함
//
// * infra 레포의 db 프로파일과는 무관함
//   그쪽은 compose 가, 이쪽은 이 코드가 띄우며 서로를 쓰지 않음
//   db 프로파일을 켜 두어도 이 검사는 자기 컨테이너를 새로 만듦
//
// * 이미지를 postgis/postgis:17-3.5 로 둔 이유
//   place 는 geom geography(Point) 컬럼과 GIST 인덱스를 쓰는데
//   postgres:17-alpine 에는 PostGIS 확장이 없어
//   Flyway 가 type "geography" does not exist 로 실패함
//   compose 의 db 프로파일이 쓰는 이미지와 같은 것이라 개발 DB 와 검증 환경이 일치함
//   주의: amd64 전용이라 Apple Silicon 에서는 Rosetta 가 필요함
//        Docker Desktop 의 Settings > General 에서
//        Use Rosetta for x86_64/amd64 emulation 을 켤 것
//
// * asCompatibleSubstituteFor 가 반드시 필요함
//   PostgreSQLContainer 는 넘긴 이미지가 자기가 아는 postgres 인지 생성자에서 검사하고
//   이름이 다르면 IllegalStateException 을 던짐
//   postgis/postgis 는 postgres 위에 확장만 얹은 이미지라 호환되므로 그것을 명시함
//   DockerImageName 은 org.testcontainers.utility 에 그대로 있음
//   패키지가 옮겨진 것은 PostgreSQLContainer 쪽임
//
// * 컨테이너를 @Bean 이 아니라 정적 필드로 두는 이유
//   @Bean 메서드로 정의하면 인스턴스가 충분히 이른 시점에 준비되지 않아
//   @ServiceConnection 에 name 을 따로 지정해야 함(Spring Boot 4 공식 문서)
//   정적 필드는 그 문제가 없음
//
// * import 가 org.testcontainers.postgresql 인 것에 주의할 것
//   Testcontainers 2 부터 대부분의 컨테이너 클래스에서 제네릭이 사라졌고
//   제네릭이 붙은 옛 클래스는 org.testcontainers.containers 에 하위 호환용으로 남아 deprecated 임
//   인터넷 예제 대부분이 1.x 기준이라 옛 패키지를 쓰며, 그대로 가져오면
//   컴파일은 통과하되 deprecated 경고가 남음
//   PostgreSQLContainer 뒤에 <?> 가 붙어 있으면 옛 클래스를 쓰고 있다는 뜻임
@SpringBootTest
@Testcontainers
class PlaceApplicationTests {

    private static final DockerImageName POSTGIS_IMAGE =
        DockerImageName.parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    // @ServiceConnection 이 컨테이너의 주소와 계정을 DataSource 에 자동으로 넣어 줌
    // 따라서 테스트 설정 파일에 spring.datasource 를 적지 않음
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGIS_IMAGE);

    @Test
    void contextLoads() {
    }

}
