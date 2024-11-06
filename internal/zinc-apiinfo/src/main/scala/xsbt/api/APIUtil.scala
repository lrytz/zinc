/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbt.api

import xsbti.api._

object APIUtil {
  val modifiersToByte = (m: Modifiers) => {
    import m._
    def x(b: Boolean, bit: Int) = if (b) 1 << bit else 0
    (x(isAbstract, 0) | x(isOverride, 1) | x(isFinal, 2) | x(isSealed, 3) | x(isImplicit, 4) | x(
      isLazy,
      5
    ) | x(isMacro, 6)).toByte
  }
  val byteToModifiers = (b: Byte) => {
    def x(bit: Int) = (b & (1 << bit)) != 0
    new Modifiers(x(0), x(1), x(2), x(3), x(4), x(5), x(6), x(7))
  }

  def isScalaSourceName(name: String): Boolean = name.endsWith(".scala")

  def hasMacro(c: ClassLike): Boolean = {
    val check = new HasMacro
    check.visitDefinition(c)
    check.hasMacro
  }

  def isAnnotationDefinition(c: ClassLike): Boolean =
    c.structure.parents.flatMap(Discovery.simpleName)
      .contains("java.lang.annotation.Annotation")

  private[this] class HasMacro extends Visit {
    var hasMacro = false

    // Don't visit inherited definitions since we consider that a class
    // that inherits a macro does not have a macro.
    override def visitStructure0(structure: Structure): Unit = {
      visitTypes(structure.parents)
      visitDefinitions(structure.declared)
    }

    override def visitModifiers(m: Modifiers): Unit = {
      hasMacro ||= m.isMacro
      super.visitModifiers(m)
    }
  }

  def minimize(api: ClassLike): ClassLike =
    minimizeClass(api)
  def minimizeDefinitions(ds: Array[Definition]): Array[Definition] =
    ds flatMap minimizeDefinition
  def minimizeDefinition(d: Definition): Array[Definition] =
    d match {
      case c: ClassLike => Array(minimizeClass(c))
      case _            => emptyDefs
    }
  def minimizeClass(c: ClassLike): ClassLike = {
    val savedAnnotations = Discovery.defAnnotations(c.structure, (_: Any) => true).toArray[String]
    val struct = minimizeStructure(c.structure, c.definitionType == DefinitionType.Module)
    ClassLike.of(
      c.name,
      c.access,
      c.modifiers,
      c.annotations,
      c.definitionType,
      emptyTypeLzy,
      lzy(struct),
      savedAnnotations,
      c.childrenOfSealedClass,
      c.topLevel,
      c.typeParameters
    )
  }

  def minimizeStructure(s: Structure, isModule: Boolean): Structure =
    Structure.of(
      lzy(s.parents),
      filterDefinitions(s.declared, isModule),
      filterDefinitions(s.inherited, isModule)
    )
  def filterDefinitions(
      ds: Array[ClassDefinition],
      isModule: Boolean
  ): Lazy[Array[ClassDefinition]] = {
    val mains = if (isModule) ds.filter(Discovery.isMainMethod) else emptyClassDefs
    if (mains.isEmpty) emptyClassDefsLzy else lzy(mains)
  }

  def isNonPrivate(d: Definition): Boolean = isNonPrivate(d.access)

  /** Returns false if the `access` is `Private` and qualified, true otherwise.*/
  def isNonPrivate(access: Access): Boolean =
    access match {
      case p: Private if !p.qualifier.isInstanceOf[IdQualifier] => false
      case _                                                    => true
    }
  private val emptyModifiers =
    new Modifiers(false, false, false, false, false, false, false, false)
  private[this] val emptyType = EmptyType.of()
  private val emptyTypeLzy = lzy(emptyType: Type)
  private val emptyDefs = Array.empty[Definition]
  private val emptyClassDefs = Array.empty[ClassDefinition]
  private val emptyClassDefsLzy = lzy(emptyClassDefs)
  private val emptyStructure = Structure.of(lzy(Array.empty), emptyClassDefsLzy, emptyClassDefsLzy)
  private val emptyStructureLzy = lzy(emptyStructure)
  private val emptyClassLikeTemplate =
    ClassLike.of(
      null,
      Public.of(),
      emptyModifiers,
      Array.empty,
      null,
      emptyTypeLzy,
      emptyStructureLzy,
      Array.empty,
      Array.empty,
      true,
      Array.empty
    )
  def emptyClassLike(name: String, definitionType: DefinitionType): ClassLike =
    ClassLike.of(
      name,
      emptyClassLikeTemplate.access,
      emptyClassLikeTemplate.modifiers,
      emptyClassLikeTemplate.annotations,
      definitionType,
      emptyTypeLzy,
      emptyStructureLzy,
      emptyClassLikeTemplate.savedAnnotations,
      emptyClassLikeTemplate.childrenOfSealedClass,
      emptyClassLikeTemplate.topLevel,
      emptyClassLikeTemplate.typeParameters,
    )

  private[this] def lzy[T <: AnyRef](t: T): Lazy[T] = SafeLazyProxy.strict(t)
}
