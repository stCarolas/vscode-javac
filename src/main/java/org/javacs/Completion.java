package org.javacs;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Union of the different types of completion provided by JavaCompilerService. Only one of the members will be non-null.
 */
public class Completion {
    public final Element element;
    public final PackagePart packagePart;
    public final TypeElement classSymbol;

    private Completion(Element element, PackagePart packagePart, TypeElement classSymbol) {
        this.element = element;
        this.packagePart = packagePart;
        this.classSymbol = classSymbol;
    }

    public static Completion ofElement(Element element) {
        return new Completion(element, null, null);
    }

    public static Completion ofPackagePart(String fullName, String name) {
        return new Completion(null, new PackagePart(fullName, name), null);
    }

    public static Completion ofClassSymbol(TypeElement forClass) {
        return new Completion(null, null, forClass);
    }

    public static class PackagePart {
        public final String fullName, name;

        public PackagePart(String fullName, String name) {
            this.fullName = fullName;
            this.name = name;
        }
    }
}
