package org.javacs;

import javax.lang.model.element.Element;

/**
 * Union of the different types of completion provided by JavaCompilerService. Only one of the members will be non-null.
 */
public class Completion {
    public final Element element;
    public final PackagePart packagePart;

    private Completion(Element element, PackagePart packagePart) {
        this.element = element;
        this.packagePart = packagePart;
    }

    public static Completion ofElement(Element element) {
        return new Completion(element, null);
    }

    public static Completion ofPackagePart(String fullName, String name) {
        return new Completion(null, new PackagePart(fullName, name));
    }

    public static class PackagePart {
        public final String fullName, name;

        public PackagePart(String fullName, String name) {
            this.fullName = fullName;
            this.name = name;
        }
    }
}
