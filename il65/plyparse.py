"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import math
import builtins
import inspect
import enum
from collections import defaultdict
from typing import Union, Generator, Tuple, List, Optional, Dict, Any, no_type_check
import attr
from ply.yacc import yacc
from .plylex import SourceRef, tokens, lexer, find_tok_column, print_warning
from .datatypes import DataType, VarType, REGISTER_SYMBOLS, REGISTER_BYTES, REGISTER_WORDS, \
    char_to_bytevalue, FLOAT_MAX_NEGATIVE, FLOAT_MAX_POSITIVE


class ProgramFormat(enum.Enum):
    RAW = "raw"
    PRG = "prg"
    BASIC = "basicprg"


class ZpOptions(enum.Enum):
    NOCLOBBER = "noclobber"
    CLOBBER = "clobber"
    CLOBBER_RESTORE = "clobber_restore"


math_functions = {name: func for name, func in vars(math).items()
                  if inspect.isbuiltin(func) and name != "pow" and not name.startswith("_")}
builtin_functions = {name: func for name, func in vars(builtins).items()
                     if inspect.isbuiltin(func) and not name.startswith("_")}


class ParseError(Exception):
    def __init__(self, message: str, sourceref: SourceRef) -> None:
        super().__init__(message)
        self.sourceref = sourceref
        # @todo chain attribute, a list of other exceptions, so we can have more than 1 error at a time.

    def __str__(self):
        return "{} {:s}".format(self.sourceref, self.args[0])


class ExpressionEvaluationError(ParseError):
    pass


class UndefinedSymbolError(LookupError):
    pass


start = "start"


@attr.s(cmp=False, slots=True, frozen=False, repr=False)
class AstNode:
    # all ast nodes have: sourceref, parent, and nodes (=list of zero or more sub-nodes)
    sourceref = attr.ib(type=SourceRef)
    parent = attr.ib(init=False, default=None)   # will be hooked up later
    nodes = attr.ib(type=list, init=False, default=attr.Factory(list))      # type: List['AstNode']

    @property
    def lineref(self) -> str:
        return "src l. " + str(self.sourceref.line)

    def my_scope(self) -> 'Scope':
        # returns the closest Scope in the ancestry of this node, or raises LookupError if no scope is found
        scope = self.parent
        while scope:
            if isinstance(scope, Scope):
                return scope
            scope = scope.parent
        raise LookupError("no scope found in node ancestry", self)

    def all_nodes(self, *nodetypes: type) -> Generator['AstNode', None, None]:
        nodetypes = nodetypes or (AstNode, )
        child_nodes = list(self.nodes)
        for node in child_nodes:
            if isinstance(node, nodetypes):
                yield node
        for node in child_nodes:
            if isinstance(node, AstNode):
                yield from node.all_nodes(*nodetypes)

    def remove_node(self, node: 'AstNode') -> None:
        assert node.parent is self
        self.nodes.remove(node)
        node.parent = None

    def replace_node(self, oldnode: 'AstNode', newnode: 'AstNode') -> None:
        assert oldnode.parent is self
        assert isinstance(newnode, AstNode)
        idx = self.nodes.index(oldnode)
        self.nodes[idx] = newnode
        newnode.parent = self
        oldnode.parent = None

    def add_node(self, newnode: 'AstNode', index: int = None) -> None:
        assert isinstance(newnode, AstNode)
        if index is None:
            self.nodes.append(newnode)
        else:
            self.nodes.insert(index, newnode)
        newnode.parent = self


@attr.s(cmp=False)
class Directive(AstNode):
    name = attr.ib(type=str)
    args = attr.ib(type=list, default=attr.Factory(list))
    # no subnodes.


@attr.s(cmp=False, slots=True, repr=False)
class Scope(AstNode):
    # has zero or more subnodes
    level = attr.ib(type=str, init=True)
    nodes = attr.ib(type=list, init=True)    # requires nodes in __init__
    symbols = attr.ib(init=False)
    name = attr.ib(init=False)          # will be set by enclosing block, or subroutine etc.
    float_const_values = attr.ib(type=dict, default=attr.Factory(dict), init=False)     # floatingpoint number -> float const name
    _save_registers = attr.ib(type=bool, default=None, init=False)

    @property
    def save_registers(self) -> bool:
        if self._save_registers is not None:
            return self._save_registers
        try:
            return self.my_scope().save_registers
        except LookupError:
            return False

    @save_registers.setter
    def save_registers(self, save: bool) -> None:
        self._save_registers = save

    @property
    def parent_scope(self) -> Optional['Scope']:
        parent_scope = self.parent
        while parent_scope and not isinstance(parent_scope, Scope):
            parent_scope = parent_scope.parent
        return parent_scope

    def __attrs_post_init__(self):
        # populate the symbol table for this scope for fast lookups via scope.lookup("name") or scope.lookup("dotted.name")
        self.symbols = {}
        for node in self.nodes:
            assert isinstance(node, AstNode)
            self._populate_symboltable(node)

    def _populate_symboltable(self, node: AstNode) -> None:
        if isinstance(node, (Label, VarDef)):
            if node.name in self.symbols:
                raise ParseError("symbol '{}' already defined at {}".format(node.name, self.symbols[node.name].sourceref), node.sourceref)
            self.symbols[node.name] = node
        elif isinstance(node, (Subroutine, BuiltinFunction)):
            if node.name in self.symbols:
                raise ParseError("symbol '{}' already defined at {}".format(node.name, self.symbols[node.name].sourceref), node.sourceref)
            self.symbols[node.name] = node
        elif isinstance(node, (Block, Scope)):
            if node.name:
                if node.name != "ZP" and node.name in self.symbols:
                    raise ParseError("symbol '{}' already defined at {}"
                                     .format(node.name, self.symbols[node.name].sourceref), node.sourceref)
                self.symbols[node.name] = node

    @no_type_check
    def define_builtin_functions(self) -> None:
        for name, func in math_functions.items():
            f = BuiltinFunction(name=name, func=func, sourceref=self.sourceref)
            self.add_node(f)
        for name, func in builtin_functions.items():
            f = BuiltinFunction(name=name, func=func, sourceref=self.sourceref)
            self.add_node(f)

    def define_float_constant(self, value: float) -> str:
        if value in self.float_const_values:
            return self.float_const_values[value]
        name = "il65_float_const_" + str(1 + len(self.float_const_values))
        self.float_const_values[name] = value
        return name

    def lookup(self, name: str) -> AstNode:
        assert isinstance(name, str)
        if '.' in name:
            # look up the dotted name starting from the topmost scope
            scope = self
            node = self
            while node.parent:
                if isinstance(node.parent, Scope):
                    scope = node.parent
                node = node.parent
            for namepart in name.split('.'):
                if isinstance(scope, (Block, Subroutine)):
                    scope = scope.scope
                if not isinstance(scope, Scope):
                    raise UndefinedSymbolError("undefined symbol: " + name)
                scope = scope.symbols.get(namepart, None)
                if not scope:
                    raise UndefinedSymbolError("undefined symbol: " + name)
            return scope
        else:
            # find the name in nested scope hierarchy
            if name in self.symbols:
                return self.symbols[name]
            parent_scope = self.parent
            while parent_scope and not isinstance(parent_scope, Scope):
                parent_scope = parent_scope.parent
            if parent_scope:
                return parent_scope.lookup(name)
            raise UndefinedSymbolError("undefined symbol: " + name)

    def remove_node(self, node: AstNode) -> None:
        if hasattr(node, "name"):
            try:
                del self.symbols[node.name]     # type: ignore
            except KeyError:
                pass
        super().remove_node(node)

    def replace_node(self, oldnode: AstNode, newnode: AstNode) -> None:
        if hasattr(oldnode, "name"):
            del self.symbols[oldnode.name]  # type: ignore
        super().replace_node(oldnode, newnode)

    def add_node(self, newnode: AstNode, index: int=None) -> None:
        super().add_node(newnode, index)
        self._populate_symboltable(newnode)


