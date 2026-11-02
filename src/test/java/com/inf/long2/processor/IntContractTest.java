package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.inf.long2.Int;

class IntContractTest extends ProcessorTestSupport {
    private static final String DECLARATIONS = """
            public static final com.inf.long2.Int.Format FORMAT = null;
            public static final Target ZERO = null;
            public static final Target ONE = null;
            public static final Target MAX_VALUE = null;
            public static final Target MIN_VALUE = null;
            public static Target of(long value) { return null; }
            public static Target of(double value) { return null; }
            public static Target of(long[] value) { return null; }
            public static Target of(byte[] value) { return null; }
            public static Target of(com.inf.long2.Int<?> value) { return null; }
            public static Target of(com.inf.long2.Binary<?> value) { return null; }
            public static Target parseInt(java.lang.String value) { return null; }
            public static Target parseHexInt(java.lang.String value) { return null; }
            public static boolean isValid(java.lang.String value) { return false; }
            public static boolean isHexValid(java.lang.String value) { return false; }
            """;

    @Test
    void acceptsTheNewStaticAndInstanceContracts() throws IOException {
        assertCompiles(fixture(DECLARATIONS, "") + """
                class Caller {
                    static Target restore(byte[] bytes) { return Target.of(bytes); }
                    static byte[] encode(com.inf.long2.Int<?> value) { return value.toBytes(); }
                    static boolean decimal(String value) { return Target.isValid(value); }
                    static boolean hex(String value) { return Target.isHexValid(value); }
                    static com.inf.long2.Int.Format format(com.inf.long2.Int<?> value) { return value.format(); }
                    static Target integer(com.inf.long2.Int<?> value) { return Target.of(value); }
                    static Target binary(com.inf.long2.Binary<?> value) { return Target.of(value); }
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes", "isValid", "isHexValid"})
    void rejectsMissingDirectStaticDeclarations(String member) throws IOException {
        String declaration = member.equals("bytes") ? "public static Target of(byte[] value) { return null; }"
                : "public static boolean " + member + "(java.lang.String value) { return false; }";
        Compilation result = compile(fixture(DECLARATIONS.replace(declaration, ""), ""));
        assertFalse(result.success(), result.messages());
        String signature = member.equals("bytes") ? "public static fixture.Target of(byte[] "
                : "public static boolean " + member + "(java.lang.String ";
        assertTrue(result.messages().contains("must declare: " + signature), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"isValid", "isHexValid"})
    void rejectsBoxedBooleanInsteadOfPrimitive(String member) throws IOException {
        Compilation result = compile(fixture(DECLARATIONS.replace("boolean " + member + "(",
                "java.lang.Boolean " + member + "("), ""));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("public static boolean " + member + "("), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"isValid", "isHexValid"})
    void rejectsInheritedStaticValidators(String member) throws IOException {
        String declaration = "public static boolean " + member + "(java.lang.String value) { return false; }";
        String source = "class Base { " + declaration + " }\n" + fixture(DECLARATIONS.replace(declaration, ""), "")
                .replace("class Target implements", "class Target extends Base implements");
        Compilation result = compile(source);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("public static boolean " + member + "("), result.messages());
    }

    @Test
    void rejectsVarargsInsteadOfTheByteArrayFactory() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS.replace("of(byte[] value)", "of(byte... value)"), ""));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("public static fixture.Target of(byte[] "), result.messages());
    }

    @Test
    void requiresTheByteConversionInstanceMethod() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS, "toBytes"));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("toBytes()"), result.messages());
    }

    @Test
    void requiresTheFormatInstanceMethod() throws IOException {
        Compilation result = compile(fixture(DECLARATIONS, "format"));
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("format()"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrongType", "inherited"})
    void requiresTheDirectFormatConstant(String variant) throws IOException {
        String declaration = "public static final com.inf.long2.Int.Format FORMAT = null;";
        String replacement = variant.equals("wrongType") ? "public static final Object FORMAT = null;" : "";
        String source = fixture(DECLARATIONS.replace(declaration, replacement), "");
        if (variant.equals("inherited")) {
            source = "class Base { " + declaration + " }\n"
                    + source.replace("class Target implements", "class Target extends Base implements");
        }
        Compilation result = compile(source);
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("public static com.inf.long2.Int.Format FORMAT"), result.messages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"com.inf.long2.Int<?>", "com.inf.long2.Binary<?>"})
    void requiresDirectInterfaceSourceFactories(String sourceType) throws IOException {
        String declaration = "public static Target of(" + sourceType + " value) { return null; }";
        Compilation missing = compile(fixture(DECLARATIONS.replace(declaration, ""), ""));
        assertMissingFactory(missing, sourceType);
        Compilation inherited = compile("class Base { " + declaration + " }\n"
                + fixture(DECLARATIONS.replace(declaration, ""), "")
                        .replace("class Target implements", "class Target extends Base implements"));
        assertMissingFactory(inherited, sourceType);
        Compilation wrongReturn = compile(fixture(DECLARATIONS.replace(declaration,
                declaration.replace("Target of(", "com.inf.long2.Int<?> of(")), ""));
        assertMissingFactory(wrongReturn, sourceType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Target", "Object", "com.inf.long2.Int", "com.inf.long2.Int<Target>",
            "com.inf.long2.ints.Int256"})
    void preservesTheCompleteIntegerSourceInterface(String sourceType) throws IOException {
        Compilation result = compile(fixture(DECLARATIONS.replace("of(com.inf.long2.Int<?> value)",
                "of(" + sourceType + " value)"), ""));
        assertMissingFactory(result, "com.inf.long2.Int<?>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Object", "com.inf.long2.Binary", "com.inf.long2.Binary<com.inf.long2.binaries.Binary256>",
            "com.inf.long2.binaries.Binary256"})
    void requiresTheCompleteBinarySourceInterface(String sourceType) throws IOException {
        Compilation result = compile(fixture(DECLARATIONS.replace("of(com.inf.long2.Binary<?> value)",
                "of(" + sourceType + " value)"), ""));
        assertMissingFactory(result, "com.inf.long2.Binary<?>");
    }

    private static void assertMissingFactory(Compilation result, String sourceType) {
        assertFalse(result.success(), result.messages());
        assertTrue(result.messages().contains("must declare: public static fixture.Target of(" + sourceType + " "),
                result.messages());
    }

    private static String fixture(String declarations, String omittedMethod) {
        StringBuilder source = new StringBuilder("class Target implements com.inf.long2.Int<Target> {\n");
        source.append(declarations);
        for (Method method : Int.class.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || method.isSynthetic()
                    || method.getName().equals(omittedMethod)) {
                continue;
            }
            source.append("public ").append(typeName(method.getGenericReturnType())).append(' ')
                    .append(method.getName()).append('(');
            Type[] parameters = method.getGenericParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (i != 0) {
                    source.append(", ");
                }
                source.append(typeName(parameters[i])).append(" value").append(i);
            }
            source.append(") { throw new UnsupportedOperationException(); }\n");
        }
        return source.append("}\n").toString();
    }

    private static String typeName(Type type) {
        if (type instanceof TypeVariable<?>) {
            return "Target";
        }
        if (type instanceof Class<?> raw) {
            return raw.getCanonicalName();
        }
        throw new IllegalArgumentException("Unsupported fixture type: " + type);
    }
}
