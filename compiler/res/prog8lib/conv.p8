; Prog8 definitions for number conversions routines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8


conv {

; ----- number conversions to decimal strings

asmsub  ubyte2decimal  (ubyte value @ A) -> ubyte @ Y, ubyte @ A, ubyte @ X  {
	; ---- A to decimal string in Y/A/X  (100s in Y, 10s in A, 1s in X)
    %asm {{
        ldy  #uword2decimal.ASCII_0_OFFSET
        bne  uword2decimal.hex_try200
        rts
	}}
}

asmsub  uword2decimal  (uword value @ AY) -> ubyte @Y, ubyte @A, ubyte @X  {
    ;  ---- convert 16 bit uword in A/Y to decimal
    ;  output in uword2decimal.decTenThousands, decThousands, decHundreds, decTens, decOnes
    ;  (these are terminated by a zero byte so they can be easily printed)
    ;  also returns Y = 100's, A = 10's, X = 1's

    %asm {{

;Convert 16 bit Hex to Decimal (0-65535) Rev 2
;By Omegamatrix    Further optimizations by tepples
; routine from http://forums.nesdev.com/viewtopic.php?f=2&t=11341&start=15

;HexToDec99
; start in A
; end with A = 10's, decOnes (also in X)

;HexToDec255
; start in A
; end with Y = 100's, A = 10's, decOnes (also in X)

;HexToDec999
; start with A = high byte, Y = low byte
; end with Y = 100's, A = 10's, decOnes (also in X)
; requires 1 extra temp register on top of decOnes, could combine
; these two if HexToDec65535 was eliminated...

;HexToDec65535
; start with A/Y (low/high) as 16 bit value
; end with decTenThousand, decThousand, Y = 100's, A = 10's, decOnes (also in X)
; (irmen: I store Y and A in decHundreds and decTens too, so all of it can be easily printed)


ASCII_0_OFFSET 	= $30
temp       	    = P8ZP_SCRATCH_B1	; byte in zeropage
hexHigh      	= P8ZP_SCRATCH_W1	; byte in zeropage
hexLow       	= P8ZP_SCRATCH_W1+1	; byte in zeropage


HexToDec65535; SUBROUTINE
    sty    hexHigh               ;3  @9
    sta    hexLow                ;3  @12
    tya
    tax                          ;2  @14
    lsr    a                     ;2  @16
    lsr    a                     ;2  @18   integer divide 1024 (result 0-63)

    cpx    #$A7                  ;2  @20   account for overflow of multiplying 24 from 43,000 ($A7F8) onward,
    adc    #1                    ;2  @22   we can just round it to $A700, and the divide by 1024 is fine...

    ;at this point we have a number 1-65 that we have to times by 24,
    ;add to original sum, and Mod 1024 to get a remainder 0-999


    sta    temp                  ;3  @25
    asl    a                     ;2  @27
    adc    temp                  ;3  @30  x3
    tay                          ;2  @32
    lsr    a                     ;2  @34
    lsr    a                     ;2  @36
    lsr    a                     ;2  @38
    lsr    a                     ;2  @40
    lsr    a                     ;2  @42
    tax                          ;2  @44
    tya                          ;2  @46
    asl    a                     ;2  @48
    asl    a                     ;2  @50
    asl    a                     ;2  @52
    clc                          ;2  @54
    adc    hexLow                ;3  @57
    sta    hexLow                ;3  @60
    txa                          ;2  @62
    adc    hexHigh               ;3  @65
    sta    hexHigh               ;3  @68
    ror    a                     ;2  @70
    lsr    a                     ;2  @72
    tay                          ;2  @74    integer divide 1,000 (result 0-65)

    lsr    a                     ;2  @76    split the 1,000 and 10,000 digit
    tax                          ;2  @78
    lda    ShiftedBcdTab,x       ;4  @82
    tax                          ;2  @84
    rol    a                     ;2  @86
    and    #$0F                  ;2  @88
    ora    #ASCII_0_OFFSET
    sta    decThousands          ;3  @91
    txa                          ;2  @93
    lsr    a                     ;2  @95
    lsr    a                     ;2  @97
    lsr    a                     ;2  @99
    ora    #ASCII_0_OFFSET
    sta    decTenThousands       ;3  @102

    lda    hexLow                ;3  @105
    cpy    temp                  ;3  @108
    bmi    _doSubtract           ;2³ @110/111
    beq    _useZero               ;2³ @112/113
    adc    #23 + 24              ;2  @114
_doSubtract
    sbc    #23                   ;2  @116
    sta    hexLow                ;3  @119
_useZero
    lda    hexHigh               ;3  @122
    sbc    #0                    ;2  @124

Start100s
    and    #$03                  ;2  @126
    tax                          ;2  @128   0,1,2,3
    cmp    #2                    ;2  @130
    rol    a                     ;2  @132   0,2,5,7
    ora    #ASCII_0_OFFSET
    tay                          ;2  @134   Y = Hundreds digit

    lda    hexLow                ;3  @137
    adc    Mod100Tab,x           ;4  @141    adding remainder of 256, 512, and 256+512 (all mod 100)
    bcs    hex_doSub200             ;2³ @143/144

hex_try200
    cmp    #200                  ;2  @145
    bcc    hex_try100               ;2³ @147/148
hex_doSub200
    iny                          ;2  @149
    iny                          ;2  @151
    sbc    #200                  ;2  @153
hex_try100
    cmp    #100                  ;2  @155
    bcc    HexToDec99            ;2³ @157/158
    iny                          ;2  @159
    sbc    #100                  ;2  @161

HexToDec99; SUBROUTINE
    lsr    a                     ;2  @163
    tax                          ;2  @165
    lda    ShiftedBcdTab,x       ;4  @169
    tax                          ;2  @171
    rol    a                     ;2  @173
    and    #$0F                  ;2  @175
    ora    #ASCII_0_OFFSET
    sta    decOnes               ;3  @178
    txa                          ;2  @180
    lsr    a                     ;2  @182
    lsr    a                     ;2  @184
    lsr    a                     ;2  @186
    ora    #ASCII_0_OFFSET

    ; irmen: load X with ones, and store Y and A too, for easy printing afterwards
    sty  decHundreds
    sta  decTens
    ldx  decOnes
    rts                          ;6  @192   Y=hundreds, A = tens digit, X=ones digit


HexToDec999; SUBROUTINE
    sty    hexLow                ;3  @9
    jmp    Start100s             ;3  @12

Mod100Tab
    .byte 0,56,12,56+12

ShiftedBcdTab
    .byte $00,$01,$02,$03,$04,$08,$09,$0A,$0B,$0C
    .byte $10,$11,$12,$13,$14,$18,$19,$1A,$1B,$1C
    .byte $20,$21,$22,$23,$24,$28,$29,$2A,$2B,$2C
    .byte $30,$31,$32,$33,$34,$38,$39,$3A,$3B,$3C
    .byte $40,$41,$42,$43,$44,$48,$49,$4A,$4B,$4C

decTenThousands   	.byte  0
decThousands    	.byte  0
decHundreds		.byte  0
decTens			.byte  0
decOnes   		.byte  0
			.byte  0		; zero-terminate the decimal output string

    }}
}


; ----- utility functions ----


asmsub  byte2decimal  (byte value @ A) -> ubyte @ Y, ubyte @ A, ubyte @ X  {
	; ---- A (signed byte) to decimal string in Y/A/X  (100s in Y, 10s in A, 1s in X)
	;      note: if the number is negative, you have to deal with the '-' yourself!
	%asm {{
		cmp  #0
		bpl  +
		eor  #255
		clc
		adc  #1
+		jmp  ubyte2decimal
	}}
}

asmsub  ubyte2hex  (ubyte value @ A) -> ubyte @ A, ubyte @ Y  {
	; ---- A to hex petscii string in AY (first hex char in A, second hex char in Y)
	%asm {{
		stx  P8ZP_SCRATCH_REG
		pha
		and  #$0f
		tax
		ldy  _hex_digits,x
		pla
		lsr  a
		lsr  a
		lsr  a
		lsr  a
		tax
		lda  _hex_digits,x
		ldx  P8ZP_SCRATCH_REG
		rts

_hex_digits	.text "0123456789abcdef"	; can probably be reused for other stuff as well
	}}
}

asmsub  uword2hex  (uword value @ AY) clobbers(A,Y)  {
	; ---- convert 16 bit uword in A/Y into 4-character hexadecimal string 'uword2hex.output' (0-terminated)
	%asm {{
		sta  P8ZP_SCRATCH_REG
		tya
		jsr  ubyte2hex
		sta  output
		sty  output+1
		lda  P8ZP_SCRATCH_REG
		jsr  ubyte2hex
		sta  output+2
		sty  output+3
		rts
output	.text  "0000", $00      ; 0-terminated output buffer (to make printing easier)
	}}
}

asmsub str2uword(str string @ AY) -> uword @ AY {
	; -- returns the unsigned word value of the string number argument in AY
	;    the number may NOT be preceded by a + sign and may NOT contain spaces
	;    (any non-digit character will terminate the number string that is parsed)
	%asm {{
_result = P8ZP_SCRATCH_W2
		sta  _mod+1
		sty  _mod+2
		ldy  #0
		sty  _result
		sty  _result+1
_mod		lda  $ffff,y		; modified
		sec
		sbc  #48
		bpl  +
_done		; return result
		lda  _result
		ldy  _result+1
		rts
+		cmp  #10
		bcs  _done
		; add digit to result
		pha
		jsr  _result_times_10
		pla
		clc
		adc  _result
		sta  _result
		bcc  +
		inc  _result+1
+		iny
		bne  _mod
		; never reached

_result_times_10     ; (W*4 + W)*2
		lda  _result+1
		sta  P8ZP_SCRATCH_REG
		lda  _result
		asl  a
		rol  P8ZP_SCRATCH_REG
		asl  a
		rol  P8ZP_SCRATCH_REG
		clc
		adc  _result
		sta  _result
		lda  P8ZP_SCRATCH_REG
		adc  _result+1
		asl  _result
		rol  a
		sta  _result+1
		rts
	}}
}

asmsub str2word(str string @ AY) -> word @ AY {
	; -- returns the signed word value of the string number argument in AY
	;    the number may be preceded by a + or - sign but may NOT contain spaces
	;    (any non-digit character will terminate the number string that is parsed)
	%asm {{
_result = P8ZP_SCRATCH_W2
		sta  P8ZP_SCRATCH_W1
		sty  P8ZP_SCRATCH_W1+1
		ldy  #0
		sty  _result
		sty  _result+1
		sty  _negative
		lda  (P8ZP_SCRATCH_W1),y
		cmp  #'+'
		bne  +
		iny
+		cmp  #'-'
		bne  _parse
		inc  _negative
		iny
_parse		lda  (P8ZP_SCRATCH_W1),y
		sec
		sbc  #48
		bpl  _digit
_done		; return result
		lda  _negative
		beq  +
		sec
		lda  #0
		sbc  _result
		sta  _result
		lda  #0
		sbc  _result+1
		sta  _result+1
+		lda  _result
		ldy  _result+1
		rts
_digit		cmp  #10
		bcs  _done
		; add digit to result
		pha
		jsr  str2uword._result_times_10
		pla
		clc
		adc  _result
		sta  _result
		bcc  +
		inc  _result+1
+		iny
		bne  _parse
		; never reached
_negative	.byte  0
	}}
}

}
