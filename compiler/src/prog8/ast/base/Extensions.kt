package prog8.ast.base

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.processing.*
import prog8.compiler.CompilationOptions
import prog8.compiler.BeforeAsmGenerationAstChanger


internal fun Program.checkValid(compilerOptions: CompilationOptions, errors: ErrorReporter) {
    val checker = AstChecker(this, compilerOptions, errors)
    checker.visit(this)
}

internal fun Program.processAstBeforeAsmGeneration(errors: ErrorReporter) {
    val fixer = BeforeAsmGenerationAstChanger(this, errors)
    fixer.visit(this)
    fixer.applyModifications()
}

internal fun Program.reorderStatements() {
    val reorder = StatementReorderer(this)
    reorder.visit(this)
    reorder.applyModifications()
}

internal fun Program.addTypecasts(errors: ErrorReporter) {
    val caster = TypecastsAdder(this, errors)
    caster.visit(this)
    caster.applyModifications()
}

internal fun Program.verifyFunctionArgTypes() {
    val fixer = VerifyFunctionArgTypes(this)
    fixer.visit(this)
}

internal fun Module.checkImportedValid() {
    val imr = ImportedModuleDirectiveRemover()
    imr.visit(this, this.parent)
    imr.applyModifications()
}

internal fun Program.checkRecursion(errors: ErrorReporter) {
    val checker = AstRecursionChecker(namespace, errors)
    checker.visit(this)
    checker.processMessages(name)
}

internal fun Program.checkIdentifiers(errors: ErrorReporter) {

    val checker2 = AstIdentifiersChecker(this, errors)
    checker2.visit(this)

    if(errors.isEmpty()) {
        val transforms = AstVariousTransforms(this)
        transforms.visit(this)
        transforms.applyModifications()
    }

    if (modules.map { it.name }.toSet().size != modules.size) {
        throw FatalAstException("modules should all be unique")
    }
}

internal fun Program.variousCleanups() {
    val process = VariousCleanups()
    process.visit(this)
    process.applyModifications()
}
