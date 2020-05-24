package de.escalon.xml.xjc;

import com.sun.codemodel.JDefinedClass;
import com.sun.tools.xjc.outline.ClassOutline;

/**
 * Holds changes for class.
 */
public class ChangeSet {
  public final ClassOutline sourceClassOutline;
  public final ClassOutline targetClassOutline;
  public final JDefinedClass definedClass;

  public ChangeSet(ClassOutline sourceClassOutline, ClassOutline targetClassOutline,
      JDefinedClass definedClass) {
    super();
    this.sourceClassOutline = sourceClassOutline;
    this.targetClassOutline = targetClassOutline;
    this.definedClass = definedClass;
  }

  public String getAliasBeanName() {
    return definedClass.fullName();
  }
}