def validate_address(obj: AstNode, attrib: attr.Attribute, value: Optional[int]) -> None:
    if value is None:
        return
    if isinstance(obj, Block) and obj.name == "ZP":
        raise ParseError("zeropage block cannot have custom start {:s}".format(attrib.name), obj.sourceref)
    if value < 0x0200 or value > 0xffff:
        raise ParseError("invalid {:s} (must be from $0200 to $ffff)".format(attrib.name), obj.sourceref)


def dimensions_validator(obj: 'DatatypeNode', attrib: attr.Attribute, value: List[int]) -> None:
    if not value:
        return
    dt = obj.to_enum()
    if value and dt not in (DataType.MATRIX, DataType.WORDARRAY, DataType.BYTEARRAY):
        raise ParseError("cannot use a dimension for this datatype", obj.sourceref)
    if dt == DataType.WORDARRAY or dt == DataType.BYTEARRAY:
        if len(value) == 1:
            if value[0] <= 0 or value[0] > 256:
                raise ParseError("array length must be 1..256", obj.sourceref)
        else:
            raise ParseError("array must have only one dimension", obj.sourceref)
    if dt == DataType.MATRIX:
        if len(value) < 2 or len(value) > 3:
            raise ParseError("matrix must have two dimensions, with optional interleave", obj.sourceref)
        if len(value) == 3:
            if value[2] < 1 or value[2] > 256:
                raise ParseError("matrix interleave must be 1..256", obj.sourceref)
        if value[0] < 0 or value[0] > 128 or value[1] < 0 or value[1] > 128:
            raise ParseError("matrix rows and columns must be 1..128", obj.sourceref)


@attr.s(cmp=False, repr=False)
class Block(AstNode):
    # has one subnode: the Scope.
    name = attr.ib(type=str, default=None)
    address = attr.ib(type=int, default=None, validator=validate_address)
    _unnamed_block_labels = {}  # type: Dict[Block, str]

    @property
    def scope(self) -> Scope:
        return self.nodes[0] if self.nodes else None    # type: ignore

    @scope.setter
    def scope(self, scope: Scope) -> None:
        assert isinstance(scope, Scope)
        self.nodes.clear()
        self.nodes.append(scope)
        scope.name = self.name

    @property
    def label(self) -> str:
        if self.name:
            return self.name
        if self in self._unnamed_block_labels:
            return self._unnamed_block_labels[self]
        label = "il65_block_{:d}".format(len(self._unnamed_block_labels))
        self._unnamed_block_labels[self] = label
        return label


@attr.s(cmp=False, repr=False)
class Module(AstNode):
    # has one subnode: the Scope.
    name = attr.ib(type=str)     # filename
    subroutine_usage = attr.ib(type=defaultdict, init=False, default=attr.Factory(lambda: defaultdict(set)))    # will be populated later
    format = attr.ib(type=ProgramFormat, init=False, default=ProgramFormat.PRG)     # can be set via directive
    address = attr.ib(type=int, init=False, default=0xc000, validator=validate_address)     # can be set via directive
    zp_options = attr.ib(type=ZpOptions, init=False, default=ZpOptions.NOCLOBBER)    # can be set via directive

    @property
    def scope(self) -> Scope:
        return self.nodes[0] if self.nodes else None    # type: ignore

    @no_type_check
    def zeropage(self) -> Optional[Block]:
        # return the zeropage block (if defined)
        first_block = next(self.scope.all_nodes(Block))
        if first_block.name == "ZP":
            return first_block
        return None

    @no_type_check
    def main(self) -> Optional[Block]:
        # return the 'main' block (if defined)
        for block in self.scope.all_nodes(Block):
            if block.name == "main":
                return block
        return None


@attr.s(cmp=False)
class Label(AstNode):
    name = attr.ib(type=str)
    # no subnodes.


@attr.s(cmp=False, slots=True, repr=False)
class Expression(AstNode):
    # just a common base class for the nodes that are an expression themselves:
    # ExpressionWithOperator, AddressOf, LiteralValue, SymbolName, Register, SubCall, Dereference
    def is_compile_constant(self) -> bool:
        raise NotImplementedError("implement in subclass")

    def const_value(self) -> Union[int, float, bool, str]:
        raise NotImplementedError("implement in subclass")


@attr.s(cmp=False, slots=True)
class Register(Expression):
    name = attr.ib(type=str, validator=attr.validators.in_(REGISTER_SYMBOLS))
    datatype = attr.ib(type=DataType, init=False)
    # no subnodes.

    def __attrs_post_init__(self):
        if self.name in REGISTER_BYTES:
            self.datatype = DataType.BYTE
        elif self.name in REGISTER_WORDS:
            self.datatype = DataType.WORD
        else:
            self.datatype = None    # register 'SC' etc.

    def __hash__(self) -> int:
        return hash(self.name)

    def __eq__(self, other) -> bool:
        if not isinstance(other, Register):
            return NotImplemented
        return self.name == other.name

    def __lt__(self, other) -> bool:
        if not isinstance(other, Register):
            return NotImplemented
        return self.name < other.name

    def is_compile_constant(self) -> bool:
        return False

    def const_value(self) -> Union[int, float, bool, str]:
        raise TypeError("register doesn't have a constant numeric value", self)


