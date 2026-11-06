package com.pointplatform.monolith;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 모듈 경계 강제: 다른 모듈의 api / domain / infra 패키지 직접 참조 금지.
 * 허용되는 것은 상대 모듈의 application 패키지(public 유스케이스)뿐이다.
 * → 이 규칙이 지켜져야 나중에 모듈 하나를 MSA 로 떼어낼 때 호출부만 HTTP/Kafka 로 바꾸면 된다.
 */
@AnalyzeClasses(packages = "com.pointplatform.monolith")
class ModuleBoundaryTest {

    private static final String[] MODULES = {"user", "asset", "point", "cs"};

    @ArchTest
    static final ArchRule user_only_uses_other_modules_application = onlyApplicationOfOthers("user");

    @ArchTest
    static final ArchRule asset_only_uses_other_modules_application = onlyApplicationOfOthers("asset");

    @ArchTest
    static final ArchRule point_only_uses_other_modules_application = onlyApplicationOfOthers("point");

    @ArchTest
    static final ArchRule cs_only_uses_other_modules_application = onlyApplicationOfOthers("cs");

    private static ArchRule onlyApplicationOfOthers(String module) {
        String[] forbidden = Arrays.stream(MODULES)
                .filter(other -> !other.equals(module))
                .flatMap(other -> Arrays.stream(new String[]{
                        "..monolith." + other + ".api..",
                        "..monolith." + other + ".domain..",
                        "..monolith." + other + ".infra.."}))
                .toArray(String[]::new);
        return noClasses()
                .that().resideInAPackage("..monolith." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                .allowEmptyShould(true) // 아직 비어 있는 모듈 허용 (ArchUnit 기본값은 실패)
                .because(module + " 모듈은 다른 모듈의 application 패키지만 참조할 수 있다 (hybrid-msa.md §3)");
    }
}
