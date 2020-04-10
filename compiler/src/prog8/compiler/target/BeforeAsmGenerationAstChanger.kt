package prog8.compiler.target

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.TypecastExpression
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.*


class BeforeAsmGenerationAstChanger(val program: Program, val errors: ErrorReporter) : AstWalker() {

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if (decl.value == null && decl.type == VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            // a numeric vardecl without an initial value is initialized with zero.
            decl.value = decl.zeroElementValue()
        }
        return emptyList()
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        val decls = scope.statements.filterIsInstance<VarDecl>()
        val sub = scope.definingSubroutine()
        if (sub != null) {
            val existingVariables = sub.statements.filterIsInstance<VarDecl>().associateBy { it.name }
            var conflicts = false
            decls.forEach {
                val existing = existingVariables[it.name]
                if (existing != null) {
                    errors.err("variable ${it.name} already defined in subroutine ${sub.name} at ${existing.position}", it.position)
                    conflicts = true
                }
            }
            if (!conflicts) {
                val numericVarsWithValue = decls.filter { it.value != null && it.datatype in NumericDatatypes }
                return numericVarsWithValue.map {
                    val initValue = it.value!!  // assume here that value has always been set by now
                    it.value = null     // make sure no value init assignment for this vardecl will be created later (would be superfluous)
                    val target = AssignTarget(null, IdentifierReference(listOf(it.name), it.position), null, null, it.position)
                    val assign = Assignment(target, null, initValue, it.position)
                    initValue.parent = assign
                    IAstModification.InsertFirst(assign, scope)
                } +  decls.map { IAstModification.ReplaceNode(it, NopStatement(it.position), scope) } +
                     decls.map { IAstModification.InsertFirst(it, sub) }    // move it up to the subroutine
            }
        }
        return emptyList()
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernel routine.
        // and if an assembly block doesn't contain a rts/rti, and some other situations.
        val mods = mutableListOf<IAstModification>()
        val returnStmt = Return(null, subroutine.position)
        if (subroutine.asmAddress == null
                && subroutine.statements.isNotEmpty()
                && subroutine.amountOfRtsInAsm() == 0
                && subroutine.statements.lastOrNull { it !is VarDecl } !is Return
                && subroutine.statements.last() !is Subroutine) {
            mods += IAstModification.InsertLast(returnStmt, subroutine)
        }

        // precede a subroutine with a return to avoid falling through into the subroutine from code above it
        val outerScope = subroutine.definingScope()
        val outerStatements = outerScope.statements
        val subroutineStmtIdx = outerStatements.indexOf(subroutine)
        if (subroutineStmtIdx > 0
                && outerStatements[subroutineStmtIdx - 1] !is Jump
                && outerStatements[subroutineStmtIdx - 1] !is Subroutine
                && outerStatements[subroutineStmtIdx - 1] !is Return
                && outerScope !is Block) {
            mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope as Node)
        }

        return mods
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // modify A = A + 5 back into augmented form A += 5 for easier code generation for optimized in-place assignments
        // also to put code generation stuff together, single value assignment (A = 5) is converted to a special
        // augmented form as wel (with the operator "setvalue")
        if (assignment.aug_op == null) {
            val binExpr = assignment.value as? BinaryExpression
            if (binExpr != null) {
                if (assignment.target.isSameAs(binExpr.left)) {
                    assignment.value = binExpr.right
                    assignment.aug_op = binExpr.operator + "="
                    assignment.value.parent = assignment
                    return emptyList()
                }
            }
            assignment.aug_op = "setvalue"
        }
        return emptyList()
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove superfluous typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword
        val sourceDt = typecast.expression.inferType(program).typeOrElse(DataType.STRUCT)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
                || typecast.type in WordDatatypes && sourceDt in WordDatatypes) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }
        return emptyList()
    }
}