@attr.s(cmp=False)
class PreserveRegs(AstNode):
    registers = attr.ib(type=str)
    # no subnodes.


@attr.s(cmp=False)
class TargetRegisters(AstNode):
    # subnodes is is a list of 1 or more registers.
    # In it's multiple-register form it is only used to be able to parse
    # the result of a subroutine call such as A,X = sub().
    # It will be replaced by a regular Register node if it contains just one register.
    pass


@attr.s(cmp=False, repr=False)
class InlineAssembly(AstNode):
    # no subnodes.
    assembly = attr.ib(type=str)


@attr.s(cmp=False, slots=True)
class DatatypeNode(AstNode):
    # no subnodes.
    name = attr.ib(type=str)
    dimensions = attr.ib(type=list, default=None, validator=dimensions_validator)    # if set, 1 or more dimensions (ints)

    def to_enum(self):
        return {
            "byte": DataType.BYTE,
            "word": DataType.WORD,
            "float": DataType.FLOAT,
            "text": DataType.STRING,
            "ptext": DataType.STRING_P,
            "stext": DataType.STRING_S,
            "pstext": DataType.STRING_PS,
            "matrix": DataType.MATRIX,
            "array": DataType.BYTEARRAY,
            "wordarray": DataType.WORDARRAY
        }[self.name]


@attr.s(cmp=False, repr=False)
class BuiltinFunction(AstNode):
    # This is a pseudo-node that will be artificially injected in the top-most scope,
    # to represent all supported built-in functions or math-functions.
    # No child nodes.
    name = attr.ib(type=str)
    func = attr.ib(type=callable)


@attr.s(cmp=False, repr=False)
class Subroutine(AstNode):
    # one subnode: the Scope.
    name = attr.ib(type=str)
    param_spec = attr.ib(type=list)
    result_spec = attr.ib(type=list)
    address = attr.ib(type=int, default=None, validator=validate_address)

    @property
    def scope(self) -> Scope:
        return self.nodes[0] if self.nodes else None    # type: ignore

    @scope.setter
    def scope(self, scope: Scope) -> None:
        assert isinstance(scope, Scope)
        self.nodes.clear()
        self.nodes.append(scope)
        scope.name = self.name
        if self.address is not None:
            raise ValueError("subroutine must have either a scope or an address, not both")


@attr.s(cmp=True, slots=True, repr=False)
class LiteralValue(Expression):
    # no subnodes.
    value = attr.ib()

    def __repr__(self) -> str:
        return "<LiteralValue value={!r} at {}>".format(self.value, self.sourceref)

    def const_value(self) -> Union[int, float, bool, str]:
        return self.value

    def is_compile_constant(self) -> bool:
        return True


@attr.s(cmp=False)
class AddressOf(Expression):
    # no subnodes.
    name = attr.ib(type=str, validator=attr.validators._InstanceOfValidator(type=str))

    def is_compile_constant(self) -> bool:
        return False

    def const_value(self) -> Union[int, float, bool, str]:
        symdef = self.my_scope().lookup(self.name)
        if isinstance(symdef, VarDef):
            if symdef.zp_address is not None:
                return symdef.zp_address
            if symdef.vartype == VarType.MEMORY:
                return symdef.value.const_value()
            raise TypeError("can only take constant address of a memory mapped variable", self)
        raise TypeError("should be a vardef to be able to take its address", self)


@attr.s(cmp=False, slots=True)
class SymbolName(Expression):
    # no subnodes.
    name = attr.ib(type=str)

    def is_compile_constant(self) -> bool:
        symdef = self.my_scope().lookup(self.name)
        return isinstance(symdef, VarDef) and symdef.vartype == VarType.CONST

    def const_value(self) -> Union[int, float, bool, str]:
        symdef = self.my_scope().lookup(self.name)
        if isinstance(symdef, VarDef) and symdef.vartype == VarType.CONST:
            return symdef.const_value()
        raise TypeError("should be a const vardef to be able to take its constant numeric value", self)


@attr.s(cmp=False)
class Dereference(Expression):
    # one subnode: operand (SymbolName, integer LiteralValue or Register)
    datatype = attr.ib()
    size = attr.ib(type=int, default=None)

    @property
    def operand(self) -> Union[SymbolName, LiteralValue, Register]:
        return self.nodes[0]    # type: ignore

    def __attrs_post_init__(self):
        # convert datatype node to enum + size
        if self.datatype is None:
            assert self.size is None
            self.size = 1
            self.datatype = DataType.BYTE
        elif isinstance(self.datatype, DatatypeNode):
            assert self.size is None
            self.size = self.datatype.dimensions
            if not self.datatype.to_enum().isnumeric():
                raise ParseError("dereference target value must be byte, word, float", self.datatype.sourceref)
            self.datatype = self.datatype.to_enum()
        if self.nodes and not isinstance(self.nodes[0], (SymbolName, LiteralValue, Register)):
            raise TypeError("operand of dereference invalid type", self.nodes[0], self.sourceref)

    def is_compile_constant(self) -> bool:
        return False

    def const_value(self) -> Union[int, float, bool, str]:
        raise TypeError("dereference is not a constant numeric value")


@attr.s(cmp=False)
class IncrDecr(AstNode):
    # increment or decrement something by a CONSTANT value (1 or more)
    # one subnode: target (TargetRegisters, Register, SymbolName, or Dereference).
    operator = attr.ib(type=str, validator=attr.validators.in_(["++", "--"]))
    howmuch = attr.ib(default=1)

    @property
    def target(self) -> Union[TargetRegisters, Register, SymbolName, Dereference]:
        return self.nodes[0]        # type: ignore

    @target.setter
    def target(self, target: Union[TargetRegisters, Register, SymbolName, Dereference]) -> None:
        if isinstance(target, Register):
            if target.name not in REGISTER_BYTES | REGISTER_WORDS:
                raise ParseError("cannot incr/decr that register", self.sourceref)
        if isinstance(target, TargetRegisters):
            raise ParseError("cannot incr/decr multiple registers at once", self.sourceref)
        assert isinstance(target, (Register, SymbolName, Dereference))
        self.nodes.clear()
        self.nodes.append(target)

    def __attrs_post_init__(self):
        # make sure the amount is always >= 0
        if self.howmuch < 0:
            self.howmuch = -self.howmuch
            self.operator = "++" if self.operator == "--" else "--"


