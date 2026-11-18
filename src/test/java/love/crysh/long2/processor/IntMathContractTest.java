package love.crysh.long2.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class IntMathContractTest extends ProcessorTestSupport {
    private static final String[] DECLARATIONS = {
            "public static Target abs(Target value) { return value; }",
            "public static Target max(Target a, Target b) { return a; }",
            "public static Target min(Target a, Target b) { return a; }",
            "public static Target ofFloor(love.crysh.long2.Binary<?> value) { return null; }",
            "public static Target ofFloor(double value) { return null; }"
    };

    @Test
    void acceptsConcreteSignaturesAndPreservesTheBinarySourceType() throws IOException {
        assertCompiles(fixture(String.join("\n", DECLARATIONS)) + """
                class Caller {
                    Target absolute(Target value) { return Target.abs(value); }
                    Target larger(Target a, Target b) { return Target.max(a, b); }
                    Target smaller(Target a, Target b) { return Target.min(a, b); }
                    Target binary(love.crysh.long2.Binary<?> value) { return Target.ofFloor(value); }
                    Target primitive(double value) { return Target.ofFloor(value); }
                }
                """);
    }

    static Stream<Arguments> invalidDeclarations() {
        return IntStream.range(0, DECLARATIONS.length).boxed()
                .flatMap(index -> Stream.of("missing", "inherited", "instance", "wrongReturn")
                        .map(variant -> Arguments.of(index, variant)));
    }

    @ParameterizedTest
    @MethodSource("invalidDeclarations")
    void requiresEveryDirectStaticMethodWithTheConcreteReturnType(int index, String variant) throws IOException {
        String[] declarations = DECLARATIONS.clone();
        String declaration = declarations[index];
        declarations[index] = switch (variant) {
            case "instance" -> declaration.replace("public static", "public");
            case "wrongReturn" -> declaration.replace("static Target ", "static love.crysh.long2.IntMath ");
            default -> "";
        };
        String source = fixture(String.join("\n", declarations));
        if (variant.equals("inherited")) {
            source = "class Base { " + declaration + " }\n"
                    + source.replace("class Target implements", "class Target extends Base implements");
        }
        var result = compile(source);
        assertFalse(result.success(), result.messages());
        String name = declaration.substring(declaration.indexOf("Target ") + "Target ".length(),
                declaration.indexOf('('));
        assertTrue(result.messages().contains(name + "("), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"float", "java.lang.Double"})
    void rejectsOtherPrimitiveFactoryParameterTypes(String sourceType) throws IOException {
        String declarations = String.join("\n", DECLARATIONS).replace("ofFloor(double value)",
                "ofFloor(" + sourceType + " value)");
        var result = compile(fixture(declarations));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("ofFloor(double"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"love.crysh.long2.binaries.binary256.Binary256", "love.crysh.long2.Binary"})
    void requiresTheWildcardBinarySourceParameter(String sourceType) throws IOException {
        String declarations = Arrays.stream(DECLARATIONS)
                .map(declaration -> declaration.replace("love.crysh.long2.Binary<?> value", sourceType + " value"))
                .collect(java.util.stream.Collectors.joining("\n"));
        var result = compile(fixture(declarations));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("ofFloor(love.crysh.long2.Binary<?>"), result.messages());
    }

    private static String fixture(String declarations) {
        return "class Target implements love.crysh.long2.IntMath {\n" + declarations + "\n}\n";
    }
}
