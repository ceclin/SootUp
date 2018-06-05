package de.upb.soot.signatures;

import com.google.common.base.Objects;

/** Represents the unique fully-qualified name of a Class (aka its signature). */
public class ClassSignature extends TypeSignature {

  /** The simple class name. */
  public final String className;

  /** The package in which the class resides. */
  public final PackageSignature packageSignature;

  /**
   * Internal: Constructs the fully-qualified ClassSignature. Instances should only be created by a
   * {@link SignatureFactory}
   *
   * @param className the simple name of the class, e.g., ClassA NOT my.package.ClassA
   * @param packageSignature the corresponding package
   */
  protected ClassSignature(final String className, final PackageSignature packageSignature) {
    this.className = className;
    this.packageSignature = packageSignature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClassSignature that = (ClassSignature) o;
    return Objects.equal(className, that.className)
        && Objects.equal(packageSignature, that.packageSignature);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(className, packageSignature);
  }

  /**
   * The fully-qualified name of the class. Concat package and class name , e.g.,
   * "java.lang.System".
   *
   * @return fully-qualified name
   */
  public String getFullyQualifiedName() {
    StringBuilder sb = new StringBuilder();
    if (!(packageSignature.packageName.isEmpty() || packageSignature.packageName == null)) {
      sb.append(packageSignature.toString());
      sb.append('.');
    }
    sb.append(className);
    return sb.toString();
  }

  @Override
  public String toString() {
    return getFullyQualifiedName();
  }
}
