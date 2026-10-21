
package com.inf.long2.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("*")
public final class StaticMethodImplementsCheckProcessor
        extends AbstractProcessor {

    private static final String ANNOTATION = "com.inf.long2.StaticMethodImplementsCheck";

    private final Set<String> checked = new HashSet<>();

    private Types types;

    @Override
    public synchronized void init(
            javax.annotation.processing.ProcessingEnvironment env) {
        super.init(env);
        types = env.getTypeUtils();
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

        if (type.getKind().isClass()
                && !type.getModifiers().contains(Modifier.ABSTRACT)) {

            String name = type.getQualifiedName().toString();

            if (checked.add(name)) {
                validate(type);
            }
        }

        for (Element child : type.getEnclosedElements()) {
            if (child instanceof TypeElement) {
                visit(child);
            }
        }
    }

    private void validate(TypeElement implementation) {
        Set<String> visited = new HashSet<>();

        collectContracts(
                implementation.asType(),
                implementation,
                visited);
    }

    private void collectContracts(
            TypeMirror current,
            TypeElement implementation,
            Set<String> visited) {

        for (TypeMirror parentType : types.directSupertypes(current)) {

            if (!(parentType instanceof DeclaredType declared)) {
                continue;
            }

            if (!(declared.asElement() instanceof TypeElement parent)) {
                continue;
            }

            String name = parent.getQualifiedName().toString();

            if (!visited.add(name)) {
                continue;
            }

            if (hasAnnotation(parent)) {
                checkContract(parent, implementation);
            }

            collectContracts(parentType, implementation, visited);
        }
    }

    private boolean hasAnnotation(TypeElement type) {
        for (AnnotationMirror annotation : type.getAnnotationMirrors()) {

            Element element = annotation.getAnnotationType().asElement();

            if (element instanceof TypeElement annotationType
                    && annotationType.getQualifiedName()
                            .contentEquals(ANNOTATION)) {
                return true;
            }
        }

        return false;
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

            // Only public static methods define contracts.
            if (!modifiers.contains(Modifier.PUBLIC)
                    || !modifiers.contains(Modifier.STATIC)) {
                continue;
            }

            if (!hasImplementation(
                    required, contract, implementation)) {

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

        if (!required.getSimpleName()
                .contentEquals(actual.getSimpleName())) {
            return false;
        }

        Set<Modifier> modifiers = actual.getModifiers();

        if (!modifiers.contains(Modifier.PUBLIC)
                || !modifiers.contains(Modifier.STATIC)) {
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
                        contract, implementation,
                        required, actual)) {
                    return false;
                }
            }
        }

        for (int i = 0; i < required.getParameters().size(); i++) {

            TypeMirror expected = required.getParameters().get(i).asType();

            TypeMirror found = actual.getParameters().get(i).asType();

            if (!sameType(
                    expected, found,
                    contract, implementation,
                    required, actual)) {
                return false;
            }
        }

        return sameType(
                required.getReturnType(),
                actual.getReturnType(),
                contract, implementation,
                required, actual);
    }

    private boolean sameType(
            TypeMirror expected,
            TypeMirror actual,
            TypeElement contract,
            TypeElement implementation,
            ExecutableElement requiredMethod,
            ExecutableElement actualMethod) {

        // Method type parameters are matched by position.
        // For example: <T> T f(T) and <U> U f(U).
        if (expected.getKind() == TypeKind.TYPEVAR) {

            List<? extends TypeParameterElement> params = requiredMethod.getTypeParameters();

            for (int i = 0; i < params.size(); i++) {

                if (types.isSameType(
                        expected, params.get(i).asType())) {

                    return actual.getKind() == TypeKind.TYPEVAR
                            && types.isSameType(
                                    actual,
                                    actualMethod.getTypeParameters()
                                            .get(i).asType());
                }
            }
        }

        if (expected.getKind() == TypeKind.ARRAY) {

            return actual.getKind() == TypeKind.ARRAY
                    && sameType(
                            ((ArrayType) expected).getComponentType(),
                            ((ArrayType) actual).getComponentType(),
                            contract, implementation,
                            requiredMethod, actualMethod);
        }

        if (expected.getKind() == TypeKind.WILDCARD) {

            if (actual.getKind() != TypeKind.WILDCARD) {
                return false;
            }

            WildcardType a = (WildcardType) expected;
            WildcardType b = (WildcardType) actual;

            return nullableSameType(
                    a.getExtendsBound(), b.getExtendsBound(),
                    contract, implementation,
                    requiredMethod, actualMethod)
                    && nullableSameType(
                            a.getSuperBound(), b.getSuperBound(),
                            contract, implementation,
                            requiredMethod, actualMethod);
        }

        if (expected.getKind() == TypeKind.DECLARED) {

            if (actual.getKind() != TypeKind.DECLARED) {
                return false;
            }

            // The contract's own type represents the
            // implementing class (self type).
            if (types.isSameType(
                    types.erasure(expected),
                    types.erasure(contract.asType()))) {

                return types.isSameType(
                        actual,
                        types.erasure(implementation.asType()));
            }

            if (!types.isSameType(
                    types.erasure(expected),
                    types.erasure(actual))) {
                return false;
            }

            List<? extends TypeMirror> a = ((DeclaredType) expected).getTypeArguments();

            List<? extends TypeMirror> b = ((DeclaredType) actual).getTypeArguments();

            if (a.size() != b.size()) {
                return false;
            }

            for (int i = 0; i < a.size(); i++) {

                if (!sameType(
                        a.get(i), b.get(i),
                        contract, implementation,
                        requiredMethod, actualMethod)) {
                    return false;
                }
            }

            return true;
        }

        return types.isSameType(expected, actual);
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
                expected, actual,
                contract, implementation,
                requiredMethod, actualMethod);
    }

    private String signature(
            ExecutableElement method,
            TypeElement contract,
            TypeElement implementation) {

        StringJoiner parameters = new StringJoiner(", ");

        for (var parameter : method.getParameters()) {
            parameters.add(
                    displayType(
                            parameter.asType(),
                            contract,
                            implementation));
        }

        return "public static "
                + displayType(
                        method.getReturnType(),
                        contract,
                        implementation)
                + " "
                + method.getSimpleName()
                + "(" + parameters + ")";
    }

    private String displayType(
            TypeMirror type,
            TypeElement contract,
            TypeElement implementation) {

        if (type.getKind() == TypeKind.ARRAY) {
            return displayType(
                    ((ArrayType) type).getComponentType(),
                    contract, implementation) + "[]";
        }

        if (type.getKind() == TypeKind.DECLARED) {

            if (types.isSameType(
                    types.erasure(type),
                    types.erasure(contract.asType()))) {
                return implementation.getSimpleName().toString();
            }

            DeclaredType declared = (DeclaredType) type;

            if (declared.getTypeArguments().isEmpty()) {
                return type.toString();
            }

            StringJoiner args = new StringJoiner(", ");

            for (TypeMirror arg : declared.getTypeArguments()) {
                args.add(displayType(arg, contract, implementation));
            }

            return types.erasure(type) + "<" + args + ">";
        }

        if (type.getKind() == TypeKind.WILDCARD) {

            WildcardType wildcard = (WildcardType) type;

            if (wildcard.getExtendsBound() != null) {
                return "? extends " + displayType(
                        wildcard.getExtendsBound(),
                        contract, implementation);
            }

            if (wildcard.getSuperBound() != null) {
                return "? super " + displayType(
                        wildcard.getSuperBound(),
                        contract, implementation);
            }

            return "?";
        }

        return type.toString();
    }
}
