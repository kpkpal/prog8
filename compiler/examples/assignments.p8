; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

sub start() {

    byte b = 99
    byte b2 = 100
    ubyte ub = 255
    ubyte ub2 = 0
    word w = 999
    word w2 = 3
    uword uw = 40000
    uword uw2 = 3434
    float fl1 = 1.1
    float fl2 = 2.2

    byte[5] barr1
    byte[5] barr2
    ubyte[5] ubarr1
    ubyte[5] ubarr2
    word[5] warr1
    word[5] warr2
    uword[5] uwarr1
    uword[5] uwarr2
    float[5] farr1
    float[5] farr2

    memory byte mbyte = $c000
    memory byte mbyte2 = $d000
    memory ubyte mubyte = $c001
    memory ubyte mubyte2 = $d001
    memory word mword = $c002
    memory word mword2 = $d002
    memory uword muword = $c004
    memory uword muword2 = $d004
    memory float mfloat = $c006
    memory float mfloat2 = $d006
    memory byte[mubyte2] mbarr1 = $e000
    memory ubyte[mubyte2] mubarr1 = $e100
    memory word[mubyte2] mwarr1 = $e100
    memory uword[mubyte2] muwarr1 = $e100

    str  string = "hello"
    str_p stringp = "hello"


; all possible assignments to a BYTE VARIABLE (not array)


ubyte_assignment_to_register:
    A = 42
    A = X
    A = ub2
    A = mubyte2
    A = string[4]
    A = string[X]
    A = string[b]
    A = string[ub]
    A = string[mbyte2]
    A = string[mubyte2]
    A = ubarr1[2]
    A = ubarr1[X]
    A = ubarr1[b]
    A = ubarr1[ub]
    A = ubarr1[mbyte2]
    A = ubarr1[mubyte2]

ubyte_assignment_to_ubytevar:
    ub = 42
    ub = X
    ub = ub2
    ub = mubyte2
    ub = string[4]
    ub = string[X]
    ub = string[b]
    ub = string[ub]
    ub = string[mbyte2]
    ub = string[mubyte2]
    ub = ubarr1[2]
    ub = ubarr1[X]
    ub = ubarr1[b]
    ub = ubarr1[ub]
    ub = ubarr1[mbyte2]
    ub = ubarr1[mubyte2]


ubyte_assignment_to_ubytemem:
    mubyte = 42
    mubyte = X
    mubyte = ub2
    mubyte = mubyte2
    mubyte = string[4]
    mubyte = string[X]
    mubyte = string[b]
    mubyte = string[ub]
    mubyte = string[mbyte2]
    mubyte = string[mubyte2]
    mubyte = ubarr1[2]
    mubyte = ubarr1[X]
    mubyte = ubarr1[b]
    mubyte = ubarr1[ub]
    mubyte = ubarr1[mbyte2]
    mubyte = ubarr1[mubyte2]

byte_assignment_to_bytevar:
    b = -42
    b = b2
    b = mbyte2
    b = barr1[2]
    b = barr1[X]
    b = barr1[b]
    b = barr1[ub]
    b = barr1[mbyte2]
    b = barr1[mubyte2]


byte_assignment_to_bytemem:
    mbyte = -42
    mbyte = b2
    mbyte = mbyte2
    mbyte = barr1[2]
    mbyte = barr1[X]
    mbyte = barr1[b]
    mbyte = barr1[ub]
    mbyte = barr1[mbyte2]
    mbyte = barr1[mubyte2]


ubyte_assignment_to_ubytearray:
    ubarr2[3] = 42
    ubarr2[3] = X
    ubarr2[3] = ub2
    ubarr2[3] = mubyte2
    ubarr2[3] = string[4]
    ubarr2[3] = string[X]
    ubarr2[3] = string[b]
    ubarr2[3] = string[ub]
    ubarr2[3] = string[mbyte2]
    ubarr2[3] = string[mubyte2]
    ubarr2[3] = ubarr1[2]
    ubarr2[3] = ubarr1[X]
    ubarr2[3] = ubarr1[b]
    ubarr2[3] = ubarr1[ub]
    ubarr2[3] = ubarr1[mbyte2]
    ubarr2[3] = ubarr1[mubyte2]
    string[4] = 42
    string[4] = 'B'
    string[4] = X
    string[4] = ub2
    string[4] = mubyte2
    string[4] = ubarr1[2]
    string[4] = ubarr1[X]
    string[4] = ubarr1[ub]
    string[4] = ubarr1[mubyte2]
    string[4] = string[3]
    string[4] = string[Y]
    string[4] = string[ub2]
    string[4] = string[mbyte2]
    string[4] = string[mubyte2]


    ubarr2[Y] = 42
    ubarr2[Y] = X
    ubarr2[Y] = ub2
    ubarr2[Y] = mubyte2
    ubarr2[Y] = string[4]
    ubarr2[Y] = string[X]
    ubarr2[Y] = string[b]
    ubarr2[Y] = string[ub]
    ubarr2[Y] = string[mbyte2]
    ubarr2[Y] = string[mubyte2]
    ubarr2[Y] = ubarr1[2]
    ubarr2[Y] = ubarr1[X]
    ubarr2[Y] = ubarr1[b]
    ubarr2[Y] = ubarr1[ub]
    ubarr2[Y] = ubarr1[mbyte2]
    ubarr2[Y] = ubarr1[mubyte2]
    string[Y] = 42
    string[Y] = 'B'
    string[Y] = X
    string[Y] = ub2
    string[Y] = mubyte2
    string[Y] = ubarr1[2]
    string[Y] = ubarr1[Y]
    string[Y] = ubarr1[ub2]
    string[Y] = ubarr1[mubyte2]
    string[Y] = string[Y]
    string[Y] = string[ub2]
    string[Y] = string[mbyte2]
    string[Y] = string[mubyte2]


        ubarr2[ub2] = 42
        ubarr2[ub2] = X
        ubarr2[ub2] = ub2
        ubarr2[ub2] = mubyte2
        ubarr2[ub2] = string[4]
        ubarr2[ub2] = string[X]
        ubarr2[ub2] = string[b]
        ubarr2[ub2] = string[ub]
        ubarr2[ub2] = string[mbyte2]
        ubarr2[ub2] = string[mubyte2]
        ubarr2[ub2] = ubarr1[2]
        ubarr2[ub2] = ubarr1[X]
        ubarr2[ub2] = ubarr1[b]
        ubarr2[ub2] = ubarr1[ub]
        ubarr2[ub2] = ubarr1[mbyte2]
        ubarr2[ub2] = ubarr1[mubyte2]
        string[ub2] = 42
        string[ub2] = 'B'
        string[ub2] = X
        string[ub2] = ub2
        string[ub2] = mubyte2
        string[ub2] = ubarr1[2]
        string[ub2] = ubarr1[Y]
        string[ub2] = ubarr1[ub2]
        string[ub2] = ubarr1[mubyte2]
        string[ub2] = string[Y]
        string[ub2] = string[ub2]
        string[ub2] = string[mbyte2]
        string[ub2] = string[mubyte2]


        ubarr2[b2] = 42
        ubarr2[b2] = X
        ubarr2[b2] = ub2
        ubarr2[b2] = mubyte2
        ubarr2[b2] = string[4]
        ubarr2[b2] = string[X]
        ubarr2[b2] = string[b]
        ubarr2[b2] = string[ub]
        ubarr2[b2] = string[mbyte2]
        ubarr2[b2] = string[mubyte2]
        ubarr2[b2] = ubarr1[2]
        ubarr2[b2] = ubarr1[X]
        ubarr2[b2] = ubarr1[b]
        ubarr2[b2] = ubarr1[ub]
        ubarr2[b2] = ubarr1[mbyte2]
        ubarr2[b2] = ubarr1[mubyte2]
        string[b2] = 42
        string[b2] = 'B'
        string[b2] = X
        string[b2] = ub2
        string[b2] = mubyte2
        string[b2] = ubarr1[2]
        string[b2] = ubarr1[Y]
        string[b2] = ubarr1[ub2]
        string[b2] = ubarr1[mubyte2]
        string[b2] = string[Y]
        string[b2] = string[ub2]
        string[b2] = string[mbyte2]
        string[b2] = string[mubyte2]

    ubarr2[mubyte2] = 42
    ubarr2[mubyte2] = X
    ubarr2[mubyte2] = ub2
    ubarr2[mubyte2] = mubyte2
    ubarr2[mubyte2] = string[4]
    ubarr2[mubyte2] = ubarr1[2]
    string[mubyte2] = 42
    string[mubyte2] = 'B'
    string[mubyte2] = X
    string[mubyte2] = ub2
    string[mubyte2] = mubyte2
    string[mubyte2] = ubarr1[2]

    ubarr2[mubyte2] = string[Y]             ; via evaluation
    ubarr2[mubyte2] = string[b]             ; via evaluation
    ubarr2[mubyte2] = string[ub]            ; via evaluation
    ubarr2[mubyte2] = string[mbyte2]        ; via evaluation
    ubarr2[mubyte2] = string[mubyte2]       ; via evaluation
    ubarr2[mubyte2] = ubarr1[Y]             ; via evaluation
    ubarr2[mubyte2] = ubarr1[b]             ; via evaluation
    ubarr2[mubyte2] = ubarr1[ub]            ; via evaluation
    ubarr2[mubyte2] = ubarr1[mbyte2]        ; via evaluation
    ubarr2[mubyte2] = ubarr1[mubyte2]       ; via evaluation
    string[mubyte2] = ubarr1[Y]             ; via evaluation
    string[mubyte2] = ubarr1[b]             ; via evaluation
    string[mubyte2] = ubarr1[ub2]           ; via evaluation
    string[mubyte2] = ubarr1[mbyte2]        ; via evaluation
    string[mubyte2] = ubarr1[mubyte2]       ; via evaluation
    string[mubyte2] = string[mubyte2]       ; via evaluation
    ubarr1[ubarr2[A]] = ubarr2[ubarr1[Y]]   ; via evaluation-- todo check generated asm...



byte_assignment_to_bytearray:

    barr2[3] = 42
    barr2[3] = b2
    barr2[3] = mbyte2
    barr2[3] = barr1[2]
    barr2[3] = barr1[X]
    barr2[3] = barr1[b]
    barr2[3] = barr1[ub]
    barr2[3] = barr1[mbyte2]
    barr2[3] = barr1[mubyte2]

    barr2[Y] = 42
    barr2[Y] = b2
    barr2[Y] = mbyte2
    barr2[Y] = barr1[2]
    barr2[Y] = barr1[X]
    barr2[Y] = barr1[b]
    barr2[Y] = barr1[ub]
    barr2[Y] = barr1[mbyte2]
    barr2[Y] = barr1[mubyte2]

        barr2[b2] = 42
        barr2[b2] = b2
        barr2[b2] = mbyte2
        barr2[b2] = barr1[2]
        barr2[b2] = barr1[X]
        barr2[b2] = barr1[b]
        barr2[b2] = barr1[ub]
        barr2[b2] = barr1[mbyte2]
        barr2[b2] = barr1[mubyte2]

        barr2[ub2] = 42
        barr2[ub2] = b2
        barr2[ub2] = mbyte2
        barr2[ub2] = barr1[2]
        barr2[ub2] = barr1[X]
        barr2[ub2] = barr1[b]
        barr2[ub2] = barr1[ub]
        barr2[ub2] = barr1[mbyte2]
        barr2[ub2] = barr1[mubyte2]

    barr2[mubyte2] = 42
    barr2[mubyte2] = b2
    barr2[mubyte2] = mbyte2
    barr2[mubyte2] = barr1[2]
    barr2[mubyte2] = barr1[Y]         ; via evaluation
    barr2[mubyte2] = barr1[b]         ; via evaluation
    barr2[mubyte2] = barr1[ub]        ; via evaluation
    barr2[mubyte2] = barr1[mbyte2]    ; via evaluation
    barr2[mubyte2] = barr1[mubyte2]   ; via evaluation
    barr1[ubarr2[A]] = barr2[ubarr1[Y]]   ; via evaluation-- todo check generated asm...


byte_assignment_to_membytearray:

    mbarr1[3] = 42
    mbarr1[3] = b2
    mbarr1[3] = mbyte2
    mbarr1[3] = barr1[2]
    mbarr1[3] = barr1[X]
    mbarr1[3] = barr1[b]
    mbarr1[3] = barr1[ub]
    mbarr1[3] = barr1[mbyte2]
    mbarr1[3] = barr1[mubyte2]

    mbarr1[Y] = 42
    mbarr1[Y] = b2
    mbarr1[Y] = mbyte2
    mbarr1[Y] = barr1[2]
    mbarr1[Y] = barr1[X]
    mbarr1[Y] = barr1[b]
    mbarr1[Y] = barr1[ub]
    mbarr1[Y] = barr1[mbyte2]
    mbarr1[Y] = barr1[mubyte2]

        mbarr1[b2] = 42
        mbarr1[b2] = b2
        mbarr1[b2] = mbyte2
        mbarr1[b2] = barr1[2]
        mbarr1[b2] = barr1[X]
        mbarr1[b2] = barr1[b]
        mbarr1[b2] = barr1[ub]
        mbarr1[b2] = barr1[mbyte2]
        mbarr1[b2] = barr1[mubyte2]

        mbarr1[ub2] = 42
        mbarr1[ub2] = b2
        mbarr1[ub2] = mbyte2
        mbarr1[ub2] = barr1[2]
        mbarr1[ub2] = barr1[X]
        mbarr1[ub2] = barr1[b]
        mbarr1[ub2] = barr1[ub]
        mbarr1[ub2] = barr1[mbyte2]
        mbarr1[ub2] = barr1[mubyte2]

    mbarr1[mubyte2] = 42
    mbarr1[mubyte2] = b2
    mbarr1[mubyte2] = mbyte2
    mbarr1[mubyte2] = barr1[2]
    mbarr1[mubyte2] = barr1[Y]         ; via evaluation
    mbarr1[mubyte2] = barr1[b]         ; via evaluation
    mbarr1[mubyte2] = barr1[ub]        ; via evaluation
    mbarr1[mubyte2] = barr1[mbyte2]    ; via evaluation
    mbarr1[mubyte2] = barr1[mubyte2]   ; via evaluation
    mbarr1[ubarr2[A]] = barr2[ubarr1[Y]]   ; via evaluation-- todo check generated asm...



; all possible assignments to a UWORD VARIABLE (not array)


uword_assignment_to_uwordvar:
    uw = 42
    uw = 42.w
    uw = 42555
    uw = X
    uw = ub2
    uw = uw2
    uw = mubyte2
    uw = muword2
    uw = string[4]
    uw = ubarr1[2]
    uw = uwarr1[2]
    uw = string[X]
    uw = string[b]
    uw = string[ub]
    uw = string[mbyte2]
    uw = string[mubyte2]
    uw = ubarr1[X]
    uw = ubarr1[b]
    uw = ubarr1[ub]
    uw = ubarr1[mbyte2]
    uw = ubarr1[mubyte2]
    uw = uwarr1[X]
    uw = uwarr1[b]
    uw = uwarr1[ub]
    uw = uwarr1[mbyte2]
    uw = uwarr1[mubyte2]


uword_assignment_to_uwordmem:
    muword = 42
    muword = 42.w
    muword = 42555
    muword = X
    muword = ub2
    muword = uw2
    muword = mubyte2
    muword = muword2
    muword = string[4]
    muword = ubarr1[2]
    muword = uwarr1[2]
    muword = string[X]
    muword = string[b]
    muword = string[ub]
    muword = string[mbyte2]
    muword = string[mubyte2]
    muword = ubarr1[X]
    muword = ubarr1[b]
    muword = ubarr1[ub]
    muword = ubarr1[mbyte2]
    muword = ubarr1[mubyte2]
    muword = uwarr1[X]
    muword = uwarr1[b]
    muword = uwarr1[ub]
    muword = uwarr1[mbyte2]
    muword = uwarr1[mubyte2]


uword_assignment_to_uwordarray:
    uwarr1[2] = 42
    uwarr1[2] = 42.w
    uwarr1[2] = 42555
    uwarr1[2] = X
    uwarr1[2] = ub2
    uwarr1[2] = uw2
    uwarr1[2] = mubyte2
    uwarr1[2] = muword2
    uwarr1[2] = string[4]
    uwarr1[2] = ubarr1[2]
    uwarr1[2] = uwarr1[2]

        uwarr1[Y] = 42
        uwarr1[Y] = 42.w
        uwarr1[Y] = 42555
        uwarr1[Y] = X
        uwarr1[Y] = ub2
        uwarr1[Y] = uw2
        uwarr1[Y] = mubyte2
        uwarr1[Y] = muword2
        uwarr1[Y] = string[4]
        uwarr1[Y] = ubarr1[2]
        uwarr1[Y] = uwarr1[2]

    uwarr1[b] = 42
    uwarr1[b] = 42.w
    uwarr1[b] = 42555
    uwarr1[b] = X
    uwarr1[b] = ub2
    uwarr1[b] = uw2
    uwarr1[b] = mubyte2
    uwarr1[b] = muword2
    uwarr1[b] = string[4]
    uwarr1[b] = ubarr1[2]
    uwarr1[b] = uwarr1[2]

    uwarr1[ub] = 42
    uwarr1[ub] = 42.w
    uwarr1[ub] = 42555
    uwarr1[ub] = X
    uwarr1[ub] = ub2
    uwarr1[ub] = uw2
    uwarr1[ub] = mubyte2
    uwarr1[ub] = muword2
    uwarr1[ub] = string[4]
    uwarr1[ub] = ubarr1[2]
    uwarr1[ub] = uwarr1[2]

        uwarr1[mbyte2] = 42
        uwarr1[mbyte2] = 42.w
        uwarr1[mbyte2] = 42555
        uwarr1[mbyte2] = X
        uwarr1[mbyte2] = ub2
        uwarr1[mbyte2] = uw2
        uwarr1[mbyte2] = mubyte2
        uwarr1[mbyte2] = muword2
        uwarr1[mbyte2] = string[4]
        uwarr1[mbyte2] = ubarr1[2]
        uwarr1[mbyte2] = uwarr1[2]

        uwarr1[mubyte2] = 42
        uwarr1[mubyte2] = 42.w
        uwarr1[mubyte2] = 42555
        uwarr1[mubyte2] = X
        uwarr1[mubyte2] = ub2
        uwarr1[mubyte2] = uw2
        uwarr1[mubyte2] = mubyte2
        uwarr1[mubyte2] = muword2
        uwarr1[mubyte2] = string[4]
        uwarr1[mubyte2] = ubarr1[2]
        uwarr1[mubyte2] = uwarr1[2]


   uwarr1[2] = string[Y]         ; via evaluation
   uwarr1[2] = string[b]         ; via evaluation
   uwarr1[2] = string[ub]        ; via evaluation
   uwarr1[2] = string[mbyte2]    ; via evaluation
   uwarr1[2] = string[mubyte2]   ; via evaluation
   uwarr1[2] = ubarr1[Y]         ; via evaluation
   uwarr1[2] = ubarr1[b]         ; via evaluation
   uwarr1[2] = ubarr1[ub]        ; via evaluation
   uwarr1[2] = ubarr1[mbyte2]    ; via evaluation
   uwarr1[2] = ubarr1[mubyte2]   ; via evaluation
   uwarr1[2] = uwarr1[Y]         ; via evaluation
   uwarr1[2] = uwarr1[b]         ; via evaluation
   uwarr1[2] = uwarr1[ub]        ; via evaluation
   uwarr1[2] = uwarr1[mbyte2]    ; via evaluation
   uwarr1[2] = uwarr1[mubyte2]   ; via evaluation


word_assignment_to_wordvar:
    w = -42
    w = -42.w
    w = 12555
    w = X
    w = ub2
    w = b2
    w = w2
    w = mubyte2
    w = mbyte2
    w = mword2
    w = string[4]
    w = ubarr1[2]
    w = barr1[2]
    w = warr1[2]

    w = string[X]
    w = string[b]
    w = string[ub]
    w = string[mbyte2]
    w = string[mubyte2]


    w = barr1[X]
    w = ubarr1[X]
    w = barr1[b]
    w = ubarr1[b]
    w = barr1[ub]
    w = ubarr1[ub]
    w = barr1[mbyte2]
    w = ubarr1[mbyte2]
    w = barr1[mubyte2]
    w = ubarr1[mubyte2]
    w = warr1[X]
    w = warr1[b]
    w = warr1[ub]
    w = warr1[mbyte2]
    w = warr1[mubyte2]


word_assignment_to_wordmem:
    mword = -42
    mword = -42.w
    mword = 12555
    mword = X
    mword = ub2
    mword = b2
    mword = w2
    mword = mubyte2
    mword = mbyte2
    mword = mword2
    mword = string[4]
    mword = ubarr1[2]
    mword = barr1[2]
    mword = warr1[2]


    mword = string[X]
    mword = string[b]
    mword = string[ub]
    mword = string[mbyte2]
    mword = string[mubyte2]
    mword = barr1[X]
    mword = ubarr1[X]
    mword = barr1[b]
    mword = ubarr1[b]
    mword = barr1[ub]
    mword = ubarr1[ub]
    mword = barr1[mbyte2]
    mword = ubarr1[mbyte2]
    mword = barr1[mubyte2]
    mword = ubarr1[mubyte2]
    mword = warr1[X]
    mword = warr1[b]
    mword = warr1[ub]
    mword = warr1[mbyte2]
    mword = warr1[mubyte2]


word_assignment_to_wordarray:
    warr2[2] = -42
    warr2[2] = -42.w
    warr2[2] = 12555
    warr2[2] = X
    warr2[2] = ub2
    warr2[2] = b2
    warr2[2] = w2
    warr2[2] = mubyte2
    warr2[2] = mbyte2
    warr2[2] = mword2
    warr2[2] = string[4]
    warr2[2] = ubarr1[2]
    warr2[2] = barr1[2]
    warr2[2] = warr1[2]

    warr2[Y] = -42
    warr2[Y] = -42.w
    warr2[Y] = 12555
    warr2[Y] = X
    warr2[Y] = ub2
    warr2[Y] = b2
    warr2[Y] = w2
    warr2[Y] = mubyte2
    warr2[Y] = mbyte2
    warr2[Y] = mword2
    warr2[Y] = string[4]
    warr2[Y] = ubarr1[2]
    warr2[Y] = barr1[2]
    warr2[Y] = warr1[2]

    warr2[b] = -42
    warr2[b] = -42.w
    warr2[b] = 12555
    warr2[b] = X
    warr2[b] = ub2
    warr2[b] = b2
    warr2[b] = w2
    warr2[b] = mubyte2
    warr2[b] = mbyte2
    warr2[b] = mword2
    warr2[b] = string[4]
    warr2[b] = ubarr1[2]
    warr2[b] = barr1[2]
    warr2[b] = warr1[2]

    warr2[ub] = -42
    warr2[ub] = -42.w
    warr2[ub] = 14255
    warr2[ub] = X
    warr2[ub] = ub2
    warr2[ub] = b2
    warr2[ub] = w2
    warr2[ub] = mubyte2
    warr2[ub] = mbyte2
    warr2[ub] = mword2
    warr2[ub] = string[4]
    warr2[ub] = ubarr1[2]
    warr2[ub] = barr1[2]
    warr2[ub] = warr1[2]

    warr2[mbyte2] = -42
    warr2[mbyte2] = -42.w
    warr2[mbyte2] = 12555
    warr2[mbyte2] = X
    warr2[mbyte2] = ub2
    warr2[mbyte2] = b2
    warr2[mbyte2] = w2
    warr2[mbyte2] = mubyte2
    warr2[mbyte2] = mbyte2
    warr2[mbyte2] = mword2
    warr2[mbyte2] = string[4]
    warr2[mbyte2] = ubarr1[2]
    warr2[mbyte2] = barr1[2]
    warr2[mbyte2] = warr1[2]

    warr2[mubyte2] = -42
    warr2[mubyte2] = -42.w
    warr2[mubyte2] = 12555
    warr2[mubyte2] = X
    warr2[mubyte2] = ub2
    warr2[mubyte2] = b2
    warr2[mubyte2] = w2
    warr2[mubyte2] = mubyte2
    warr2[mubyte2] = mbyte2
    warr2[mubyte2] = mword2
    warr2[mubyte2] = string[4]
    warr2[mubyte2] = ubarr1[2]
    warr2[mubyte2] = barr1[2]
    warr2[mubyte2] = warr1[2]


   warr1[2] = string[Y]          ; via evaluation
   warr1[2] = string[b]          ; via evaluation
   warr1[2] = string[ub]         ; via evaluation
   warr1[2] = string[mbyte2]     ; via evaluation
   warr1[2] = string[mubyte2]    ; via evaluation
   warr1[2] = barr1[Y]           ; via evaluation
   warr1[2] = barr1[b]           ; via evaluation
   warr1[2] = barr1[ub]          ; via evaluation
   warr1[2] = barr1[mbyte2]      ; via evaluation
   warr1[2] = barr1[mubyte2]     ; via evaluation
   warr1[2] = warr1[Y]           ; via evaluation
   warr1[2] = warr1[b]           ; via evaluation
   warr1[2] = warr1[ub]          ; via evaluation
   warr1[2] = warr1[mbyte2]      ; via evaluation
   warr1[2] = warr1[mubyte2]     ; via evaluation


;; all possible assignments to a FLOAT VARIABLE
float_assignment_to_floatvar:
    fl1 = 34
    fl1 = 34555.w
    fl1 = 3.33e22
    fl1 = X
    fl1 = b2
    fl1 = ub2
    fl1 = w2
    fl1 = uw2
    fl1 = mbyte
    fl1 = mubyte
    fl1 = mword
    fl1 = muword
    fl1 = fl2
    fl1 = mfloat2
    fl1 = barr1[2]
    fl1 = ubarr1[2]
    fl1 = warr1[2]
    fl1 = uwarr1[2]
    fl1 = string[4]
    fl1 = farr1[4]

    fl1 = string[Y]            ; via evaluation
    fl1 = string[b]            ; via evaluation
    fl1 = string[ub]           ; via evaluation
    fl1 = string[mbyte2]       ; via evaluation
    fl1 = string[mubyte2]      ; via evaluation
    fl1 = barr1[Y]             ; via evaluation
    fl1 = ubarr1[Y]            ; via evaluation
    fl1 = barr1[b]             ; via evaluation
    fl1 = ubarr1[b]            ; via evaluation
    fl1 = barr1[ub]            ; via evaluation
    fl1 = ubarr1[ub]           ; via evaluation
    fl1 = barr1[mbyte2]        ; via evaluation
    fl1 = ubarr1[mbyte2]       ; via evaluation
    fl1 = barr1[mubyte2]       ; via evaluation
    fl1 = ubarr1[mubyte2]      ; via evaluation
    fl1 = warr1[Y]             ; via evaluation
    fl1 = warr1[b]             ; via evaluation
    fl1 = warr1[ub]            ; via evaluation
    fl1 = warr1[mbyte2]        ; via evaluation
    fl1 = warr1[mubyte2]       ; via evaluation


float_assignment_to_floatmem:
    mfloat = 34
    mfloat = 34555.w
    mfloat = 3.33e22
    mfloat = X
    mfloat = b2
    mfloat = ub2
    mfloat = w2
    mfloat = uw2
    mfloat = mbyte
    mfloat = mubyte
    mfloat = mword
    mfloat = muword
    mfloat = fl2
    mfloat = mfloat2
    mfloat = barr1[2]
    mfloat = ubarr1[2]
    mfloat = warr1[2]
    mfloat = uwarr1[2]
    mfloat = string[4]
    mfloat = farr1[4]

    mfloat = string[Y]            ; via evaluation
    mfloat = string[b]            ; via evaluation
    mfloat = string[ub]           ; via evaluation
    mfloat = string[mbyte2]       ; via evaluation
    mfloat = string[mubyte2]      ; via evaluation
    mfloat = barr1[Y]             ; via evaluation
    mfloat = ubarr1[Y]            ; via evaluation
    mfloat = barr1[b]             ; via evaluation
    mfloat = ubarr1[b]            ; via evaluation
    mfloat = barr1[ub]            ; via evaluation
    mfloat = ubarr1[ub]           ; via evaluation
    mfloat = barr1[mbyte2]        ; via evaluation
    mfloat = ubarr1[mbyte2]       ; via evaluation
    mfloat = barr1[mubyte2]       ; via evaluation
    mfloat = ubarr1[mubyte2]      ; via evaluation
    mfloat = warr1[Y]             ; via evaluation
    mfloat = warr1[b]             ; via evaluation
    mfloat = warr1[ub]            ; via evaluation
    mfloat = warr1[mbyte2]        ; via evaluation
    mfloat = warr1[mubyte2]       ; via evaluation



    return
}

}