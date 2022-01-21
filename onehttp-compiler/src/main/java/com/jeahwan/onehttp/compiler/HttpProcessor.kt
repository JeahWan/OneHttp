package com.jeahwan.onehttp.compiler

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.IOException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.*
import javax.lang.model.util.SimpleTypeVisitor7

@AutoService(Processor::class)
open class HttpProcessor : AbstractProcessor() {

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.RELEASE_8
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(OneHttp::class.java.canonicalName)
    }

    override fun process(set: Set<TypeElement?>, roundEnvironment: RoundEnvironment): Boolean {

        //参数map
        //private var mParams: ConcurrentHashMap<String?, Any?> = ConcurrentHashMap<String?, Any?>()
        val paramMapClass = ClassName("java.util.concurrent", "ConcurrentHashMap")
            .parameterizedBy(
                ClassName("kotlin", "String").copy(nullable = true),
                ClassName("kotlin", "Any").copy(nullable = true)
            )
        val paramMap = PropertySpec.builder("mParams", paramMapClass)
            .mutable()
            .addAnnotation(Volatile::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer("%T()", paramMapClass)
            .build()

        //companion 单例INSTANCE
        //private var INSTANCE: AutoRequest? = null
        val companion = TypeSpec.companionObjectBuilder()
            .addProperty(
                PropertySpec.builder(
                    "INSTANCE",
                    ClassName(
                        "com.jeahwan.onehttp",
                        "AutoRequest"
                    ).copy(nullable = true)
                )
                    .mutable()
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("null")
                    .build()
            )
            .addProperty(paramMap)
            .addFunction(
                FunSpec.builder("getInstance")
                    .returns(ClassName("com.jeahwan.onehttp", "AutoRequest"))
                    .addCode(
                        "if (INSTANCE == null) { \n " +
                                "synchronized(AutoRequest::class.java) { \n " +
                                "if (INSTANCE == null) INSTANCE = AutoRequest() \n" +
                                "} \n } \n " +
                                "return INSTANCE!!"
                    )
                    .build()
            )
            .build()

        //创建AutoRequest类
        val requestClass: TypeSpec.Builder = TypeSpec.classBuilder("AutoRequest")
            .superclass(ClassName("com.jeahwan.onehttp", "HttpMethods"))
            .addFunction(
                //私有构造
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addType(companion)
            .addModifiers(KModifier.FINAL)

        //普通参数方法 需要传入map
        requestClass.addFunction(
            FunSpec.builder("params")
                .returns(ClassName("com.jeahwan.onehttp", "AutoRequest"))
                .addParameter(
                    "params", ClassName("java.util.concurrent", "ConcurrentHashMap")
                        .parameterizedBy(
                            ClassName("kotlin", "String").copy(nullable = true),
                            ClassName("kotlin", "Any").copy(nullable = true)
                        )
                )
                .addCode("mParams = params\n return this")
                .build()
        )

        //lambda 直接{}
        requestClass.addFunction(
            FunSpec.builder("params")
                .returns(ClassName("com.jeahwan.onehttp", "AutoRequest"))
                .addParameter(
                    ParameterSpec.builder(
                        "block",
                        LambdaTypeName.get(
                            paramMapClass,
                            emptyList(),
                            ClassName("kotlin", "Unit")
                        )
                    ).build()
                )
                .addCode("mParams = object : ConcurrentHashMap<String?, Any?>() { init { block(this) }}\n")
                .addCode("return this")
                .build()
        )

        //pairs可变参数  xx to xx,...
        requestClass.addFunction(
            FunSpec.builder("params")
                .returns(ClassName("com.jeahwan.onehttp", "AutoRequest"))
                .addParameter(
                    ParameterSpec.builder(
                        "pairs", ClassName("kotlin", "Pair")
                            .parameterizedBy(
                                ClassName("kotlin", "String").copy(nullable = true),
                                ClassName("kotlin", "Any").copy(nullable = true)
                            ), KModifier.VARARG
                    ).build()
                )
                .addCode("pairs.forEach {if (it.first != null && it.second != null) {mParams[it.first] = it.second}}\n")
                .addCode("return this")
                .build()
        )

        //遍历OneHttp注解的接口 分析参数 返回值等
        var returnClass: TypeName
        for (serviceClass in roundEnvironment.getElementsAnnotatedWith(
            OneHttp::class.java
        )) {
            for (method in serviceClass.enclosedElements) {
                val executableElement: ExecutableElement = method as ExecutableElement
                run loop@{
                    for (annotationMirror in executableElement.annotationMirrors) {
                        if (annotationMirror.annotationType.toString()
                                .contains(OneHttpIgnore::class.java.simpleName)
                        ) {
                            return@loop
                        }
                    }
                    val returnType: DeclaredType = executableElement.returnType as DeclaredType
                    //处理字符串 拿到需要的返回值类型
                    println(returnType.toString())
//                    returnClass = (returnType.typeArguments[0] as DeclaredType).typeArguments[0].asTypeName()
                    returnClass =
                        (returnType.typeArguments[0] as DeclaredType).typeArguments[0].asTypeNameCustomize()

                    //取得方法参数列表 生成observable
                    val methodParameters: List<VariableElement?> = executableElement.parameters
                    val observableParamStr =
                        StringBuilder("(mService as " + (method.enclosingElement as TypeElement).qualifiedName + ")." + method.simpleName + "(")
                    for (i in 0 until methodParameters.size - 1) {
                        observableParamStr.append(methodParameters[i]?.asType())
                            .append(".valueOf(mParams.get(\"")
                            .append(methodParameters[i]?.simpleName.toString())
                            .append("\")), ")
                    }
                    observableParamStr.append("mParams)")

                    //生成方法并添加到类中
                    requestClass
                        .addFunction(
                            //kotlin 函数回调方法
                            FunSpec.builder(method.simpleName.toString())
                                .addModifiers(KModifier.PUBLIC)
                                .returns(Void.TYPE)
                                .addParameter(
                                    ParameterSpec.builder(
                                        "onSucc",
                                        LambdaTypeName.get(
                                            null,
                                            arrayListOf(
                                                ParameterSpec.builder("data", returnClass).build()
                                            ),
                                            ClassName("kotlin", "Unit")
                                        )
                                    ).build()
                                )
                                .addParameter(
                                    ParameterSpec.builder(
                                        "onError",
                                        LambdaTypeName.get(
                                            null,
                                            arrayListOf(
                                                ParameterSpec.builder(
                                                    "t",
                                                    ClassName("kotlin", "Throwable")
                                                )
                                                    .build()
                                            ),
                                            ClassName("kotlin", "Unit")
                                        ).copy(nullable = true)
                                    ).defaultValue("null").build()
                                )
                                .addParameter(
                                    ParameterSpec.builder("needLoading", Boolean::class.java)
                                        .defaultValue("true")
                                        .build()
                                )
                                .addParameter(
                                    ParameterSpec.builder("overrideErrorSuper", Boolean::class.java)
                                        .defaultValue("true")
                                        .build()
                                )
                                .addStatement("com.jeahwan.onehttp.SubscriberKt.toSubscribe($observableParamStr, onSucc, onError, needLoading, overrideErrorSuper)")
                                .build()
                        )
                        .addFunction(
                            //kotlin 函数回调方法重载 只有onSucc 支持lambda
                            FunSpec.builder(method.simpleName.toString())
                                .addModifiers(KModifier.PUBLIC)
                                .returns(Void.TYPE)
                                .addParameter(
                                    ParameterSpec.builder(
                                        "onSucc",
                                        LambdaTypeName.get(
                                            null,
                                            arrayListOf(
                                                ParameterSpec.builder("data", returnClass).build()
                                            ),
                                            ClassName("kotlin", "Unit")
                                        )
                                    ).build()
                                )
                                .addStatement("${method.simpleName}(onSucc,null,true, true)")
                                .build()
                        )
                }
            }
        }

        //生成kt类
        try {
            val file = FileSpec.builder("com.jeahwan.onehttp", "AutoRequest")
                .addType(requestClass.build()).build()
            file.writeTo(processingEnv.filer)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        //生成kt扩展类
        try {
            val file = FileSpec.builder("com.jeahwan.onehttp", "AutoRequestKt")
                .addType(
                    TypeSpec.objectBuilder("AutoRequestKt")
                        .addFunction(
                            FunSpec.builder("AutoRequestScope")
                                .receiver(ClassName("kotlin", "Any"))
                                .addParameter(
                                    ParameterSpec.builder(
                                        "block",
                                        LambdaTypeName.get(
                                            ClassName(
                                                "com.jeahwan.onehttp",
                                                "AutoRequest"
                                            ),
                                            emptyList(),
                                            ClassName("kotlin", "Unit")
                                        )
                                    ).build()
                                )
                                .addCode("block(AutoRequest.getInstance())")
                                .build()
                        ).build()
                ).build()
            file.writeTo(processingEnv.filer)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return false
    }

    /**
     * 自定义实现TypeName转换，为了中间转classname使用[asClassNameCustomize]
     */
    private fun TypeMirror.asTypeNameCustomize(): TypeName {
        return this.accept(
            object : SimpleTypeVisitor7<TypeName, Void?>() {
                override fun visitDeclared(t: DeclaredType, p: Void?): TypeName {
                    val rawType: ClassName = (t.asElement() as TypeElement).asClassNameCustomize()
                    val enclosingType = t.enclosingType
                    val enclosing = if (enclosingType.kind != TypeKind.NONE &&
                        Modifier.STATIC !in t.asElement().modifiers
                    )
                        enclosingType.accept(this, null) else
                        null
                    if (t.typeArguments.isEmpty() && enclosing !is ParameterizedTypeName) {
                        return rawType
                    }

                    val typeArgumentNames = mutableListOf<TypeName>()
                    for (typeArgument in t.typeArguments) {
                        typeArgumentNames += typeArgument.asTypeNameCustomize()
                    }
                    return if (enclosing is ParameterizedTypeName)
                        enclosing.nestedClass(rawType.simpleName, typeArgumentNames) else
                        rawType.parameterizedBy(typeArgumentNames)
                }
            },
            null
        )
    }

    /**
     * 自定义实现ClassName转换,为了中间使用[toKotlinType]将java包替换为kotlin包
     */
    private fun TypeElement.asClassNameCustomize(): ClassName {
        fun isClassOrInterface(e: Element) = e.kind.isClass || e.kind.isInterface

        fun getPackage(type: Element): PackageElement {
            var t = type
            while (t.kind != ElementKind.PACKAGE) {
                t = t.enclosingElement
            }
            return t as PackageElement
        }

        val names = mutableListOf<String>()
        var e: Element = this
        while (isClassOrInterface(e)) {
            val eType = e as TypeElement
            require(eType.nestingKind == NestingKind.TOP_LEVEL || eType.nestingKind == NestingKind.MEMBER) {
                "unexpected type testing"
            }
            names += eType.simpleName.toString().toKotlinType()
            e = eType.enclosingElement
        }
        names.reverse()
        return ClassName(getPackage(this).qualifiedName.toString().toKotlinType(), names)
    }

    /**
     * java包、类 修改为kotlin
     */
    private fun String.toKotlinType(): String {
        return when (this) {
            "Integer" -> "Int"
            "java.lang" -> "kotlin"
            "java.util" -> "kotlin.collections"
            else -> this
        }
    }
}