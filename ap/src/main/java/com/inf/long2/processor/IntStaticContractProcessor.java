package com.inf.long2.processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** Checks a public static Self of(long) factory on concrete implementors of Int. */
@SupportedAnnotationTypes("*")
public final class IntStaticContractProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver()) {
            return false;
        }

        TypeElement intInterface = processingEnv.getElementUtils()
                .getTypeElement("com.inf.long2.Int");
        if (intInterface == null) {
            return false;
        }

        Types types = processingEnv.getTypeUtils();
        for (Element root : round.getRootElements()) {
            checkRecursively(root, intInterface, types);
        }
        return false;
    }

    private void checkRecursively(Element element, TypeElement intInterface, Types types) {
        if (!(element instanceof TypeElement type)) {
            return;
        }

        ElementKind kind = type.getKind();
        boolean isClass = kind == ElementKind.CLASS
                || kind == ElementKind.RECORD
                || kind == ElementKind.ENUM;
        if (isClass
                && !type.getModifiers().contains(Modifier.ABSTRACT)
                && types.isSubtype(types.erasure(type.asType()), types.erasure(intInterface.asType()))
                && !declaresFactory(type, types)) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Class " + type.getQualifiedName()
                            + " must declare: public static " + type.getSimpleName()
                            + " of(long last)",
                    type);
        }

        for (Element child : type.getEnclosedElements()) {
            if (child instanceof TypeElement) {
                checkRecursively(child, intInterface, types);
            }
        }
    }

    private static boolean declaresFactory(TypeElement owner, Types types) {
        for (Element member : owner.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getSimpleName().contentEquals("of")
                    || !method.getModifiers().contains(Modifier.PUBLIC)
                    || !method.getModifiers().contains(Modifier.STATIC)
                    || !method.getTypeParameters().isEmpty()
                    || method.getParameters().size() != 1
                    || method.getParameters().get(0).asType().getKind() != TypeKind.LONG) {
                continue;
            }
            if (types.isSameType(method.getReturnType(), owner.asType())) {
                return true;
            }
        }
        return false;
    }
}
