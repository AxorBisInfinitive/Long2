package com.inf.long2.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
public final class StaticMethodImplementsCheckProcessor
        extends AbstractProcessor {

    private static final String ANNOTATION_NAME = "com.inf.long2.util.annotations.StaticMethodImplementsCheck";

    private final Set<String> checkedImplementations = new HashSet<>();
    private final Set<String> reportedInvalidContracts = new HashSet<>();

    private Types types;
    private Elements elements;

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        types = environment.getTypeUtils();
        elements = environment.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment round) {

        if (round.processingOver()) {
            return false;
        }

        for (Element root : round.getRootElements()) {
            visit(root);
        }

        return false;
    }

    private void visit(Element element) {
        if (!(element instanceof TypeElement type)) {
            return;
        }

        if (hasContractAnnotation(type)
                && type.getKind() != ElementKind.INTERFACE) {
            reportInvalidContractType(type);
        }

        if (isConcreteType(type)) {
            String name = type.getQualifiedName().toString();

            if (checkedImplementations.add(name)) {
                validate(type);
            }
        }

        for (Element child : type.getEnclosedElements()) {
            if (child instanceof TypeElement) {
                visit(child);
            }
        }
    }

    private boolean isConcreteType(TypeElement type) {
        return switch (type.getKind()) {
            case CLASS -> !type.getModifiers().contains(Modifier.ABSTRACT);
            case ENUM, RECORD -> true;
            default -> false;
        };
    }

    private void validate(TypeElement implementation) {
        Set<String> visitedTypes = new HashSet<>();
        collectContracts(
                implementation.asType(),
                implementation,
                visitedTypes);
    }

    private void collectContracts(
            TypeMirror current,
            TypeElement implementation,
            Set<String> visitedTypes) {

        for (TypeMirror parentType : types.directSupertypes(current)) {
            if (!(parentType instanceof DeclaredType declared)) {
                continue;
            }

            if (!(declared.asElement() instanceof TypeElement parent)) {
                continue;
            }

            // Include type arguments in the key so distinct declared
            // supertype uses are not accidentally conflated.
            if (!visitedTypes.add(parentType.toString())) {
                continue;
            }

            if (hasContractAnnotation(parent)) {
                if (parent.getKind() == ElementKind.INTERFACE) {
                    checkContract(parent, implementation);
                } else {
                    // Also catches an invalid annotated supertype loaded
                    // from a dependency rather than from this compilation.
                    reportInvalidContractType(parent);
                }
            }

            collectContracts(parentType, implementation, visitedTypes);
        }
    }

    private boolean hasContractAnnotation(TypeElement type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {
            Element annotationElement = annotation.getAnnotationType().asElement();

            if (annotationElement instanceof TypeElement annotationType) {
                Name qualifiedName = annotationType.getQualifiedName();

                if (qualifiedName.contentEquals(ANNOTATION_NAME)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void reportInvalidContractType(TypeElement type) {
        String name = type.getQualifiedName().toString();

        if (!reportedInvalidContracts.add(name)) {
            return;
        }

        String kind = switch (type.getKind()) {
            case CLASS -> "class";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION_TYPE -> "annotation type";
            default -> type.getKind().name().toLowerCase().replace('_', ' ');
        };

        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "@" + ANNOTATION_NAME
                        + " may only annotate interfaces; found "
                        + kind + " " + name,
                type);
    }

    private void checkContract(
            TypeElement contract,
            TypeElement implementation) {

        for (Element element : contract.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement required = (ExecutableElement) element;
            Set<Modifier> modifiers = required.getModifiers();

            // Preserve the contract rule: only directly declared
            // public static methods define requirements.
            if (!modifiers.contains(Modifier.PUBLIC)
                    || !modifiers.contains(Modifier.STATIC)) {
                continue;
            }

            if (!hasImplementation(required, contract, implementation)) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Class " + implementation.getQualifiedName()
                                + " must declare: "
                                + signature(required, contract, implementation)
                                + " (contract: "
                                + contract.getQualifiedName() + ")",
                        implementation);
            }
        }
    }

    private boolean hasImplementation(
            ExecutableElement required,
            TypeElement contract,
            TypeElement implementation) {

        for (Element element : implementation.getEnclosedElements()) {
            if (element.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement actual = (ExecutableElement) element;

            if (matches(required, actual, contract, implementation)) {
                return true;
            }
        }

        return false;
    }

    private boolean matches(
            ExecutableElement required,
            ExecutableElement actual,
            TypeElement contract,
            TypeElement implementation) {

        if (!required.getSimpleName().contentEquals(actual.getSimpleName())) {
            return false;
        }

        Set<Modifier> modifiers = actual.getModifiers();

        if (!modifiers.contains(Modifier.PUBLIC)
                || !modifiers.contains(Modifier.STATIC)) {
            return false;
        }

        // An array parameter and a varargs parameter have the same
        // underlying type but different source-level declarations.
        if (required.isVarArgs() != actual.isVarArgs()) {
            return false;
        }

        if (required.getParameters().size() != actual.getParameters().size()) {
            return false;
        }

        List<? extends TypeParameterElement> requiredGenerics = required.getTypeParameters();
        List<? extends TypeParameterElement> actualGenerics = actual.getTypeParameters();

        if (requiredGenerics.size() != actualGenerics.size()) {
            return false;
        }

        for (int i = 0; i < requiredGenerics.size(); i++) {
            List<? extends TypeMirror> expectedBounds = requiredGenerics.get(i).getBounds();
            List<? extends TypeMirror> actualBounds = actualGenerics.get(i).getBounds();

            if (expectedBounds.size() != actualBounds.size()) {
                return false;
            }

            for (int j = 0; j < expectedBounds.size(); j++) {
                if (!sameType(
                        expectedBounds.get(j),
                        actualBounds.get(j),
                        contract,
                        implementation,
                        required,
                        actual)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < required.getParameters().size(); i++) {
            TypeMirror expected = required.getParameters().get(i).asType();
            TypeMirror found = actual.getParameters().get(i).asType();

            if (!sameType(
                    expected,
                    found,
                    contract,
                    implementation,
                    required,
                    actual)) {
                return false;
            }
        }

        return sameType(
                required.getReturnType(),
                actual.getReturnType(),
                contract,
                implementation,
                required,
                actual);
    }

    private boolean sameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            ExecutableElement requiredMethod,
            ExecutableElement actualMethod) {

        TypeKind expectedKind = expected.getKind();
        TypeKind actualKind = actual.getKind();

        /*
         * Method type variables are matched by position, not by their
         * spelling. Thus <T> T f(T) and <U> U f(U) are equivalent here.
         */
        if (expectedKind == TypeKind.TYPEVAR
                || actualKind == TypeKind.TYPEVAR) {
            int expectedIndex = methodTypeParameterIndex(expected, requiredMethod);
            int actualIndex = methodTypeParameterIndex(actual, actualMethod);

            if (expectedIndex >= 0 || actualIndex >= 0) {
                return expectedIndex >= 0 && expectedIndex == actualIndex;
            }

            return types.isSameType(expected, actual);
        }

        if (expectedKind != actualKind) {
            return false;
        }

        if (expectedKind == TypeKind.ARRAY) {
            return sameType(
                    ((ArrayType) expected).getComponentType(),
                    ((ArrayType) actual).getComponentType(),
                    contract,
                    implementation,
                    requiredMethod,
                    actualMethod);
        }

        if (expectedKind == TypeKind.WILDCARD) {
            WildcardType expectedWildcard = (WildcardType) expected;
            WildcardType actualWildcard = (WildcardType) actual;

            return nullableSameType(
                    expectedWildcard.getExtendsBound(),
                    actualWildcard.getExtendsBound(),
                    contract,
                    implementation,
                    requiredMethod,
                    actualMethod)
                    && nullableSameType(
                            expectedWildcard.getSuperBound(),
                            actualWildcard.getSuperBound(),
                            contract,
                            implementation,
                            requiredMethod,
                            actualMethod);
        }

        if (expectedKind == TypeKind.INTERSECTION) {
            List<? extends TypeMirror> expectedBounds = ((IntersectionType) expected).getBounds();
            List<? extends TypeMirror> actualBounds = ((IntersectionType) actual).getBounds();

            if (expectedBounds.size() != actualBounds.size()) {
                return false;
            }

            for (int i = 0; i < expectedBounds.size(); i++) {
                if (!sameType(
                        expectedBounds.get(i),
                        actualBounds.get(i),
                        contract,
                        implementation,
                        requiredMethod,
                        actualMethod)) {
                    return false;
                }
            }

            return true;
        }

        if (expectedKind == TypeKind.DECLARED) {
            DeclaredType expectedDeclared = (DeclaredType) expected;
            DeclaredType actualDeclared = (DeclaredType) actual;

            // Any use of the annotated contract type is its Self Type.
            if (isContractSelfType(expected, contract)) {
                return types.isSameType(actual, implementation.asType());
            }

            if (!types.isSameType(
                    types.erasure(expected),
                    types.erasure(actual))) {
                return false;
            }

            // A nested declared type's enclosing type is part of its
            // identity, e.g. Outer<String>.Inner vs Outer<Integer>.Inner.
            TypeMirror expectedEnclosing = expectedDeclared.getEnclosingType();
            TypeMirror actualEnclosing = actualDeclared.getEnclosingType();

            if (!sameEnclosingType(
                    expectedEnclosing,
                    actualEnclosing,
                    contract,
                    implementation,
                    requiredMethod,
                    actualMethod)) {
                return false;
            }

            List<? extends TypeMirror> expectedArguments = expectedDeclared.getTypeArguments();
            List<? extends TypeMirror> actualArguments = actualDeclared.getTypeArguments();

            if (expectedArguments.size() != actualArguments.size()) {
                return false;
            }

            for (int i = 0; i < expectedArguments.size(); i++) {
                if (!sameType(
                        expectedArguments.get(i),
                        actualArguments.get(i),
                        contract,
                        implementation,
                        requiredMethod,
                        actualMethod)) {
                    return false;
                }
            }

            return true;
        }

        return types.isSameType(expected, actual);
    }

    private int methodTypeParameterIndex(
            TypeMirror type,
            ExecutableElement method) {

        if (type.getKind() != TypeKind.TYPEVAR) {
            return -1;
        }

        List<? extends TypeParameterElement> parameters = method.getTypeParameters();

        for (int i = 0; i < parameters.size(); i++) {
            if (types.isSameType(type, parameters.get(i).asType())) {
                return i;
            }
        }

        return -1;
    }

    private boolean sameEnclosingType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            ExecutableElement requiredMethod,
            ExecutableElement actualMethod) {

        boolean expectedNone = expected.getKind() == TypeKind.NONE;
        boolean actualNone = actual.getKind() == TypeKind.NONE;

        if (expectedNone || actualNone) {
            return expectedNone && actualNone;
        }

        return sameType(
                expected,
                actual,
                contract,
                implementation,
                requiredMethod,
                actualMethod);
    }

    private boolean nullableSameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            ExecutableElement requiredMethod,
            ExecutableElement actualMethod) {

        if (expected == null || actual == null) {
            return expected == actual;
        }

        return sameType(
                expected,
                actual,
                contract,
                implementation,
                requiredMethod,
                actualMethod);
    }

    private boolean isContractSelfType(
            TypeMirror type,
            TypeElement contract) {

        return type.getKind() == TypeKind.DECLARED
                && types.isSameType(
                        types.erasure(type),
                        types.erasure(contract.asType()));
    }

    private String signature(
            ExecutableElement method,
            TypeElement contract,
            TypeElement implementation) {

        StringBuilder result = new StringBuilder("public static ");

        String typeParameters = formatTypeParameters(method, contract, implementation);

        if (!typeParameters.isEmpty()) {
            result.append(typeParameters).append(' ');
        }

        result.append(displayType(
                method.getReturnType(),
                contract,
                implementation));
        result.append(' ').append(method.getSimpleName()).append('(');

        StringJoiner parameters = new StringJoiner(", ");
        List<? extends Element> methodParameters = method.getParameters();

        for (int i = 0; i < methodParameters.size(); i++) {
            Element parameter = methodParameters.get(i);
            TypeMirror parameterType = parameter.asType();
            String typeText;

            if (method.isVarArgs()
                    && i == methodParameters.size() - 1
                    && parameterType.getKind() == TypeKind.ARRAY) {
                typeText = displayType(
                        ((ArrayType) parameterType).getComponentType(),
                        contract,
                        implementation) + "...";
            } else {
                typeText = displayType(
                        parameterType,
                        contract,
                        implementation);
            }

            parameters.add(typeText + " " + parameter.getSimpleName());
        }

        result.append(parameters).append(')');
        return result.toString();
    }

    private String formatTypeParameters(
            ExecutableElement method,
            TypeElement contract,
            TypeElement implementation) {

        List<? extends TypeParameterElement> typeParameters = method.getTypeParameters();

        if (typeParameters.isEmpty()) {
            return "";
        }

        StringJoiner formattedParameters = new StringJoiner(", ");

        for (TypeParameterElement parameter : typeParameters) {
            StringBuilder formatted = new StringBuilder(parameter.getSimpleName());

            List<? extends TypeMirror> bounds = parameter.getBounds();

            boolean onlyImplicitObjectBound = bounds.size() == 1 && isJavaLangObject(bounds.get(0));

            if (!bounds.isEmpty() && !onlyImplicitObjectBound) {
                StringJoiner formattedBounds = new StringJoiner(" & ");

                for (TypeMirror bound : bounds) {
                    formattedBounds.add(
                            displayType(bound, contract, implementation));
                }

                formatted.append(" extends ").append(formattedBounds);
            }

            formattedParameters.add(formatted);
        }

        return "<" + formattedParameters + ">";
    }

    private boolean isJavaLangObject(TypeMirror type) {
        TypeElement objectElement = elements.getTypeElement("java.lang.Object");

        return objectElement != null
                && types.isSameType(
                        types.erasure(type),
                        types.erasure(objectElement.asType()));
    }

    private String displayType(
            TypeMirror type,
            TypeElement contract,
            TypeElement implementation) {

        if (type.getKind() == TypeKind.ARRAY) {
            return displayType(
                    ((ArrayType) type).getComponentType(),
                    contract,
                    implementation) + "[]";
        }

        if (type.getKind() == TypeKind.DECLARED) {
            if (isContractSelfType(type, contract)) {
                return displayType(implementation.asType(), contract, implementation);
            }

            DeclaredType declared = (DeclaredType) type;
            TypeElement declaredElement = (TypeElement) declared.asElement();
            TypeMirror enclosing = declared.getEnclosingType();

            String rawName;

            if (enclosing.getKind() != TypeKind.NONE) {
                rawName = displayType(enclosing, contract, implementation)
                        + "." + declaredElement.getSimpleName();
            } else {
                rawName = types.erasure(type).toString();
            }

            List<? extends TypeMirror> arguments = declared.getTypeArguments();

            if (arguments.isEmpty()) {
                return rawName;
            }

            StringJoiner formattedArguments = new StringJoiner(", ");

            for (TypeMirror argument : arguments) {
                formattedArguments.add(
                        displayType(argument, contract, implementation));
            }

            return rawName + "<" + formattedArguments + ">";
        }

        if (type.getKind() == TypeKind.WILDCARD) {
            WildcardType wildcard = (WildcardType) type;

            if (wildcard.getExtendsBound() != null) {
                return "? extends " + displayType(
                        wildcard.getExtendsBound(),
                        contract,
                        implementation);
            }

            if (wildcard.getSuperBound() != null) {
                return "? super " + displayType(
                        wildcard.getSuperBound(),
                        contract,
                        implementation);
            }

            return "?";
        }

        if (type.getKind() == TypeKind.INTERSECTION) {
            StringJoiner bounds = new StringJoiner(" & ");

            for (TypeMirror bound : ((IntersectionType) type).getBounds()) {
                bounds.add(displayType(bound, contract, implementation));
            }

            return bounds.toString();
        }

        return type.toString();
    }
}