@attr.s(cmp=False, slots=True, repr=False)
class ExpressionWithOperator(Expression):
    # 2 nodes: left (Expression), right (not present if unary, Expression if not unary)
    operator = attr.ib(type=str)
    # when evaluating the expression, does it have to be a compile-time constant value?
    must_be_constant = attr.ib(type=bool, init=False, default=False)

    @property
    def unary(self) -> bool:
        return len(self.nodes) == 1

    @property
    def left(self) -> Expression:
        return self.nodes[0]    # type: ignore

    @left.setter
    def left(self, newleft: Expression) -> None:
        if self.nodes:
            self.nodes[0] = newleft
        else:
            self.nodes.append(newleft)

    @property
    def right(self) -> Optional[Expression]:
        return self.nodes[1] if len(self.nodes) == 2 else None    # type: ignore

    @right.setter
    def right(self, newright: Expression) -> None:
        self.nodes[1] = newright

    def __attrs_post_init__(self):
        assert self.operator not in ("++", "--"), "incr/decr should not be an expression"
        if self.operator == "mod":
            self.operator = "%"   # change it back to the more common '%'

    def const_value(self) -> Union[int, float, bool, str]:
        raise TypeError("an expression is not a constant", self)

    def is_compile_constant(self) -> bool:
        return False

    def evaluate_primitive_constants(self, sourceref: SourceRef) -> LiteralValue:
        # make sure the lvalue and rvalue are primitives, and the operator is allowed
        assert isinstance(self.left, LiteralValue)
        assert isinstance(self.right, LiteralValue)
        if self.operator not in {'+', '-', '*', '/', '//', '~', '|', '&', '%', '<<', '>>', '<', '>', '<=', '>=', '==', '!='}:
            raise ValueError("operator", self.operator)
        estr = "{} {} {}".format(repr(self.left.value), self.operator, repr(self.right.value))
        try:
            lv = LiteralValue(value=eval(estr, {}, {}), sourceref=sourceref)   # type: ignore  # safe because of checks above
            lv.parent = self.parent
            return lv
        except ZeroDivisionError:
            raise ParseError("division by zero", sourceref)
        except Exception as x:
            raise ExpressionEvaluationError("expression error: " + str(x), self.sourceref) from None


@attr.s(cmp=False, repr=False)
class Goto(AstNode):
    # one or two subnodes: target (SymbolName, integer LiteralValue, or Dereference) and optionally: condition (Expression)
    if_stmt = attr.ib(default=None)

    @property
    def target(self) -> Union[SymbolName, LiteralValue, Dereference]:
        return self.nodes[0]    # type: ignore

    @property
    def condition(self) -> Optional[Expression]:
        return self.nodes[1] if len(self.nodes) == 2 else None      # type: ignore


@attr.s(cmp=False, slots=True, repr=False)
class CallArgument(AstNode):
    # one subnode: the value (Expression)
    name = attr.ib(type=str, default=None)

    @property
    def value(self) -> Expression:
        return self.nodes[0]        # type: ignore


@attr.s(cmp=False)
class CallArguments(AstNode):
    # subnodes are zero or more subroutine call arguments (CallArgument)
    nodes = attr.ib(type=list, init=True)    # requires nodes in __init__


@attr.s(cmp=False, repr=False)
class SubCall(Expression):
    # has three subnodes:
    # 0: target (Symbolname, integer LiteralValue, or Dereference),
    # 1: preserve_regs (PreserveRegs)
    # 2: arguments (CallArguments).

    @property
    def target(self) -> Union[SymbolName, LiteralValue, Dereference]:
        return self.nodes[0]        # type: ignore

    @property
    def preserve_regs(self) -> PreserveRegs:
        return self.nodes[1]    # type: ignore

    @property
    def arguments(self) -> CallArguments:
        return self.nodes[2]    # type: ignore

    def is_compile_constant(self) -> bool:
        return False

    def const_value(self) -> Union[int, float, bool, str]:
        raise TypeError("subroutine call is not a constant value", self)


@attr.s(cmp=False, slots=True, repr=False)
class VarDef(AstNode):
    # zero or one subnode: value (Expression).
    name = attr.ib(type=str)
    vartype = attr.ib()
    datatype = attr.ib()
    size = attr.ib(type=list, default=None)
    zp_address = attr.ib(type=int, default=None, init=False)    # the address in the zero page if this var is there, will be set later

    @property
    def value(self) -> Expression:
        return self.nodes[0] if self.nodes else None    # type: ignore

    @value.setter
    def value(self, value: Expression) -> None:
        assert isinstance(value, Expression)
        if self.nodes:
            self.nodes[0] = value
        else:
            self.nodes.append(value)
        if isinstance(value, ExpressionWithOperator):
            # an expression in a vardef should evaluate to a compile-time constant:
            value.must_be_constant = True

    def const_value(self) -> Union[int, float, bool, str]:
        if self.vartype != VarType.CONST:
            raise TypeError("not a constant value", self)
        if self.nodes and isinstance(self.nodes[0], Expression):
            return self.nodes[0].const_value()
        raise ValueError("no value", self)

    def __attrs_post_init__(self):
        # convert vartype to enum
        if self.vartype == "const":
            self.vartype = VarType.CONST
        elif self.vartype == "var":
            self.vartype = VarType.VAR
        elif self.vartype == "memory":
            self.vartype = VarType.MEMORY
        else:
            raise ValueError("invalid vartype", self.vartype)
        # convert datatype node to enum + size
        if self.datatype is None:
            assert self.size is None
            self.size = [1]
            self.datatype = DataType.BYTE
        elif isinstance(self.datatype, DatatypeNode):
            assert self.size is None
            self.size = self.datatype.dimensions or [1]
            self.datatype = self.datatype.to_enum()
            if self.datatype == DataType.MATRIX and len(self.size) not in (2, 3):
                raise ValueError("matrix size should be 2 dimensions with optional interleave", self)
        if self.datatype.isarray() and sum(self.size) in (0, 1):
            print("warning: {}: array/matrix with size 1, use normal byte/word instead".format(self.sourceref))
        if self.value is None and (self.datatype.isnumeric() or self.datatype.isarray()):
            self.value = LiteralValue(value=0, sourceref=self.sourceref)
            self.value.parent = self
        # if it's a matrix with interleave, it must be memory mapped
        if self.datatype == DataType.MATRIX and len(self.size) == 3:
            if self.vartype != VarType.MEMORY:
                raise ParseError("matrix with interleave can only be a memory-mapped variable", self.sourceref)
        # note: value coercion is done later, when all expressions are evaluated


@attr.s(cmp=False, repr=False)
class Return(AstNode):
    # one, two or three subnodes: value_A, value_X, value_Y (all three Expression)
    @property
    def value_A(self) -> Optional[Expression]:
        return self.nodes[0] if len(self.nodes) >= 1 else None    # type: ignore

    @property
    def value_X(self) -> Optional[Expression]:
        return self.nodes[1] if len(self.nodes) >= 2 else None    # type: ignore

    @property
    def value_Y(self) -> Optional[Expression]:
        return self.nodes[2] if len(self.nodes) >= 3 else None    # type: ignore


