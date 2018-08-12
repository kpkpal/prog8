package il65.ast

import il65.parser.il65Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode


/**************************** AST Data classes ****************************/

enum class DataType {
    BYTE,
    WORD,
    FLOAT,
    STR,
    STR_P,
    STR_S,
    STR_PS
}

enum class Register {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    SI,
    SC,
    SZ
}


open class AstException(override var message: String) : Exception(message)
class ExpressionException(override var message: String) : AstException(message)

class SyntaxError(override var message: String, val node: Node?) : AstException(message) {
    fun printError() {
        val location = if(node?.position == null)
            ""
        else
            "[line ${node.position!!.line} col ${node.position!!.startCol}-${node.position!!.endCol}] "
        System.err.println("$location$message")
    }
}


data class Position(val line: Int, val startCol:Int, val endCol: Int)


interface IAstProcessor {
    fun process(expr: PrefixExpression): IExpression
    fun process(expr: BinaryExpression): IExpression
    fun process(directive: Directive): IStatement
}


interface Node {
    val position: Position?     // optional for the sake of easy unit testing
}


interface IStatement : Node {
    fun process(processor: IAstProcessor) : IStatement
}


data class Module(val name: String,
                  var lines: List<IStatement>,
                  override val position: Position? = null) : Node {
    fun process(processor: IAstProcessor): Module {
        lines = lines.map { it.process(processor) }
        return this
    }
}

data class Block(val name: String, val address: Int?, var statements: List<IStatement>,
                 override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor) : IStatement {
        statements = statements.map { it.process(processor) }
        return this
    }
}

data class Directive(val directive: String, val args: List<DirectiveArg>,
                     override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor) : IStatement {
        return processor.process(this)
    }
}

data class DirectiveArg(val str: String?, val name: String?, val int: Int?,
                        override val position: Position? = null) : Node

data class Label(val name: String,
                 override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor) = this
}

data class Return(var values: List<IExpression>,
                  override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor): IStatement {
        values = values.map { it.process(processor) }
        return this
    }
}


interface IVarDecl : IStatement {
    val datatype: DataType
    val arrayspec: ArraySpec?
    val name: String
    var value: IExpression?
}

data class ArraySpec(var x: IExpression,
                     var y: IExpression?,
                     override val position: Position? = null) : Node

data class VarDecl(override val datatype: DataType,
                   override val arrayspec: ArraySpec?,
                   override val name: String,
                   override var value: IExpression?,
                   override val position: Position? = null) : IVarDecl {
    override fun process(processor: IAstProcessor): IStatement {
        value = value?.process(processor)
        return this
    }
}

data class ConstDecl(override val datatype: DataType,
                     override val arrayspec: ArraySpec?,
                     override val name: String,
                     override var value: IExpression?,
                     override val position: Position? = null) : IVarDecl {
    override fun process(processor: IAstProcessor): IStatement {
        value = value?.process(processor)
        return this
    }
}

data class MemoryVarDecl(override val datatype: DataType,
                         override val arrayspec: ArraySpec?,
                         override val name: String,
                         override var value: IExpression?,
                         override val position: Position? = null) : IVarDecl {
    override fun process(processor: IAstProcessor): IStatement {
        value = value?.process(processor)
        return this
    }
}

data class Assignment(var target: AssignTarget, val aug_op : String?, var value: IExpression,
                      override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        value = value.process(processor)
        return this
    }
}

data class AssignTarget(val register: Register?, val identifier: Identifier?,
                        override val position: Position? = null) : Node {
    fun process(processor: IAstProcessor) = this       // for now
}


