package org.javacs;

import com.google.common.reflect.ClassPath;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.javacs.Completion.PackagePart;

/**
 * Union of the different types of completion provided by JavaCompilerService. Only one of the members will be non-null.
 */
public class Completion {
    public final Element element;
    public final PackagePart packagePart;
    public final TypeElement classSymbol;
    public final ClassPath.ClassInfo notImportedClass;

    private Completion(
            Element element, PackagePart packagePart, TypeElement classSymbol, ClassPath.ClassInfo notImportedClass) {
        this.element = element;
        this.packagePart = packagePart;
        this.classSymbol = classSymbol;
        this.notImportedClass = notImportedClass;
    }

    public static Completion ofElement(Element element) {
        return new Completion(element, null, null, null);
    }

    public static Completion ofPackagePart(String fullName, String name) {
        return new Completion(null, new PackagePart(fullName, name), null, null);
    }

    public static Completion ofClassSymbol(TypeElement forClass) {
        return new Completion(null, null, forClass, null);
    }

    public static Completion ofNotImportedClass(ClassPath.ClassInfo notImportedClass) {
        return new Completion(null, null, null, notImportedClass);
    }

    public static class PackagePart {
        public final String fullName, name;

        public PackagePart(String fullName, String name) {
            this.fullName = fullName;
            this.name = name;
        }
    }
}