@attr.s(cmp=False, slots=True, repr=False)
class AssignmentTargets(AstNode):
    # a list of one or more assignment targets (TargetRegisters, Register, SymbolName, or Dereference).
    nodes = attr.ib(type=list, init=True)    # requires nodes in __init__

    def has_memvalue(self) -> bool:
        for t in self.nodes:
            if isinstance(t, Dereference):
                return True
            if isinstance(t, SymbolName):
                symdef = self.my_scope().lookup(t.name)
                if isinstance(symdef, VarDef) and symdef.vartype == VarType.MEMORY:
                    return True
        return False

    def same_targets(self, other: 'AssignmentTargets') -> bool:
        if len(self.nodes) != len(other.nodes):
            return False
        # @todo be able to compare targets in different order as well (sort them)
        for t1, t2 in zip(self.nodes, other.nodes):
            if type(t1) is not type(t2):
                return False
            if isinstance(t1, TargetRegisters):
                pass
            elif isinstance(t1, Register):
                if t1 != t2:  # __eq__ is defined
                    return False
            elif isinstance(t1, SymbolName):
                if t1.name != t2.name:
                    return False
            elif isinstance(t1, Dereference):
                if t1.size != t2.size or t1.datatype != t2.datatype:
                    return False
                op1, op2 = t1.operand, t2.operand
                if type(op1) is not type(op2):
                    return False
                if isinstance(op1, SymbolName):
                    if op1.name != op2.name:
                        return False
                else:
                    if op1 != op2:
                        return False
            else:
                return False
        return True


@attr.s(cmp=False, slots=True, repr=False)
class Assignment(AstNode):
    # can be single- or multi-assignment
    # has two subnodes: left (=AssignmentTargets) and right (=Expression,
    #    or another Assignment but those will be converted into multi assign)

    @property
    def left(self) -> AssignmentTargets:
        return self.nodes[0]    # type: ignore

    @property
    def right(self) -> Expression:
        return self.nodes[1]    # type: ignore

    @right.setter
    def right(self, rvalue: Expression) -> None:
        assert isinstance(rvalue, Expression)
        self.nodes[1] = rvalue


@attr.s(cmp=False, slots=True, repr=False)
class AugAssignment(AstNode):
    # has two subnodes: left (=TargetRegisters, Register, SymbolName, or Dereference) and right (=Expression)
    operator = attr.ib(type=str)

    @property
    def left(self) -> Union[TargetRegisters, Register, SymbolName, Dereference]:
        return self.nodes[0]    # type: ignore

    @property
    def right(self) -> Expression:
        return self.nodes[1]    # type: ignore

    @right.setter
    def right(self, rvalue: Expression) -> None:
        assert isinstance(rvalue, Expression)
        self.nodes[1] = rvalue


def datatype_of(targetnode: AstNode, scope: Scope) -> DataType:
    # tries to determine the DataType of an assignment target node
    if isinstance(targetnode, VarDef):
        return DataType.WORD if targetnode.vartype == VarType.MEMORY else targetnode.datatype
    elif isinstance(targetnode, (Dereference, Register)):
        return targetnode.datatype
    elif isinstance(targetnode, SymbolName):
        symdef = scope.lookup(targetnode.name)
        if isinstance(symdef, VarDef):
            return symdef.datatype
    elif isinstance(targetnode, TargetRegisters):
        if len(targetnode.nodes) == 1:
            return datatype_of(targetnode.nodes[0], scope)
    raise TypeError("cannot determine datatype", targetnode)


def coerce_constant_value(datatype: DataType, value: Expression,
                          sourceref: SourceRef=None) -> Tuple[bool, Expression]:
    # if we're a BYTE type, and the value is a single character, convert it to the numeric value
    assert isinstance(value, Expression)

    def verify_bounds(pvalue: Union[int, float, str]) -> None:
        # if the value is out of bounds, raise an overflow exception
        if isinstance(pvalue, (int, float)):
            if datatype == DataType.BYTE and not (0 <= pvalue <= 0xff):       # type: ignore
                raise OverflowError("value out of range for byte: " + str(pvalue))
            if datatype == DataType.WORD and not (0 <= pvalue <= 0xffff):        # type: ignore
                raise OverflowError("value out of range for word: " + str(pvalue))
            if datatype == DataType.FLOAT and not (FLOAT_MAX_NEGATIVE <= pvalue <= FLOAT_MAX_POSITIVE):      # type: ignore
                raise OverflowError("value out of range for float: " + str(pvalue))

    if isinstance(value, LiteralValue):
        if type(value.value) is str and len(value.value) == 1 and (datatype.isnumeric() or datatype.isarray()):
            # convert a string of length 1 to its numeric character value
            lv = LiteralValue(value=char_to_bytevalue(value.value), sourceref=value.sourceref)   # type: ignore
            lv.parent = value.parent
            return True, lv
        # if we're an integer value and the passed value is float, truncate it (and give a warning)
        if datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX) and isinstance(value.value, float):
            frac = math.modf(value.value)
            if frac != 0:
                print_warning("float value truncated ({} to datatype {})".format(value.value, datatype.name), sourceref=sourceref)
                v2 = int(value.value)
                verify_bounds(v2)
                lv = LiteralValue(value=v2, sourceref=value.sourceref)  # type: ignore
                lv.parent = value.parent
                return True, lv
        if type(value.value) in (int, float):
            verify_bounds(value.value)
        if datatype == DataType.WORD:
            if type(value.value) not in (int, float, str):
                raise TypeError("cannot assign '{:s}' to {:s}".format(type(value.value).__name__, datatype.name.lower()), sourceref)
        elif datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
            if type(value.value) not in (int, float):
                raise TypeError("cannot assign '{:s}' to {:s}".format(type(value.value).__name__, datatype.name.lower()), sourceref)
    elif isinstance(value, (ExpressionWithOperator, SubCall)):
        return False, value
    elif isinstance(value, SymbolName):
        symboldef = value.my_scope().lookup(value.name)
        if isinstance(symboldef, VarDef) and symboldef.vartype == VarType.CONST:
            return True, symboldef.value
    elif isinstance(value, AddressOf):
        try:
            address = value.const_value()
            lv = LiteralValue(value=address, sourceref=value.sourceref)     # type: ignore
            lv.parent = value.parent
            return True, lv
        except TypeError:
            return False, value
    if datatype == DataType.WORD and not isinstance(value, (LiteralValue, Dereference, Register, SymbolName, AddressOf)):
        raise TypeError("cannot assign '{:s}' to {:s}".format(type(value).__name__, datatype.name.lower()), sourceref)
    elif datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT) \
            and not isinstance(value, (LiteralValue, Dereference, Register, SymbolName, AddressOf)):
        raise TypeError("cannot assign '{:s}' to {:s}".format(type(value).__name__, datatype.name.lower()), sourceref)
    return False, value


