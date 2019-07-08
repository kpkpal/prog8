package prog8.ast

import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstProcessor
import prog8.ast.statements.*

interface Node {
    val position: Position
    var parent: Node             // will be linked correctly later (late init)
    fun linkParents(parent: Node)

    fun definingModule(): Module {
        if(this is Module)
            return this
        return findParentNode<Module>(this)!!
    }

    fun definingSubroutine(): Subroutine?  = findParentNode<Subroutine>(this)

    fun definingScope(): INameScope {
        val scope = findParentNode<INameScope>(this)
        if(scope!=null) {
            return scope
        }
        if(this is Label && this.name.startsWith("builtin::")) {
            return BuiltinFunctionScopePlaceholder
        }
        if(this is GlobalNamespace)
            return this
        throw FatalAstException("scope missing from $this")
    }
}

interface IStatement : Node {
    fun process(processor: IAstProcessor) : IStatement
    fun makeScopedName(name: String): String {
        // easy way out is to always return the full scoped name.
        // it would be nicer to find only the minimal prefixed scoped name, but that's too much hassle for now.
        // and like this, we can cache the name even,
        // like in a lazy property on the statement object itself (label, subroutine, vardecl)
        val scope = mutableListOf<String>()
        var statementScope = this.parent
        while(statementScope !is ParentSentinel && statementScope !is Module) {
            if(statementScope is INameScope) {
                scope.add(0, statementScope.name)
            }
            statementScope = statementScope.parent
        }
        if(name.isNotEmpty())
            scope.add(name)
        return scope.joinToString(".")
    }

    val expensiveToInline: Boolean

    fun definingBlock(): Block {
        if(this is Block)
            return this
        return findParentNode<Block>(this)!!
    }
}

interface IFunctionCall {
    var target: IdentifierReference
    var arglist: MutableList<IExpression>
}

interface INameScope {
    val name: String
    val position: Position
    val statements: MutableList<IStatement>
    val parent: Node

    fun linkParents(parent: Node)

    fun subScopes(): Map<String, INameScope> {
        val subscopes = mutableMapOf<String, INameScope>()
        for(stmt in statements) {
            when(stmt) {
                is INameScope -> subscopes[stmt.name] = stmt
                is ForLoop -> subscopes[stmt.body.name] = stmt.body
                is RepeatLoop -> subscopes[stmt.body.name] = stmt.body
                is WhileLoop -> subscopes[stmt.body.name] = stmt.body
                is BranchStatement -> {
                    subscopes[stmt.truepart.name] = stmt.truepart
                    if(stmt.elsepart.containsCodeOrVars())
                        subscopes[stmt.elsepart.name] = stmt.elsepart
                }
                is IfStatement -> {
                    subscopes[stmt.truepart.name] = stmt.truepart
                    if(stmt.elsepart.containsCodeOrVars())
                        subscopes[stmt.elsepart.name] = stmt.elsepart
                }
            }
        }
        return subscopes
    }

    fun getLabelOrVariable(name: String): IStatement? {
        // TODO this is called A LOT and could perhaps be optimized a bit more, but adding a cache didn't make much of a practical runtime difference
        for (stmt in statements) {
            if (stmt is VarDecl && stmt.name==name) return stmt
            if (stmt is Label && stmt.name==name) return stmt
        }
        return null
    }

    fun allDefinedSymbols(): List<Pair<String, IStatement>>  {
        return statements.mapNotNull {
            when (it) {
                is Label -> it.name to it
                is VarDecl -> it.name to it
                is Subroutine -> it.name to it
                is Block -> it.name to it
                else -> null
            }
        }
    }

    fun lookup(scopedName: List<String>, localContext: Node) : IStatement? {
        if(scopedName.size>1) {
            // it's a qualified name, look it up from the root of the module's namespace (consider all modules in the program)
            for(module in localContext.definingModule().program.modules) {
                var scope: INameScope? = module
                for(name in scopedName.dropLast(1)) {
                    scope = scope?.subScopes()?.get(name)
                    if(scope==null)
                        break
                }
                if(scope!=null) {
                    val result = scope.getLabelOrVariable(scopedName.last())
                    if(result!=null)
                        return result
                    return scope.subScopes()[scopedName.last()] as IStatement?
                }
            }
            return null
        } else {
            // unqualified name, find the scope the localContext is in, look in that first
            var statementScope = localContext
            while(statementScope !is ParentSentinel) {
                val localScope = statementScope.definingScope()
                val result = localScope.getLabelOrVariable(scopedName[0])
                if (result != null)
                    return result
                val subscope = localScope.subScopes()[scopedName[0]] as IStatement?
                if (subscope != null)
                    return subscope
                // not found in this scope, look one higher up
                statementScope = statementScope.parent
            }
            return null
        }
    }

    fun containsCodeOrVars() = statements.any { it !is Directive || it.directive == "%asminclude" || it.directive == "%asm"}
    fun containsNoCodeNorVars() = !containsCodeOrVars()

    fun remove(stmt: IStatement) {
        if(!statements.remove(stmt))
            throw FatalAstException("stmt to remove wasn't found in scope")
    }
}

interface IExpression: Node {
    fun constValue(program: Program): LiteralValue?
    fun process(processor: IAstProcessor): IExpression
    fun referencesIdentifier(name: String): Boolean
    fun inferType(program: Program): DataType?

    infix fun isSameAs(other: IExpression): Boolean {
        if(this===other)
            return true
        when(this) {
            is RegisterExpr ->
                return (other is RegisterExpr && other.register==register)
            is IdentifierReference ->
                return (other is IdentifierReference && other.nameInSource==nameInSource)
            is PrefixExpression ->
                return (other is PrefixExpression && other.operator==operator && other.expression isSameAs expression)
            is BinaryExpression ->
                return (other is BinaryExpression && other.operator==operator
                        && other.left isSameAs left
                        && other.right isSameAs right)
            is ArrayIndexedExpression -> {
                return (other is ArrayIndexedExpression && other.identifier.nameInSource == identifier.nameInSource
                        && other.arrayspec.index isSameAs arrayspec.index)
            }
            is LiteralValue -> return (other is LiteralValue && other==this)
        }
        return false
    }
}