interface IExpression: Node {
    fun constValue() : LiteralValue?
    fun process(processor: IAstProcessor): IExpression
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

data class PrefixExpression(val operator: String, var expression: IExpression,
                            override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? {
        throw ExpressionException("should have been optimized away before const value was asked")
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class BinaryExpression(var left: IExpression, val operator: String, var right: IExpression,
                            override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? {
        throw ExpressionException("should have been optimized away before const value was asked")
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}

data class LiteralValue(val intvalue: Int? = null,
                        val floatvalue: Double? = null,
                        val strvalue: String? = null,
                        val arrayvalue: List<IExpression>? = null,
                        override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue?  = this
    override fun process(processor: IAstProcessor) = this
}


data class RangeExpr(var from: IExpression, var to: IExpression,
                     override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? = null
    override fun process(processor: IAstProcessor): IExpression {
        from = from.process(processor)
        to = to.process(processor)
        return this
    }
}


data class RegisterExpr(val register: Register,
                        override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
}


data class Identifier(val name: String, val scope: List<String>,
                      override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? {
        // @todo should look up the identifier and return its value if that is a compile time const
        return null
    }

    override fun process(processor: IAstProcessor) = this
}


data class CallTarget(val address: Int?, val identifier: Identifier?,
                      override val position: Position? = null) : Node {
    fun process(processor: IAstProcessor) = this
}


data class PostIncrDecr(var target: AssignTarget, val operator: String,
                        override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        return this
    }
}


data class Jump(var target: CallTarget,
                override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        return this
    }
}


data class FunctionCall(var target: CallTarget, var arglist: List<IExpression>,
                        override val position: Position? = null) : IExpression {
    override fun constValue(): LiteralValue? {
        // if the function is a built-in function and the args are consts, should evaluate!
        return null
    }

    override fun process(processor: IAstProcessor): IExpression {
        target = target.process(processor)
        arglist = arglist.map{it.process(processor)}
        return this
    }
}


data class InlineAssembly(val assembly: String,
                          override val position: Position? = null) : IStatement {
    override fun process(processor: IAstProcessor) = this
}


/***************** Antlr Extension methods to create AST ****************/

fun ParserRuleContext.toPosition(withPosition: Boolean) : Position? {
    return if (withPosition)
        Position(start.line, start.charPositionInLine, stop.charPositionInLine+stop.text.length)
    else
        null
}


fun il65Parser.ModuleContext.toAst(name: String, withPosition: Boolean) =
        Module(name, modulestatement().map { it.toAst(withPosition) }, toPosition(withPosition))


fun il65Parser.ModulestatementContext.toAst(withPosition: Boolean) : IStatement {
    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val block = block()?.toAst(withPosition)
    if(block!=null) return block

    throw UnsupportedOperationException(text)
}


fun il65Parser.BlockContext.toAst(withPosition: Boolean) : IStatement {
    return Block(identifier().text,
            integerliteral()?.toAst(),
            statement().map { it.toAst(withPosition) },
            toPosition(withPosition))
}


fun il65Parser.StatementContext.toAst(withPosition: Boolean) : IStatement {
    val vardecl = vardecl()
    if(vardecl!=null) {
        return VarDecl(vardecl.datatype().toAst(),
                vardecl.arrayspec()?.toAst(withPosition),
                vardecl.identifier().text,
                null,
                vardecl.toPosition(withPosition))
    }

    val varinit = varinitializer()
    if(varinit!=null) {
        return VarDecl(varinit.datatype().toAst(),
                varinit.arrayspec()?.toAst(withPosition),
                varinit.identifier().text,
                varinit.expression().toAst(withPosition),
                varinit.toPosition(withPosition))
    }

    val constdecl = constdecl()
    if(constdecl!=null) {
        val cvarinit = constdecl.varinitializer()
        return ConstDecl(cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(withPosition),
                cvarinit.identifier().text,
                cvarinit.expression().toAst(withPosition),
                cvarinit.toPosition(withPosition))
    }

    val memdecl = memoryvardecl()
    if(memdecl!=null) {
        val mvarinit = memdecl.varinitializer()
        return MemoryVarDecl(mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(withPosition),
                mvarinit.identifier().text,
                mvarinit.expression().toAst(withPosition),
                mvarinit.toPosition(withPosition))
    }

    val assign = assignment()
    if (assign!=null) {
        return Assignment(assign.assign_target().toAst(withPosition),
                null, assign.expression().toAst(withPosition),
                assign.toPosition(withPosition))
    }

    val augassign = augassignment()
    if (augassign!=null)
        return Assignment(augassign.assign_target().toAst(withPosition),
                augassign.operator.text,
                augassign.expression().toAst(withPosition),
                augassign.toPosition(withPosition))

    val post = postincrdecr()
    if(post!=null)
        return PostIncrDecr(post.assign_target().toAst(withPosition),
                post.operator.text, post.toPosition(withPosition))

    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val label=labeldef()
    if(label!=null)
        return Label(label.text, label.toPosition(withPosition))

    val jump = unconditionaljump()
    if(jump!=null)
        return Jump(jump.call_location().toAst(withPosition), jump.toPosition(withPosition))

    val returnstmt = returnstmt()
    if(returnstmt!=null)
        return Return(returnstmt.expression_list().toAst(withPosition))

    val asm = inlineasm()
    if(asm!=null)
        return InlineAssembly(asm.INLINEASMBLOCK().text, asm.toPosition(withPosition))

    throw UnsupportedOperationException(text)
}


fun il65Parser.Call_locationContext.toAst(withPosition: Boolean) : CallTarget {
    val address = integerliteral()?.toAst()
    val identifier = identifier()
    return if(identifier!=null)
        CallTarget(address, identifier.toAst(withPosition), toPosition(withPosition))
    else
        CallTarget(address, scoped_identifier().toAst(withPosition), toPosition(withPosition))
}


fun il65Parser.Assign_targetContext.toAst(withPosition: Boolean) : AssignTarget {
    val register = register()?.toAst()
    val identifier = identifier()
    return if(identifier!=null)
        AssignTarget(register, identifier.toAst(withPosition), toPosition(withPosition))
    else
        AssignTarget(register, scoped_identifier()?.toAst(withPosition), toPosition(withPosition))
}


fun il65Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())


fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())