def check_symbol_definition(name: str, scope: Scope, sref: SourceRef) -> Any:
    try:
        return scope.lookup(name)
    except UndefinedSymbolError as x:
        raise ParseError(str(x), sref)


# ----------------- PLY parser definition follows ----------------------

def p_start(p):
    """
    start :  empty
          |  module_elements
    """
    if p[1]:
        scope = Scope(nodes=p[1], level="module", sourceref=_token_sref(p, 1))
        scope.name = "<" + p.lexer.source_filename + " global scope>"
        p[0] = Module(name=p.lexer.source_filename, sourceref=_token_sref(p, 1))
        p[0].nodes.append(scope)
    else:
        scope = Scope(nodes=[], level="module", sourceref=_token_sref(p, 1))
        scope.name = "<" + p.lexer.source_filename + " global scope>"
        p[0] = Module(name=p.lexer.source_filename, sourceref=SourceRef(lexer.source_filename, 1, 1))
        p[0].nodes.append(scope)


def p_module(p):
    """
    module_elements :  module_elt
                    |  module_elements  module_elt
    """
    if len(p) == 2:
        if p[1] is None:
            p[0] = []
        else:
            p[0] = [p[1]]
    else:
        if p[2] is None:
            p[0] = p[1]
        else:
            p[0] = p[1] + [p[2]]


def p_module_elt(p):
    """
    module_elt :  ENDL
               |  directive
               |  block
    """
    if p[1] != '\n':
        p[0] = p[1]


def p_directive(p):
    """
    directive :  DIRECTIVE  ENDL
              |  DIRECTIVE  directive_args  ENDL
    """
    if len(p) == 3:
        p[0] = Directive(name=p[1], sourceref=_token_sref(p, 1))
    else:
        p[0] = Directive(name=p[1], args=p[2], sourceref=_token_sref(p, 1))


def p_directive_args(p):
    """
    directive_args :  directive_arg
                   |  directive_args  ','  directive_arg
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_directive_arg(p):
    """
    directive_arg :  NAME
                  |  INTEGER
                  |  STRING
                  |  BOOLEAN
    """
    p[0] = p[1]


def p_block_name_addr(p):
    """
    block :  BITINVERT  NAME  INTEGER  endl_opt  scope
    """
    p[0] = Block(name=p[2], address=p[3], sourceref=_token_sref(p, 2))
    p[0].scope = p[5]


def p_block_name(p):
    """
    block :  BITINVERT  NAME  endl_opt  scope
    """
    p[0] = Block(name=p[2], sourceref=_token_sref(p, 2))
    p[0].scope = p[4]


def p_block(p):
    """
    block :  BITINVERT  endl_opt  scope
    """
    p[0] = Block(sourceref=_token_sref(p, 1))
    p[0].scope = p[3]


def p_endl_opt(p):
    """
    endl_opt :  empty
             |  ENDL
    """
    pass


def p_scope(p):
    """
    scope :  '{'  scope_elements_opt  '}'
    """
    p[0] = Scope(nodes=p[2] or [], level="block", sourceref=_token_sref(p, 1))


def p_scope_elements_opt(p):
    """
    scope_elements_opt :  empty
                       |  scope_elements
    """
    p[0] = p[1]


def p_scope_elements(p):
    """
    scope_elements :  scope_element
                   |  scope_elements  scope_element
    """
    if len(p) == 2:
        p[0] = [] if p[1] in (None, '\n') else [p[1]]
    else:
        if p[2] in (None, '\n'):
            p[0] = p[1]
        else:
            p[0] = p[1] + [p[2]]


def p_scope_element(p):
    """
    scope_element :  ENDL
                  |  label
                  |  directive
                  |  vardef
                  |  subroutine
                  |  inlineasm
                  |  statement
    """
    if p[1] != '\n':
        p[0] = p[1]
    else:
        p[0] = None


def p_label(p):
    """
    label :  LABEL
    """
    p[0] = Label(name=p[1], sourceref=_token_sref(p, 1))


def p_inlineasm(p):
    """
    inlineasm :  INLINEASM  ENDL
    """
    p[0] = InlineAssembly(assembly=p[1], sourceref=_token_sref(p, 1))


def p_vardef(p):
    """
    vardef :  VARTYPE  type_opt  NAME  ENDL
    """
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], sourceref=_token_sref(p, 3))


def p_vardef_value(p):
    """
    vardef :  VARTYPE  type_opt  NAME  IS  expression
    """
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], sourceref=_token_sref(p, 3))
    p[0].value = p[5]


def p_type_opt(p):
    """
    type_opt :  DATATYPE  '('  dimensions  ')'
             |  DATATYPE
             |  empty
    """
    if len(p) == 5:
        p[0] = DatatypeNode(name=p[1], dimensions=p[3], sourceref=_token_sref(p, 1))
    elif len(p) == 2 and p[1]:
        p[0] = DatatypeNode(name=p[1], sourceref=_token_sref(p, 1))


def p_dimensions(p):
    """
    dimensions :  INTEGER
               |  dimensions  ','  INTEGER
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_literal_value(p):
    """literal_value : INTEGER
                     | FLOATINGPOINT
                     | STRING
                     | CHARACTER
                     | BOOLEAN"""
    tok = p.slice[-1]
    if tok.type == "CHARACTER":
        p[1] = char_to_bytevalue(p[1])     # character literals are converted to byte value.
    elif tok.type == "BOOLEAN":
        p[1] = int(p[1])    # boolean literals are converted to integer form (true=1, false=0).
    p[0] = LiteralValue(value=p[1], sourceref=_token_sref(p, 1))


def p_subroutine(p):
    """
    subroutine :  SUB  NAME  '('  sub_param_spec  ')'  RARROW  '('  sub_result_spec  ')'  subroutine_body  ENDL
    """
    body = p[10]
    if isinstance(body, Scope):
        p[0] = Subroutine(name=p[2], param_spec=p[4] or [], result_spec=p[8] or [], sourceref=_token_sref(p, 1))
        p[0].scope = body
    elif type(body) is int:
        p[0] = Subroutine(name=p[2], param_spec=p[4] or [], result_spec=p[8] or [], address=body, sourceref=_token_sref(p, 1))
    else:
        raise TypeError("subroutine_body", p.slice)


