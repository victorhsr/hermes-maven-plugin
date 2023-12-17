package io.github.victorhsr.hermes.maven

import io.github.victorhsr.hermes.core.annotations.DSLIgnore
import io.github.victorhsr.hermes.core.annotations.DSLProperty
import java.util.*
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.ElementFilter

class ElementDefinitionsBuilder(private val processingEnvironment: ProcessingEnvironment) {

    private val classElementDefinitionMap = mutableMapOf<String, ClassElementDefinition>()
    private val annotatedClassesMap = mutableListOf<String>();

    fun resolveElementDefinitions(annotatedClasses: List<TypeElement>): List<ClassElementDefinition> {
        annotatedClasses.forEach{annotatedClassesMap.add(it.asType().toString())}
        annotatedClasses.forEach(this::processAnnotatedClass)
        return this.classElementDefinitionMap.values.toList()
    }

    private fun processAnnotatedClass(typeElement: TypeElement) {
        this.buildClassElementDefinitions(typeElement)
    }

    private fun buildClassElementDefinitions(typeElement: TypeElement) {
        val fullQualifiedClassName = typeElement.asType().toString()
        val classElementDefinition = buildClassElementDefinition(typeElement, true);
        this.classElementDefinitionMap[fullQualifiedClassName] = classElementDefinition

        classElementDefinition.accessibleFields.forEach {
            if (it.shouldClassBeGenerated) {
                try {
                    this.buildClassElementDefinitionsForNestedFields(it.declaredType!!.asElement() as TypeElement, false)
                } catch (ex: Exception) {
                    println("hello")
                }
            }
        }
    }

    private fun buildClassElementDefinitionsForNestedFields(typeElement: TypeElement, isAnnotatedClass: Boolean) {
        val fullQualifiedClassName = typeElement.asType().toString()

        if (!isAnnotatedClass && this.classElementDefinitionMap.containsKey(fullQualifiedClassName)) {
            return
        }

        val classElementDefinition = buildClassElementDefinition(typeElement, isAnnotatedClass);
        this.classElementDefinitionMap[fullQualifiedClassName] = classElementDefinition
    }

    private fun buildClassElementDefinition(typeElement: TypeElement, isAnnotatedClass: Boolean): ClassElementDefinition {
        return ClassElementDefinition(
            element = typeElement,
            wasAnnotated = isAnnotatedClass,
            accessibleFields = this.resolveAccessibleFields(typeElement)
        )
    }

    private fun resolveAccessibleFields(clazz: TypeElement): List<FieldElementDefinition> {
        return this.resolveFields(clazz)
            .filter { it.getAnnotation(DSLIgnore::class.java) == null }
            .map { this.buildFieldElementDefinitions(it) }
    }

    private fun buildFieldElementDefinitions(fieldElement: Element): FieldElementDefinition {
        val isPrimitiveType = fieldElement.asType().kind.isPrimitive
        val declaredType =
            if (!isPrimitiveType) {
                fieldElement.asType() as DeclaredType
            } else null

        val dslPropertyAnnotation = fieldElement.getAnnotation(DSLProperty::class.java)
        val customBuildName = dslPropertyAnnotation?.value

        val fullQualifiedName = fieldElement.asType().toString()

        return FieldElementDefinition(
            fieldName = fieldElement.simpleName.toString(),
            declaredType = declaredType,
            primitiveElement = fieldElement,
            isPrimitiveType = isPrimitiveType,
            shouldClassBeGenerated = annotatedClassesMap.contains(fullQualifiedName),
            customBuildName = customBuildName
        )
    }

    private fun isNativeClass(declaredType: DeclaredType): Boolean {
        val typeElement = declaredType.asElement() as TypeElement
        val qualifiedName = typeElement.qualifiedName

        return qualifiedName.startsWith("java.")
                || qualifiedName.startsWith("sun.")
    }

    private fun hasDefaultConstructor(declaredType: DeclaredType): Boolean {
        val element = declaredType.asElement()
        return ElementFilter.constructorsIn(element.enclosedElements).any { it.parameters.isEmpty() }
    }

    private fun resolveFields(clazz: TypeElement): List<Element> {

        val setMethodsMap = clazz.enclosedElements
            .filter { it.kind == ElementKind.METHOD }
            .filter { it.simpleName.startsWith("set") }
            .groupBy { it.simpleName.toString() }

        return clazz.enclosedElements
            .filter { it.kind.isField }
            .filter { setMethodsMap.containsKey(this.buildSetMethodName(it.simpleName.toString())) }
            .toList()
    }

    private fun buildSetMethodName(name: String): String {
        return "set${name.replaceFirstChar { it.titlecase(Locale.getDefault()) }}"
    }

}