fun il65Parser.ArrayspecContext.toAst(withPosition: Boolean) = ArraySpec(
        expression(0).toAst(withPosition),
        if (expression().size > 1) expression(1).toAst(withPosition) else null,
        toPosition(withPosition)
)


fun il65Parser.DirectiveContext.toAst(withPosition: Boolean) =
        Directive(directivename.text, directivearg().map { it.toAst(withPosition) }, toPosition(withPosition))


fun il65Parser.DirectiveargContext.toAst(withPosition: Boolean) =
        DirectiveArg(stringliteral()?.text,
                identifier()?.text,
                integerliteral()?.toAst(),
                toPosition(withPosition))


fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> text.toInt()
        il65Parser.HEX_INTEGER -> text.substring(1).toInt(16)
        il65Parser.BIN_INTEGER -> text.substring(1).toInt(2)
        else -> throw UnsupportedOperationException(text)
    }
}


fun il65Parser.ExpressionContext.toAst(withPosition: Boolean) : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        if(booleanlit!=null)
            return LiteralValue(intvalue = if(booleanlit) 1 else 0)
        return LiteralValue(litval.integerliteral()?.toAst(),
                litval.floatliteral()?.toAst(),
                litval.stringliteral()?.text,
                litval.arrayliteral()?.toAst(withPosition),
                litval.toPosition(withPosition)
        )
    }

    if(register()!=null)
        return RegisterExpr(register().toAst(), register().toPosition(withPosition))

    if(identifier()!=null)
        return identifier().toAst(withPosition)

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst(withPosition)

    if(bop!=null)
        return BinaryExpression(left.toAst(withPosition),
                bop.text,
                right.toAst(withPosition),
                toPosition(withPosition))

    if(prefix!=null)
        return PrefixExpression(prefix.text,
                expression(0).toAst(withPosition),
                toPosition(withPosition))

    val funcall = functioncall()
    if(funcall!=null) {
        val location = funcall.call_location().toAst(withPosition)
        return if(funcall.expression_list()==null)
            FunctionCall(location, emptyList(), funcall.toPosition(withPosition))
        else
            FunctionCall(location, funcall.expression_list().toAst(withPosition), funcall.toPosition(withPosition))
    }

    if (rangefrom!=null && rangeto!=null)
        return RangeExpr(rangefrom.toAst(withPosition), rangeto.toAst(withPosition), toPosition(withPosition))

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst(withPosition)        // expression within ( )

    throw UnsupportedOperationException(text)
}


fun il65Parser.Expression_listContext.toAst(withPosition: Boolean) = expression().map{ it.toAst(withPosition) }


fun il65Parser.IdentifierContext.toAst(withPosition: Boolean) : Identifier {
    return Identifier(text, emptyList(), toPosition(withPosition))
}


fun il65Parser.Scoped_identifierContext.toAst(withPosition: Boolean) : Identifier {
    val names = NAME()
    val name = names.last().text
    val scope = names.take(names.size-1)
    return Identifier(name, scope.map { it.text }, toPosition(withPosition))
}


fun il65Parser.FloatliteralContext.toAst() = text.toDouble()


fun il65Parser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw UnsupportedOperationException(text)
}


fun il65Parser.ArrayliteralContext.toAst(withPosition: Boolean) =
        expression().map { it.toAst(withPosition) }