def p_sub_param_spec(p):
    """
    sub_param_spec : empty
                   | sub_param_list
    """
    p[0] = p[1]


def p_sub_param_list(p):
    """
    sub_param_list :  sub_param
                   |  sub_param_list  ','  sub_param
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_sub_param(p):
    """
    sub_param :  LABEL  REGISTER
              |  REGISTER
    """
    if len(p) == 3:
        p[0] = (p[1], p[2])
    elif len(p) == 2:
        p[0] = (None, p[1])


def p_sub_result_spec(p):
    """
    sub_result_spec :  empty
                    |  '?'
                    |  sub_result_list
    """
    if p[1] == '?':
        p[0] = ['A', 'X', 'Y']      # '?' means: all registers clobbered
    else:
        p[0] = p[1]


def p_sub_result_list(p):
    """
    sub_result_list :  sub_result_reg
                    |  sub_result_list  ','  sub_result_reg
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_sub_result_reg(p):
    """
    sub_result_reg :  REGISTER
                   |  CLOBBEREDREGISTER
    """
    p[0] = p[1]


def p_subroutine_body(p):
    """
    subroutine_body :  scope
                    |  IS INTEGER
    """
    if len(p) == 2:
        p[0] = p[1]
    else:
        p[0] = p[2]


def p_statement(p):
    """
    statement :  assignment  ENDL
              |  aug_assignment ENDL
              |  subroutine_call  ENDL
              |  goto  ENDL
              |  conditional_goto  ENDL
              |  incrdecr  ENDL
              |  return  ENDL
    """
    p[0] = p[1]


def p_incrdecr(p):
    """
    incrdecr :  assignment_target  INCR
             |  assignment_target  DECR
    """
    p[0] = IncrDecr(operator=p[2], sourceref=_token_sref(p, 2))
    p[0].target = p[1]


def p_call_subroutine(p):
    """
    subroutine_call :  calltarget  preserveregs_opt  '('  call_arguments_opt  ')'
    """
    sref = _token_sref(p, 3)
    p[0] = SubCall(sourceref=sref)
    target = p[1]
    if isinstance(target, int):
        target = LiteralValue(value=target, sourceref=sref)
    p[0].nodes.append(target)
    p[0].nodes.append(p[2] or PreserveRegs(registers="", sourceref=sref))
    p[0].nodes.append(CallArguments(nodes=p[4] or [], sourceref=sref))


def p_preserveregs_opt(p):
    """
    preserveregs_opt :  empty
                     |  preserveregs
    """
    p[0] = p[1]


def p_preserveregs(p):
    """
    preserveregs :  PRESERVEREGS
    """
    p[0] = PreserveRegs(registers=p[1], sourceref=_token_sref(p, 1))


def p_call_arguments_opt(p):
    """
    call_arguments_opt :  empty
                       |  call_arguments
    """
    p[0] = p[1]


