package prog8.compiler

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstModification
import prog8.ast.statements.*


internal class BeforeAsmGenerationAstChanger(val program: Program, val errors: ErrorReporter) : AstWalker() {

    private val noModifications = emptyList<IAstModification>()

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if (decl.value == null && decl.type == VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            // a numeric vardecl without an initial value is initialized with zero.
            decl.value = decl.zeroElementValue()
        }
        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // Try to replace A = B <operator> Something  by A= B, A = A <operator> Something
        // this triggers the more efficent augmented assignment code generation more often.
        if(!assignment.isAugmentable
                && assignment.target.identifier != null
                && assignment.target.isNotMemory(program.namespace)) {
            val binExpr = assignment.value as? BinaryExpression
            if(binExpr!=null && binExpr.operator !in comparisonOperators) {
                if(binExpr.left !is BinaryExpression) {
                    val assignLeft = Assignment(assignment.target, binExpr.left, assignment.position)
                    return listOf(
                            IAstModification.InsertBefore(assignment, assignLeft, parent),
                            IAstModification.ReplaceNode(binExpr.left, assignment.target.toExpression(), binExpr))
                }
            }
        }
        return noModifications
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
                    val target = AssignTarget(IdentifierReference(listOf(it.name), it.position), null, null, it.position)
                    val assign = Assignment(target, initValue, it.position)
                    initValue.parent = assign
                    IAstModification.InsertFirst(assign, scope)
                } +  decls.map { IAstModification.ReplaceNode(it, NopStatement(it.position), scope) } +
                     decls.map { IAstModification.InsertFirst(it, sub) }    // move it up to the subroutine
            }
        }
        return noModifications
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

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        // see if we can remove superfluous typecasts (outside of expressions)
        // such as casting byte<->ubyte,  word<->uword
        // Also the special typecast of a reference type (str, array) to an UWORD will be changed into address-of.
        val sourceDt = typecast.expression.inferType(program).typeOrElse(DataType.STRUCT)
        if (typecast.type in ByteDatatypes && sourceDt in ByteDatatypes
                || typecast.type in WordDatatypes && sourceDt in WordDatatypes) {
            if(typecast.parent !is Expression) {
                return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
            }
        }


        // Note: for various reasons (most importantly, code simplicity), the code generator assumes/requires
        // that the types of assignment values and their target are the same,
        // and that the types of both operands of a binaryexpression node are the same.
        // So, it is not easily possible to remove the typecasts that are there to make these conditions true.
        // The only place for now where we can do this is for:
        //    asmsub register pair parameter.

        if(typecast.type in WordDatatypes) {
            val fcall = typecast.parent as? IFunctionCall
            if (fcall != null) {
                val sub = fcall.target.targetStatement(program.namespace) as? Subroutine
                if (sub != null && sub.isAsmSubroutine) {
                    return listOf(IAstModification.ReplaceNode(typecast, typecast.expression, parent))
                }
            }
        }

        if(sourceDt in PassByReferenceDatatypes) {
            if(typecast.type==DataType.UWORD) {
                return listOf(IAstModification.ReplaceNode(
                        typecast,
                        AddressOf(typecast.expression as IdentifierReference, typecast.position),
                        parent
                ))
            } else {
                errors.err("cannot cast pass-by-reference value to type ${typecast.type} (only to UWORD)", typecast.position)
            }
        }

        return noModifications
    }
}
