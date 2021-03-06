package prog8.compiler.target.c64.codegen.assignment

import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.AssemblyError
import prog8.compiler.target.CompilationTarget
import prog8.compiler.target.CpuType
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.toHex


internal class AssignmentAsmGen(private val program: Program, private val asmgen: AsmGen) {

    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, this, asmgen)

    fun translate(assignment: Assignment) {
        val target = AsmAssignTarget.fromAstAssignment(assignment, program, asmgen)
        val source = AsmAssignSource.fromAstSource(assignment.value, program).adjustDataTypeToTarget(target)

        val assign = AsmAssignment(source, target, assignment.isAugmentable, assignment.position)
        target.origAssign = assign

        if(assign.isAugmentable)
            augmentableAsmGen.translate(assign)
        else
            translateNormalAssignment(assign)
    }

    fun translateNormalAssignment(assign: AsmAssignment) {
        when(assign.source.kind) {
            SourceStorageKind.LITERALNUMBER -> {
                // simple case: assign a constant number
                val num = assign.source.number!!.number
                when (assign.source.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignConstantByte(assign.target, num.toShort())
                    DataType.UWORD, DataType.WORD -> assignConstantWord(assign.target, num.toInt())
                    DataType.FLOAT -> assignConstantFloat(assign.target, num.toDouble())
                    else -> throw AssemblyError("weird numval type")
                }
            }
            SourceStorageKind.VARIABLE -> {
                // simple case: assign from another variable
                val variable = assign.source.variable!!
                when (assign.source.datatype) {
                    DataType.UBYTE, DataType.BYTE -> assignVariableByte(assign.target, variable)
                    DataType.UWORD, DataType.WORD -> assignVariableWord(assign.target, variable)
                    DataType.FLOAT -> assignVariableFloat(assign.target, variable)
                    in PassByReferenceDatatypes -> assignAddressOf(assign.target, variable)
                    else -> throw AssemblyError("unsupported assignment target type ${assign.target.datatype}")
                }
            }
            SourceStorageKind.ARRAY -> {
                val value = assign.source.array!!
                val elementDt = assign.source.datatype
                val index = value.arrayspec.index
                val arrayVarName = asmgen.asmVariableName(value.identifier)
                if (index is NumericLiteralValue) {
                    // constant array index value
                    val indexValue = index.number.toInt() * elementDt.memorySize()
                    when (elementDt) {
                        in ByteDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  dex")
                        in WordDatatypes ->
                            asmgen.out("  lda  $arrayVarName+$indexValue |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+$indexValue+1 |  sta  P8ESTACK_HI,x | dex")
                        DataType.FLOAT ->
                            asmgen.out("  lda  #<$arrayVarName+$indexValue |  ldy  #>$arrayVarName+$indexValue |  jsr  c64flt.push_float")
                        else ->
                            throw AssemblyError("weird array type")
                    }
                } else {
                    when (elementDt) {
                        in ByteDatatypes -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  dex")
                        }
                        in WordDatatypes  -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.Y)
                            asmgen.out("  lda  $arrayVarName,y |  sta  P8ESTACK_LO,x |  lda  $arrayVarName+1,y |  sta  P8ESTACK_HI,x |  dex")
                        }
                        DataType.FLOAT -> {
                            asmgen.loadScaledArrayIndexIntoRegister(value, elementDt, CpuRegister.A)
                            asmgen.out("""
                                ldy  #>$arrayVarName
                                clc
                                adc  #<$arrayVarName
                                bcc  +
                                iny
+                               jsr  c64flt.push_float""")
                        }
                        else ->
                            throw AssemblyError("weird array elt type")
                    }
                }
                assignStackValue(assign.target)
            }
            SourceStorageKind.MEMORY -> {
                val value = assign.source.memory!!
                when (value.addressExpression) {
                    is NumericLiteralValue -> {
                        val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                        assignMemoryByte(assign.target, address, null)
                    }
                    is IdentifierReference -> {
                        assignMemoryByte(assign.target, null, value.addressExpression as IdentifierReference)
                    }
                    else -> {
                        asmgen.translateExpression(value.addressExpression)
                        asmgen.out("  jsr  prog8_lib.read_byte_from_address_on_stack |  inx")
                        assignRegisterByte(assign.target, CpuRegister.A)
                    }
                }
            }
            SourceStorageKind.EXPRESSION -> {
                val value = assign.source.expression!!
                when(value) {
                    is AddressOf -> assignAddressOf(assign.target, value.identifier)
                    is NumericLiteralValue -> throw AssemblyError("source kind should have been literalnumber")
                    is IdentifierReference -> throw AssemblyError("source kind should have been variable")
                    is ArrayIndexedExpression -> throw AssemblyError("source kind should have been array")
                    is DirectMemoryRead -> throw AssemblyError("source kind should have been memory")
//                    is TypecastExpression -> {
//                        if(assign.target.kind == TargetStorageKind.STACK) {
//                            asmgen.translateExpression(value)
//                            assignStackValue(assign.target)
//                        } else {
//                            println("!!!!TYPECAST to ${assign.target.kind} $value")
//                            // TODO maybe we can do the typecast on the target directly instead of on the stack?
//                            asmgen.translateExpression(value)
//                            assignStackValue(assign.target)
//                        }
//                    }
//                    is FunctionCall -> {
//                        if (assign.target.kind == TargetStorageKind.STACK) {
//                            asmgen.translateExpression(value)
//                            assignStackValue(assign.target)
//                        } else {
//                            val functionName = value.target.nameInSource.last()
//                            val builtinFunc = BuiltinFunctions[functionName]
//                            if (builtinFunc != null) {
//                                println("!!!!BUILTIN-FUNCCALL target=${assign.target.kind} $value") // TODO optimize certain functions?
//                            }
//                            asmgen.translateExpression(value)
//                            assignStackValue(assign.target)
//                        }
//                    }
                    else -> {
                        // everything else just evaluate via the stack.
                        asmgen.translateExpression(value)
                        assignStackValue(assign.target)
                    }
                }
            }
            SourceStorageKind.REGISTER -> {
                assignRegisterByte(assign.target, assign.source.register!!)
            }
            SourceStorageKind.STACK -> {
                assignStackValue(assign.target)
            }
        }
    }

    private fun assignStackValue(target: AsmAssignTarget) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}")
                    }
                    DataType.UWORD, DataType.WORD -> {
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            sta  ${target.asmVarname}
                            lda  P8ESTACK_HI,x
                            sta  ${target.asmVarname}+1
                        """)
                    }
                    DataType.FLOAT -> {
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            ldy  #>${target.asmVarname}
                            jsr  c64flt.pop_float
                        """)
                    }
                    else -> throw AssemblyError("weird target variable type ${target.datatype}")
                }
            }
            TargetStorageKind.MEMORY -> {
                asmgen.out("  inx")
                storeByteViaRegisterAInMemoryAddress("P8ESTACK_LO,x", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        when(target.datatype) {
                            in ByteDatatypes -> {
                                asmgen.out(" inx | lda  P8ESTACK_LO,x  | sta  ${target.asmVarname}+$scaledIdx")
                            }
                            in WordDatatypes -> {
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  P8ESTACK_HI,x
                                    sta  ${target.asmVarname}+$scaledIdx+1
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.out("""
                                    lda  #<${target.asmVarname}+$scaledIdx
                                    ldy  #>${target.asmVarname}+$scaledIdx
                                    jsr  c64flt.pop_float
                                """)
                            }
                            else -> throw AssemblyError("weird target variable type ${target.datatype}")
                        }
                    }
                    index is IdentifierReference -> {
                        when(target.datatype) {
                            DataType.UBYTE, DataType.BYTE -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out(" inx |  lda  P8ESTACK_LO,x |  sta  ${target.asmVarname},y")
                            }
                            DataType.UWORD, DataType.WORD -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out("""
                                    inx
                                    lda  P8ESTACK_LO,x
                                    sta  ${target.asmVarname},y
                                    lda  P8ESTACK_HI,x
                                    sta  ${target.asmVarname}+1,y
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                                asmgen.out("""
                                    ldy  #>${target.asmVarname}
                                    clc
                                    adc  #<${target.asmVarname}
                                    bcc  +
                                    iny
+                                   jsr  c64flt.pop_float""")
                            }
                            else -> throw AssemblyError("weird dt")
                        }
                    }
                    else -> {
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  P8ESTACK_LO,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when (target.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        when(target.register!!) {
                            RegisterOrPair.A -> asmgen.out(" inx |  lda  P8ESTACK_LO,x")
                            RegisterOrPair.X -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.Y -> asmgen.out(" inx |  ldy  P8ESTACK_LO,x")
                            RegisterOrPair.AX -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldx  #0")
                            RegisterOrPair.AY -> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  #0")
                            else -> throw AssemblyError("can't assign byte from stack to register pair XY")
                        }
                    }
                    DataType.UWORD, DataType.WORD, in PassByReferenceDatatypes -> {
                        when(target.register!!) {
                            RegisterOrPair.AX -> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            RegisterOrPair.AY-> asmgen.out(" inx |  lda  P8ESTACK_LO,x |  ldy  P8ESTACK_HI,x")
                            RegisterOrPair.XY-> throw AssemblyError("can't load X from stack here - use intermediary var? ${target.origAstTarget?.position}")
                            else -> throw AssemblyError("can't assign word to single byte register")
                        }
                    }
                    else -> throw AssemblyError("weird dt")
                }
            }
            TargetStorageKind.STACK -> {}
        }
    }

    private fun assignAddressOf(target: AsmAssignTarget, name: IdentifierReference) {
        val struct = name.memberOfStruct(program.namespace)
        val sourceName = if (struct != null) {
            // take the address of the first struct member instead
            val decl = name.targetVarDecl(program.namespace)!!
            val firstStructMember = struct.nameOfFirstMember()
            // find the flattened var that belongs to this first struct member
            val firstVarName = listOf(decl.name, firstStructMember)
            val firstVar = name.definingScope().lookup(firstVarName, name) as VarDecl
            firstVar.name
        } else {
            asmgen.fixNameSymbols(name.nameInSource.joinToString("."))
        }

        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                        lda  #<$sourceName
                        ldy  #>$sourceName
                        sta  ${target.asmVarname}
                        sty  ${target.asmVarname}+1
                    """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to memory word $target")
            }
            TargetStorageKind.ARRAY -> {
                throw AssemblyError("no asm gen for assign address $sourceName to array ${target.asmVarname}")
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  #<$sourceName |  ldx  #>$sourceName")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<$sourceName |  ldy  #>$sourceName")
                    else -> throw AssemblyError("can't load address in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                val srcname = asmgen.asmVariableName(name)
                asmgen.out("""
                    lda  #<$srcname
                    sta  P8ESTACK_LO,x
                    lda  #>$srcname
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignVariableWord(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    ldy  $sourceName+1
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign wordvar $sourceName to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        when(target.datatype) {
                            in ByteDatatypes -> {
                                asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                            }
                            in WordDatatypes -> {
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname}+$scaledIdx
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+$scaledIdx+1
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.out("""
                                    lda  #<$sourceName
                                    ldy  #>$sourceName
                                    sta  P8ZP_SCRATCH_W1
                                    sty  P8ZP_SCRATCH_W1+1
                                    lda  #<${target.asmVarname}+$scaledIdx
                                    ldy  #>${target.asmVarname}+$scaledIdx
                                    jsr  c64flt.copy_float
                                """)
                            }
                            else -> throw AssemblyError("weird target variable type ${target.datatype}")
                        }
                    }
                    index is IdentifierReference -> {
                        when(target.datatype) {
                            DataType.UBYTE, DataType.BYTE -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                            }
                            DataType.UWORD, DataType.WORD -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                                asmgen.out("""
                                    lda  $sourceName
                                    sta  ${target.asmVarname},y
                                    lda  $sourceName+1
                                    sta  ${target.asmVarname}+1,y
                                """)
                            }
                            DataType.FLOAT -> {
                                asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.A)
                                asmgen.out("""
                                    ldy  #<$sourceName
                                    sty  P8ZP_SCRATCH_W1
                                    ldy  #>$sourceName
                                    sty  P8ZP_SCRATCH_W1+1
                                    ldy  #>${target.asmVarname}
                                    clc
                                    adc  #<${target.asmVarname}
                                    bcc  +
                                    iny
+                                   jsr  c64flt.copy_float""")
                            }
                            else -> throw AssemblyError("weird dt")
                        }
                    }
                    else -> {
                        asmgen.out("  lda  $sourceName |  sta  P8ESTACK_LO,x |  lda  $sourceName+1 |  sta  P8ESTACK_HI,x |  dex")
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  P8ESTACK_LO,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  $sourceName+1")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  $sourceName+1")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  $sourceName+1")
                    else -> throw AssemblyError("can't load word in a single 8-bit register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #$sourceName
                    sta  P8ESTACK_LO,x
                    lda  #$sourceName+1
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignVariableFloat(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmVarname}
                    lda  $sourceName+1
                    sta  ${target.asmVarname}+1
                    lda  $sourceName+2
                    sta  ${target.asmVarname}+2
                    lda  $sourceName+3
                    sta  ${target.asmVarname}+3
                    lda  $sourceName+4
                    sta  ${target.asmVarname}+4
                """)
            }
            TargetStorageKind.ARRAY -> {
                // TODO optimize this, but the situation doesn't occur very often
//                if(target.constArrayIndexValue!=null) {
//                    TODO("const index ${target.constArrayIndexValue}")
//                } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                    TODO("array[var] ${target.constArrayIndexValue}")
//                }
                val index = target.array!!.arrayspec.index
                asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
                asmgen.translateExpression(index)
                asmgen.out("  lda  #<${target.asmVarname} |  ldy  #>${target.asmVarname} |  jsr  c64flt.pop_float_to_indexed_var")
            }
            TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to mem byte")
            TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
            TargetStorageKind.STACK -> asmgen.out("  lda  #<$sourceName |  ldy  #>$sourceName |  jsr  c64flt.push_float")
        }
    }

    private fun assignVariableByte(target: AsmAssignTarget, variable: IdentifierReference) {
        val sourceName = asmgen.asmVariableName(variable)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("""
                    lda  $sourceName
                    sta  ${target.asmVarname}
                    """)
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val scaledIdx = target.constArrayIndexValue!! * target.datatype.memorySize()
                        asmgen.out(" lda  $sourceName  | sta  ${target.asmVarname}+$scaledIdx")
                    }
                    index is IdentifierReference -> {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array, target.datatype, CpuRegister.Y)
                        asmgen.out(" lda  $sourceName |  sta  ${target.asmVarname},y")
                    }
                    else -> {
                        asmgen.out("  lda  $sourceName |  sta  P8ESTACK_LO,x |  dex")
                        asmgen.translateExpression(index)
                        asmgen.out("  inx |  lda  P8ESTACK_LO,x")
                        popAndWriteArrayvalueWithUnscaledIndexA(target.datatype, target.asmVarname)
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  $sourceName")
                    RegisterOrPair.X -> asmgen.out("  ldx  $sourceName")
                    RegisterOrPair.Y -> asmgen.out("  ldy  $sourceName")
                    RegisterOrPair.AX -> asmgen.out("  lda  $sourceName |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  $sourceName |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName |  ldy  #0")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #$sourceName
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignRegisterByte(target: AsmAssignTarget, register: CpuRegister) {
        require(target.datatype in ByteDatatypes)
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  st${register.name.toLowerCase()}  ${target.asmVarname}")
            }
            TargetStorageKind.MEMORY -> {
                storeRegisterInMemoryAddress(register, target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when (index) {
                    is NumericLiteralValue -> {
                        val memindex = index.number.toInt()
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  ${target.asmVarname}+$memindex")
                            CpuRegister.X -> asmgen.out("  stx  ${target.asmVarname}+$memindex")
                            CpuRegister.Y -> asmgen.out("  sty  ${target.asmVarname}+$memindex")
                        }
                    }
                    is IdentifierReference -> {
                        when (register) {
                            CpuRegister.A -> {}
                            CpuRegister.X -> asmgen.out(" txa")
                            CpuRegister.Y -> asmgen.out(" tya")
                        }
                        asmgen.out(" ldy  ${asmgen.asmVariableName(index)} |  sta  ${target.asmVarname},y")
                    }
                    else -> {
                        asmgen.saveRegister(register)
                        asmgen.translateExpression(index)
                        asmgen.restoreRegister(register)
                        when (register) {
                            CpuRegister.A -> asmgen.out("  sta  P8ZP_SCRATCH_B1")
                            CpuRegister.X -> asmgen.out("  stx  P8ZP_SCRATCH_B1")
                            CpuRegister.Y -> asmgen.out("  sty  P8ZP_SCRATCH_B1")
                        }
                        asmgen.out("""
                            inx
                            lda  P8ESTACK_LO,x
                            tay
                            lda  P8ZP_SCRATCH_B1
                            sta  ${target.asmVarname},y  
                        """)
                    }
                }
            }
            TargetStorageKind.REGISTER -> {
                when(register) {
                    CpuRegister.A -> when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> { asmgen.out("  tax") }
                        RegisterOrPair.Y -> { asmgen.out("  tay") }
                        RegisterOrPair.AY -> { asmgen.out("  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tax |  ldy  #0") }
                    }
                    CpuRegister.X -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  txa") }
                        RegisterOrPair.X -> {  }
                        RegisterOrPair.Y -> { asmgen.out("  txy") }
                        RegisterOrPair.AY -> { asmgen.out("  txa |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  txa |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  ldy  #0") }
                    }
                    CpuRegister.Y -> when(target.register!!) {
                        RegisterOrPair.A -> { asmgen.out("  tya") }
                        RegisterOrPair.X -> { asmgen.out("  tyx") }
                        RegisterOrPair.Y -> { }
                        RegisterOrPair.AY -> { asmgen.out("  tya |  ldy  #0") }
                        RegisterOrPair.AX -> { asmgen.out("  tya |  ldx  #0") }
                        RegisterOrPair.XY -> { asmgen.out("  tya |  tax |  ldy  #0") }
                    }
                }
            }
            TargetStorageKind.STACK -> {
                when(register) {
                    CpuRegister.A -> asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                    CpuRegister.X -> throw AssemblyError("can't use X here")
                    CpuRegister.Y -> asmgen.out(" tya |  sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun assignConstantWord(target: AsmAssignTarget, word: Int) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                if (word ushr 8 == word and 255) {
                    // lsb=msb
                    asmgen.out("""
                    lda  #${(word and 255).toHex()}
                    sta  ${target.asmVarname}
                    sta  ${target.asmVarname}+1
                """)
                } else {
                    asmgen.out("""
                    lda  #<${word.toHex()}
                    ldy  #>${word.toHex()}
                    sta  ${target.asmVarname}
                    sty  ${target.asmVarname}+1
                """)
                }
            }
            TargetStorageKind.MEMORY -> {
                throw AssemblyError("no asm gen for assign word $word to memory ${target.memory}")
            }
            TargetStorageKind.ARRAY -> {
                // TODO optimize this, but the situation doesn't occur very often
//                if(target.constArrayIndexValue!=null) {
//                    TODO("const index ${target.constArrayIndexValue}")
//                } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                    TODO("array[var] ${target.constArrayIndexValue}")
//                }
                val index = target.array!!.arrayspec.index
                asmgen.translateExpression(index)
                asmgen.out("""
                    inx
                    lda  P8ESTACK_LO,x
                    asl  a
                    tay
                    lda  #<${word.toHex()}
                    sta  ${target.asmVarname},y
                    lda  #>${word.toHex()}
                    sta  ${target.asmVarname}+1,y
                """)
            }
            TargetStorageKind.REGISTER -> {
                when(target.register!!) {
                    RegisterOrPair.AX -> asmgen.out("  lda  #<${word.toHex()} |  ldx  #>${word.toHex()}")
                    RegisterOrPair.AY -> asmgen.out("  lda  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    RegisterOrPair.XY -> asmgen.out("  ldx  #<${word.toHex()} |  ldy  #>${word.toHex()}")
                    else -> throw AssemblyError("can't assign word to single byte register")
                }
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #<${word.toHex()}
                    sta  P8ESTACK_LO,x
                    lda  #>${word.toHex()}
                    sta  P8ESTACK_HI,x
                    dex""")
            }
        }
    }

    private fun assignConstantByte(target: AsmAssignTarget, byte: Short) {
        when(target.kind) {
            TargetStorageKind.VARIABLE -> {
                asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname} ")
            }
            TargetStorageKind.MEMORY -> {
                storeByteViaRegisterAInMemoryAddress("#${byte.toHex()}", target.memory!!)
            }
            TargetStorageKind.ARRAY -> {
                val index = target.array!!.arrayspec.index
                when {
                    target.constArrayIndexValue!=null -> {
                        val indexValue = target.constArrayIndexValue!!
                        asmgen.out("  lda  #${byte.toHex()} |  sta  ${target.asmVarname}+$indexValue")
                    }
                    index is IdentifierReference -> {
                        asmgen.loadScaledArrayIndexIntoRegister(target.array, DataType.UBYTE, CpuRegister.Y)
                        asmgen.out("  lda  #<${byte.toHex()} |  sta  ${target.asmVarname},y")
                    }
                    else -> {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            inx
                            ldy  P8ESTACK_LO,x
                            lda  #${byte.toHex()}
                            sta  ${target.asmVarname},y
                        """)
                    }
                }
            }
            TargetStorageKind.REGISTER -> when(target.register!!) {
                RegisterOrPair.A -> asmgen.out("  lda  #${byte.toHex()}")
                RegisterOrPair.X -> asmgen.out("  ldx  #${byte.toHex()}")
                RegisterOrPair.Y -> asmgen.out("  ldy  #${byte.toHex()}")
                RegisterOrPair.AX -> asmgen.out("  lda  #${byte.toHex()} |  ldx  #0")
                RegisterOrPair.AY -> asmgen.out("  lda  #${byte.toHex()} |  ldy  #0")
                RegisterOrPair.XY -> asmgen.out("  ldx  #${byte.toHex()} |  ldy  #0")
            }
            TargetStorageKind.STACK -> {
                asmgen.out("""
                    lda  #${byte.toHex()}
                    sta  P8ESTACK_LO,x
                    dex""")
            }
        }
    }

    private fun assignConstantFloat(target: AsmAssignTarget, float: Double) {
        if (float == 0.0) {
            // optimized case for float zero
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    if(CompilationTarget.instance.machine.cpu == CpuType.CPU65c02)
                        asmgen.out("""
                            stz  ${target.asmVarname}
                            stz  ${target.asmVarname}+1
                            stz  ${target.asmVarname}+2
                            stz  ${target.asmVarname}+3
                            stz  ${target.asmVarname}+4
                        """)
                    else
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}
                            sta  ${target.asmVarname}+1
                            sta  ${target.asmVarname}+2
                            sta  ${target.asmVarname}+3
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    // TODO optimize this, but the situation doesn't occur very often
//                    if(target.constArrayIndexValue!=null) {
//                        TODO("const index ${target.constArrayIndexValue}")
//                    } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                        TODO("array[var] ${target.constArrayIndexValue}")
//                    }
                    val index = target.array!!.arrayspec.index
                    if (index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * DataType.FLOAT.memorySize()
                        asmgen.out("""
                            lda  #0
                            sta  ${target.asmVarname}+$indexValue
                            sta  ${target.asmVarname}+$indexValue+1
                            sta  ${target.asmVarname}+$indexValue+2
                            sta  ${target.asmVarname}+$indexValue+3
                            sta  ${target.asmVarname}+$indexValue+4
                        """)
                    } else {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            lda  #<${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${target.asmVarname}
                            sta  P8ZP_SCRATCH_W1+1
                            jsr  c64flt.set_0_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float")
                }
            }
        } else {
            // non-zero value
            val constFloat = asmgen.getFloatAsmConst(float)
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                            lda  $constFloat
                            sta  ${target.asmVarname}
                            lda  $constFloat+1
                            sta  ${target.asmVarname}+1
                            lda  $constFloat+2
                            sta  ${target.asmVarname}+2
                            lda  $constFloat+3
                            sta  ${target.asmVarname}+3
                            lda  $constFloat+4
                            sta  ${target.asmVarname}+4
                        """)
                }
                TargetStorageKind.ARRAY -> {
                    // TODO optimize this, but the situation doesn't occur very often
//                    if(target.constArrayIndexValue!=null) {
//                        TODO("const index ${target.constArrayIndexValue}")
//                    } else if(target.array!!.arrayspec.index is IdentifierReference) {
//                        TODO("array[var] ${target.constArrayIndexValue}")
//                    }
                    val index = target.array!!.arrayspec.index
                    val arrayVarName = target.asmVarname
                    if (index is NumericLiteralValue) {
                        val indexValue = index.number.toInt() * DataType.FLOAT.memorySize()
                        asmgen.out("""
                            lda  $constFloat
                            sta  $arrayVarName+$indexValue
                            lda  $constFloat+1
                            sta  $arrayVarName+$indexValue+1
                            lda  $constFloat+2
                            sta  $arrayVarName+$indexValue+2
                            lda  $constFloat+3
                            sta  $arrayVarName+$indexValue+3
                            lda  $constFloat+4
                            sta  $arrayVarName+$indexValue+4
                        """)
                    } else {
                        asmgen.translateExpression(index)
                        asmgen.out("""
                            lda  #<${constFloat}
                            sta  P8ZP_SCRATCH_W1
                            lda  #>${constFloat}
                            sta  P8ZP_SCRATCH_W1+1
                            lda  #<${arrayVarName}
                            sta  P8ZP_SCRATCH_W2
                            lda  #>${arrayVarName}
                            sta  P8ZP_SCRATCH_W2+1
                            jsr  c64flt.set_array_float
                        """)
                    }
                }
                TargetStorageKind.MEMORY -> throw AssemblyError("can't assign float to memory byte")
                TargetStorageKind.REGISTER -> throw AssemblyError("can't assign float to register")
                TargetStorageKind.STACK -> {
                    val floatConst = asmgen.getFloatAsmConst(float)
                    asmgen.out(" lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float")
                }
            }
        }
    }

    private fun assignMemoryByte(target: AsmAssignTarget, address: Int?, identifier: IdentifierReference?) {
        if (address != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  ${target.asmVarname}
                        """)
                }
                TargetStorageKind.MEMORY -> {
                    storeByteViaRegisterAInMemoryAddress(address.toHex(), target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte at $address to array ${target.asmVarname}")
                }
                TargetStorageKind.REGISTER -> when(target.register!!) {
                    RegisterOrPair.A -> asmgen.out("  lda  ${address.toHex()}")
                    RegisterOrPair.X -> asmgen.out("  ldx  ${address.toHex()}")
                    RegisterOrPair.Y -> asmgen.out("  ldy  ${address.toHex()}")
                    RegisterOrPair.AX -> asmgen.out("  lda  ${address.toHex()} |  ldx  #0")
                    RegisterOrPair.AY -> asmgen.out("  lda  ${address.toHex()} |  ldy  #0")
                    RegisterOrPair.XY -> asmgen.out("  ldy  ${address.toHex()} |  ldy  #0")
                }
                TargetStorageKind.STACK -> {
                    asmgen.out("""
                        lda  ${address.toHex()}
                        sta  P8ESTACK_LO,x
                        dex""")
                }
            }
        } else if (identifier != null) {
            when(target.kind) {
                TargetStorageKind.VARIABLE -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  ${target.asmVarname}")
                }
                TargetStorageKind.MEMORY -> {
                    val sourceName = asmgen.asmVariableName(identifier)
                    storeByteViaRegisterAInMemoryAddress(sourceName, target.memory!!)
                }
                TargetStorageKind.ARRAY -> {
                    throw AssemblyError("no asm gen for assign memory byte $identifier to array ${target.asmVarname} ")
                }
                TargetStorageKind.REGISTER -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    when(target.register!!) {
                        RegisterOrPair.A -> {}
                        RegisterOrPair.X -> asmgen.out("  tax")
                        RegisterOrPair.Y -> asmgen.out("  tay")
                        RegisterOrPair.AX -> asmgen.out("  ldx  #0")
                        RegisterOrPair.AY -> asmgen.out("  ldy  #0")
                        RegisterOrPair.XY -> asmgen.out("  tax |  ldy  #0")
                    }
                }
                TargetStorageKind.STACK -> {
                    asmgen.loadByteFromPointerIntoA(identifier)
                    asmgen.out(" sta  P8ESTACK_LO,x |  dex")
                }
            }
        }
    }

    private fun storeByteViaRegisterAInMemoryAddress(ldaInstructionArg: String, memoryAddress: DirectMemoryWrite) {
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        when {
            addressLv != null -> {
                asmgen.out("  lda $ldaInstructionArg |  sta  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                asmgen.storeByteIntoPointer(addressExpr, ldaInstructionArg)
            }
            else -> {
                asmgen.translateExpression(addressExpr)
                asmgen.out("""
                    inx
                    lda  P8ESTACK_LO,x
                    sta  P8ZP_SCRATCH_W2
                    lda  P8ESTACK_HI,x
                    sta  P8ZP_SCRATCH_W2+1
                    lda  $ldaInstructionArg
                    ldy  #0
                    sta  (P8ZP_SCRATCH_W2),y""")
            }
        }
    }

    private fun storeRegisterInMemoryAddress(register: CpuRegister, memoryAddress: DirectMemoryWrite) {
        // this is optimized for register A.
        val addressExpr = memoryAddress.addressExpression
        val addressLv = addressExpr as? NumericLiteralValue
        val registerName = register.name.toLowerCase()
        when {
            addressLv != null -> {
                asmgen.out("  st$registerName  ${addressLv.number.toHex()}")
            }
            addressExpr is IdentifierReference -> {
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> asmgen.out(" txa")
                    CpuRegister.Y -> asmgen.out(" tya")
                }
                asmgen.storeByteIntoPointer(addressExpr, null)
            }
            else -> {
                asmgen.saveRegister(register)
                asmgen.translateExpression(addressExpr)
                asmgen.restoreRegister(CpuRegister.A)
                asmgen.out("""
                    inx
                    ldy  P8ESTACK_LO,x
                    sty  P8ZP_SCRATCH_W2
                    ldy  P8ESTACK_HI,x
                    sty  P8ZP_SCRATCH_W2+1
                    ldy  #0
                    sta  (P8ZP_SCRATCH_W2),y""")
            }
        }
    }

    private fun popAndWriteArrayvalueWithUnscaledIndexA(elementDt: DataType, asmArrayvarname: String) {
        when (elementDt) {
            in ByteDatatypes ->
                asmgen.out("  tay |  inx |  lda  P8ESTACK_LO,x  | sta  $asmArrayvarname,y")
            in WordDatatypes ->
                asmgen.out("  asl  a |  tay |  inx |  lda  P8ESTACK_LO,x |  sta  $asmArrayvarname,y |  lda  P8ESTACK_HI,x |  sta $asmArrayvarname+1,y")
            DataType.FLOAT ->
                // scaling * 5 is done in the subroutine that's called
                asmgen.out("""
                    sta  P8ESTACK_LO,x
                    dex
                    lda  #<$asmArrayvarname
                    ldy  #>$asmArrayvarname
                    jsr  c64flt.pop_float_to_indexed_var
                """)
            else ->
                throw AssemblyError("weird array type")
        }
    }
}