def p_call_arguments(p):
    """
    call_arguments :  call_argument
                   |  call_arguments  ','  call_argument
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_call_argument(p):
    """
    call_argument :  expression
                  |  register  IS  expression
                  |  NAME  IS  expression
    """
    if len(p) == 2:
        p[0] = CallArgument(sourceref=_token_sref(p, 1))
        p[0].nodes.append(p[1])
    elif len(p) == 4:
        if isinstance(p[1], AstNode):
            sref = p[1].sourceref
        else:
            sref = _token_sref(p, 2)
        p[0] = CallArgument(name=p[1], sourceref=sref)
        p[0].nodes.append(p[3])


def p_return(p):
    """
    return :  RETURN
           |  RETURN  expression
           |  RETURN  expression  ','  expression
           |  RETURN  expression  ','  expression  ','  expression
    """
    if len(p) == 2:
        p[0] = Return(sourceref=_token_sref(p, 1))
    elif len(p) == 3:
        p[0] = Return(sourceref=_token_sref(p, 1))
        p[0].nodes.append(p[2])   # A
    elif len(p) == 5:
        p[0] = Return(sourceref=_token_sref(p, 1))
        p[0].nodes.append(p[2])   # A
        p[0].nodes.append(p[4])   # X
    elif len(p) == 7:
        p[0] = Return(sourceref=_token_sref(p, 1))
        p[0].nodes.append(p[2])   # A
        p[0].nodes.append(p[4])   # X
        p[0].nodes.append(p[6])   # Y


def p_register(p):
    """
    register :  REGISTER
    """
    p[0] = Register(name=p[1], sourceref=_token_sref(p, 1))


def p_goto(p):
    """
    goto :  GOTO  calltarget
    """
    p[0] = Goto(sourceref=_token_sref(p, 1))
    target = p[2]
    if isinstance(target, int):
        target = LiteralValue(value=target, sourceref=p[0].sourceref)
    p[0].nodes.append(target)


def p_conditional_goto_plain(p):
    """
    conditional_goto :  IF  GOTO  calltarget
    """
    p[0] = Goto(if_stmt=p[1], sourceref=_token_sref(p, 1))
    p[0].nodes.append(p[3])


def p_conditional_goto_expr(p):
    """
    conditional_goto :  IF  expression  GOTO  calltarget
    """
    p[0] = Goto(if_stmt=p[1], sourceref=_token_sref(p, 1))
    p[0].nodes.append(p[4])
    p[0].nodes.append(p[2])


def p_calltarget(p):
    """
    calltarget :  symbolname
               |  INTEGER
               |  dereference
    """
    p[0] = p[1]


def p_dereference(p):
    """
    dereference :  '['  dereference_operand  ']'
    """
    p[0] = Dereference(datatype=p[2][1], sourceref=_token_sref(p, 1))
    operand = p[2][0]
    if isinstance(operand, int):
        p[0].nodes.append(LiteralValue(value=operand, sourceref=p[0].sourceref))
    elif isinstance(operand, str):
        p[0].nodes.append(Register(name=operand, sourceref=p[0].sourceref))
    elif isinstance(operand, SymbolName):
        p[0].nodes.append(operand)
    attr.validate(p[0])


def p_dereference_operand(p):
    """
    dereference_operand :  symbolname  type_opt
                        |  REGISTER  type_opt
                        |  INTEGER  type_opt
    """
    p[0] = (p[1], p[2])


def p_symbolname(p):
    """
    symbolname :  NAME
               |  DOTTEDNAME
    """
    p[0] = SymbolName(name=p[1], sourceref=_token_sref(p, 1))


def p_assignment(p):
    """
    assignment :  assignment_target  IS  expression
               |  assignment_target  IS  assignment
    """
    p[0] = Assignment(sourceref=_token_sref(p, 2))
    p[0].nodes.append(AssignmentTargets(nodes=[p[1]], sourceref=p[0].sourceref))
    p[0].nodes.append(p[3])


def p_aug_assignment(p):
    """
    aug_assignment :  assignment_target  AUGASSIGN  expression
    """
    p[0] = AugAssignment(operator=p[2], sourceref=_token_sref(p, 2))
    p[0].nodes.append(p[1])
    p[0].nodes.append(p[3])


precedence = (
    # following the python operator precedence rules mostly; https://docs.python.org/3/reference/expressions.html#operator-precedence
    ('left', 'LOGICOR'),
    ('left', 'LOGICAND'),
    ('right', 'LOGICNOT'),
    ('left', "LT", "GT", "LE", "GE", "EQUALS", "NOTEQUALS"),
    ('left', 'BITOR'),
    ('left', 'BITXOR'),
    ('left', 'BITAND'),
    ('left', 'SHIFTLEFT', 'SHIFTRIGHT'),
    ('left', '+', '-'),
    ('left', '*', '/', 'INTEGERDIVIDE', 'MODULO'),
    ('right', 'UNARY_MINUS', 'BITINVERT', "UNARY_ADDRESSOF"),
    ('left', 'POWER'),
    ('nonassoc', "COMMENT"),
)


def p_expression(p):
    """
    expression :  expression  '+'  expression
               |  expression  '-'  expression
               |  expression  '*'  expression
               |  expression  '/'  expression
               |  expression  MODULO expression
               |  expression  BITOR  expression
               |  expression  BITXOR  expression
               |  expression  BITAND  expression
               |  expression  SHIFTLEFT  expression
               |  expression  SHIFTRIGHT  expression
               |  expression  LOGICOR  expression
               |  expression  LOGICAND  expression
               |  expression  POWER  expression
               |  expression  INTEGERDIVIDE  expression
               |  expression  LT  expression
               |  expression  GT  expression
               |  expression  LE  expression
               |  expression  GE  expression
               |  expression  EQUALS  expression
               |  expression  NOTEQUALS  expression
    """
    p[0] = ExpressionWithOperator(operator=p[2], sourceref=_token_sref(p, 2))
    p[0].nodes.append(p[1])
    p[0].nodes.append(p[3])


def p_expression_uminus(p):
    """
    expression :  '-'  expression  %prec UNARY_MINUS
    """
    p[0] = ExpressionWithOperator(operator=p[1], sourceref=_token_sref(p, 1))
    p[0].nodes.append(p[2])


def p_expression_addressof(p):
    """
    expression :  BITAND  symbolname  %prec UNARY_ADDRESSOF
    """
    p[0] = AddressOf(name=p[2].name, sourceref=_token_sref(p, 1))


def p_unary_expression_bitinvert(p):
    """
    expression :  BITINVERT  expression
    """
    p[0] = ExpressionWithOperator(operator=p[1], sourceref=_token_sref(p, 1))
    p[0].nodes.append(p[2])


def p_unary_expression_logicnot(p):
    """
    expression :  LOGICNOT  expression
    """
    p[0] = ExpressionWithOperator(operator=p[1], sourceref=_token_sref(p, 1))
    p[0].nodes.append(p[2])


def p_expression_group(p):
    """
    expression :  '('  expression  ')'
    """
    p[0] = p[2]


def p_expression_expr_value(p):
    """expression :  expression_value"""
    p[0] = p[1]


def p_expression_value(p):
    """
    expression_value :  literal_value
                     |  symbolname
                     |  register
                     |  subroutine_call
                     |  dereference
    """
    p[0] = p[1]


def p_assignment_target(p):
    """
    assignment_target :  target_registers
                      |  symbolname
                      |  dereference
    """
    if isinstance(p[1], TargetRegisters):
        # if the target registers is just a single register, use that instead
        if len(p[1].nodes) == 1:
            assert isinstance(p[1].nodes[0], Register)
            p[1] = p[1].nodes[0]
    p[0] = p[1]


def p_target_registers(p):
    """
    target_registers :  register
                     |  target_registers  ','  register
    """
    if len(p) == 2:
        p[0] = TargetRegisters(sourceref=_token_sref(p, 1))
        p[0].nodes.append(p[1])
    else:
        p[1].nodes.append(p[3])
        p[0] = p[1]


def p_empty(p):
    """empty :"""
    pass


def p_error(p):
    stack_state_str = '  '.join([symbol.type for symbol in parser.symstack][1:])
    print('\n[ERROR DEBUG: parser state={:d} stack: {} . {} ]'.format(parser.state, stack_state_str, p))
    if p:
        sref = SourceRef(p.lexer.source_filename, p.lineno, find_tok_column(p))
        if p.value in ("", "\n"):
            p.lexer.error_function(sref, "syntax error before end of line")
        else:
            p.lexer.error_function(sref, "syntax error before or at '{:.20s}'", str(p.value).rstrip())
    else:
        lexer.error_function(None, "syntax error at end of input", lexer.source_filename)


def _token_sref(p, token_idx):
    """ Returns the coordinates for the YaccProduction object 'p' indexed
        with 'token_idx'. The coordinate includes the 'lineno' and 'column', starting from 1.
    """
    last_cr = p.lexer.lexdata.rfind('\n', 0, p.lexpos(token_idx))
    if last_cr < 0:
        last_cr = -1
    chunk = p.lexer.lexdata[last_cr:p.lexpos(token_idx)]
    column = len(chunk.expandtabs())
    return SourceRef(p.lexer.source_filename, p.lineno(token_idx), column)


class TokenFilter:
    def __init__(self, lexer):
        self.lexer = lexer
        self.prev_was_EOL = False
        assert "ENDL" in tokens

    def token(self):
        # make sure we only ever emit ONE "ENDL" token in sequence
        if self.prev_was_EOL:
            # skip all EOLS that might follow
            while True:
                tok = self.lexer.token()
                if not tok or tok.type != "ENDL":
                    break
            self.prev_was_EOL = False
        else:
            tok = self.lexer.token()
            self.prev_was_EOL = tok and tok.type == "ENDL"
        return tok


parser = yacc(write_tables=True)


def connect_parents(node: AstNode, parent: AstNode) -> None:
    node.parent = parent
    for childnode in node.nodes:
        if isinstance(childnode, AstNode):
            connect_parents(childnode, node)


def parse_file(filename: str, lexer_error_func=None) -> Module:
    lexer.error_function = lexer_error_func
    lexer.lineno = 1
    lexer.source_filename = filename
    tfilter = TokenFilter(lexer)
    with open(filename, "rU") as inf:
        sourcecode = inf.read()
    result = parser.parse(input=sourcecode, tokenfunc=tfilter.token)
    connect_parents(result, None)
    return result
