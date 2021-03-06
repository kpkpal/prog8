; Prog8 definitions for the CommanderX16
; Including memory registers, I/O registers, Basic and Kernal subroutines.
;
; Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
;
; indent format: TABS, size=8

%target cx16


c64 {

; ---- kernal routines, these are the same as on the Commodore-64 (hence the same block name) ----

; STROUT --> use screen.print
; CLEARSCR -> use screen.clear_screen
; HOMECRSR -> use screen.plot

romsub $FF81 = CINT() clobbers(A,X,Y)                           ; (alias: SCINIT) initialize screen editor and video chip
romsub $FF84 = IOINIT() clobbers(A, X)                          ; initialize I/O devices (CIA, SID, IRQ)
romsub $FF87 = RAMTAS() clobbers(A,X,Y)                         ; initialize RAM, tape buffer, screen
romsub $FF8A = RESTOR() clobbers(A,X,Y)                         ; restore default I/O vectors
romsub $FF8D = VECTOR(uword userptr @ XY, ubyte dir @ Pc) clobbers(A,Y)     ; read/set I/O vector table
romsub $FF90 = SETMSG(ubyte value @ A)                          ; set Kernal message control flag
romsub $FF93 = SECOND(ubyte address @ A) clobbers(A)            ; (alias: LSTNSA) send secondary address after LISTEN
romsub $FF96 = TKSA(ubyte address @ A) clobbers(A)              ; (alias: TALKSA) send secondary address after TALK
romsub $FF99 = MEMTOP(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set top of memory  pointer
romsub $FF9C = MEMBOT(uword address @ XY, ubyte dir @ Pc) -> uword @ XY     ; read/set bottom of memory  pointer
romsub $FF9F = SCNKEY() clobbers(A,X,Y)                         ; scan the keyboard
romsub $FFA2 = SETTMO(ubyte timeout @ A)                        ; set time-out flag for IEEE bus
romsub $FFA5 = ACPTR() -> ubyte @ A                             ; (alias: IECIN) input byte from serial bus
romsub $FFA8 = CIOUT(ubyte databyte @ A)                        ; (alias: IECOUT) output byte to serial bus
romsub $FFAB = UNTLK() clobbers(A)                              ; command serial bus device to UNTALK
romsub $FFAE = UNLSN() clobbers(A)                              ; command serial bus device to UNLISTEN
romsub $FFB1 = LISTEN(ubyte device @ A) clobbers(A)             ; command serial bus device to LISTEN
romsub $FFB4 = TALK(ubyte device @ A) clobbers(A)               ; command serial bus device to TALK
romsub $FFB7 = READST() -> ubyte @ A                            ; read I/O status word
romsub $FFBA = SETLFS(ubyte logical @ A, ubyte device @ X, ubyte address @ Y)   ; set logical file parameters
romsub $FFBD = SETNAM(ubyte namelen @ A, str filename @ XY)     ; set filename parameters
romsub $FFC0 = OPEN() clobbers(A,X,Y)                           ; (via 794 ($31A)) open a logical file
romsub $FFC3 = CLOSE(ubyte logical @ A) clobbers(A,X,Y)         ; (via 796 ($31C)) close a logical file
romsub $FFC6 = CHKIN(ubyte logical @ X) clobbers(A,X)           ; (via 798 ($31E)) define an input channel
romsub $FFC9 = CHKOUT(ubyte logical @ X) clobbers(A,X)          ; (via 800 ($320)) define an output channel
romsub $FFCC = CLRCHN() clobbers(A,X)                           ; (via 802 ($322)) restore default devices
romsub $FFCF = CHRIN() clobbers(Y) -> ubyte @ A                 ; (via 804 ($324)) input a character (for keyboard, read a whole line from the screen) A=byte read.
romsub $FFD2 = CHROUT(ubyte char @ A)                           ; (via 806 ($326)) output a character
romsub $FFD5 = LOAD(ubyte verify @ A, uword address @ XY) -> ubyte @Pc, ubyte @ A, ubyte @ X, ubyte @ Y     ; (via 816 ($330)) load from device
romsub $FFD8 = SAVE(ubyte zp_startaddr @ A, uword endaddr @ XY) -> ubyte @ Pc, ubyte @ A                    ; (via 818 ($332)) save to a device
romsub $FFDB = SETTIM(ubyte low @ A, ubyte middle @ X, ubyte high @ Y)      ; set the software clock
romsub $FFDE = RDTIM() -> ubyte @ A, ubyte @ X, ubyte @ Y       ; read the software clock
romsub $FFE1 = STOP() clobbers(A,X) -> ubyte @ Pz, ubyte @ Pc   ; (via 808 ($328)) check the STOP key
romsub $FFE4 = GETIN() clobbers(X,Y) -> ubyte @ A               ; (via 810 ($32A)) get a character
romsub $FFE7 = CLALL() clobbers(A,X)                            ; (via 812 ($32C)) close all files
romsub $FFEA = UDTIM() clobbers(A,X)                            ; update the software clock
romsub $FFED = SCREEN() -> ubyte @ X, ubyte @ Y                 ; read number of screen rows and columns
romsub $FFF0 = PLOT(ubyte col @ Y, ubyte row @ X, ubyte dir @ Pc) -> ubyte @ X, ubyte @ Y       ; read/set position of cursor on screen.  Use screen.plot for a 'safe' wrapper that preserves X.
romsub $FFF3 = IOBASE() -> uword @ XY                           ; read base address of I/O devices

}

cx16 {

        ; 65c02 hardware vectors:
        &uword  NMI_VEC         = $FFFA     ; 6502 nmi vector, determined by the kernal if banked in
        &uword  RESET_VEC       = $FFFC     ; 6502 reset vector, determined by the kernal if banked in
        &uword  IRQ_VEC         = $FFFE     ; 6502 interrupt vector, determined by the kernal if banked in


; the sixteen virtual 16-bit registers
    &uword r0  = $02
    &uword r1  = $04
    &uword r2  = $06
    &uword r3  = $08
    &uword r4  = $0a
    &uword r5  = $0c
    &uword r6  = $0e
    &uword r7  = $10
    &uword r8  = $12
    &uword r9  = $14
    &uword r10 = $16
    &uword r11 = $18
    &uword r12 = $1a
    &uword r13 = $1c
    &uword r14 = $1e
    &uword r15 = $20

; VERA registers

    const uword VERA_BASE = $9F20
    &ubyte  VERA_ADDR_L   	  = VERA_BASE + $00
    &ubyte  VERA_ADDR_M   	  = VERA_BASE + $01
    &ubyte  VERA_ADDR_H   	  = VERA_BASE + $02
    &ubyte  VERA_DATA0        = VERA_BASE + $03
    &ubyte  VERA_DATA1        = VERA_BASE + $04
    &ubyte  VERA_CTRL         = VERA_BASE + $05
    &ubyte  VERA_IEN          = VERA_BASE + $06
    &ubyte  VERA_ISR          = VERA_BASE + $07
    &ubyte  VERA_IRQ_LINE_L   = VERA_BASE + $08
    &ubyte  VERA_DC_VIDEO     = VERA_BASE + $09
    &ubyte  VERA_DC_HSCALE    = VERA_BASE + $0A
    &ubyte  VERA_DC_VSCALE    = VERA_BASE + $0B
    &ubyte  VERA_DC_BORDER    = VERA_BASE + $0C
    &ubyte  VERA_DC_HSTART    = VERA_BASE + $09
    &ubyte  VERA_DC_HSTOP     = VERA_BASE + $0A
    &ubyte  VERA_DC_VSTART    = VERA_BASE + $0B
    &ubyte  VERA_DC_VSTOP     = VERA_BASE + $0C
    &ubyte  VERA_L0_CONFIG    = VERA_BASE + $0D
    &ubyte  VERA_L0_MAPBASE   = VERA_BASE + $0E
    &ubyte  VERA_L0_TILEBASE  = VERA_BASE + $0F
    &ubyte  VERA_L0_HSCROLL_L = VERA_BASE + $10
    &ubyte  VERA_L0_HSCROLL_H = VERA_BASE + $11
    &ubyte  VERA_L0_VSCROLL_L = VERA_BASE + $12
    &ubyte  VERA_L0_VSCROLL_H = VERA_BASE + $13
    &ubyte  VERA_L1_CONFIG    = VERA_BASE + $14
    &ubyte  VERA_L1_MAPBASE   = VERA_BASE + $15
    &ubyte  VERA_L1_TILEBASE  = VERA_BASE + $16
    &ubyte  VERA_L1_HSCROLL_L = VERA_BASE + $17
    &ubyte  VERA_L1_HSCROLL_H = VERA_BASE + $18
    &ubyte  VERA_L1_VSCROLL_L = VERA_BASE + $19
    &ubyte  VERA_L1_VSCROLL_H = VERA_BASE + $1A
    &ubyte  VERA_AUDIO_CTRL   = VERA_BASE + $1B
    &ubyte  VERA_AUDIO_RATE   = VERA_BASE + $1C
    &ubyte  VERA_AUDIO_DATA   = VERA_BASE + $1D
    &ubyte  VERA_SPI_DATA     = VERA_BASE + $1E
    &ubyte  VERA_SPI_CTRL     = VERA_BASE + $1F
; VERA_PSG_BASE     = $1F9C0
; VERA_PALETTE_BASE = $1FA00
; VERA_SPRITES_BASE = $1FC00

; I/O

    const uword  via1  = $9f60                  ;VIA 6522 #1
    &ubyte  d1prb	= via1+0
    &ubyte  d1pra	= via1+1
    &ubyte  d1ddrb	= via1+2
    &ubyte  d1ddra	= via1+3
    &ubyte  d1t1l	= via1+4
    &ubyte  d1t1h	= via1+5
    &ubyte  d1t1ll	= via1+6
    &ubyte  d1t1lh	= via1+7
    &ubyte  d1t2l	= via1+8
    &ubyte  d1t2h	= via1+9
    &ubyte  d1sr	= via1+10
    &ubyte  d1acr	= via1+11
    &ubyte  d1pcr	= via1+12
    &ubyte  d1ifr	= via1+13
    &ubyte  d1ier	= via1+14
    &ubyte  d1ora	= via1+15

    const uword  via2  = $9f70                  ;VIA 6522 #2
    &ubyte  d2prb	=via2+0
    &ubyte  d2pra	=via2+1
    &ubyte  d2ddrb	=via2+2
    &ubyte  d2ddra	=via2+3
    &ubyte  d2t1l	=via2+4
    &ubyte  d2t1h	=via2+5
    &ubyte  d2t1ll	=via2+6
    &ubyte  d2t1lh	=via2+7
    &ubyte  d2t2l	=via2+8
    &ubyte  d2t2h	=via2+9
    &ubyte  d2sr	=via2+10
    &ubyte  d2acr	=via2+11
    &ubyte  d2pcr	=via2+12
    &ubyte  d2ifr	=via2+13
    &ubyte  d2ier	=via2+14
    &ubyte  d2ora	=via2+15


; ---- Commander X-16 additions on top of C64 kernal routines ----
; spelling of the names is taken from the Commander X-16 rom sources

; supported C128 additions
romsub $ff4a = close_all()
romsub $ff59 = lkupla()
romsub $ff5c = lkupsa()
romsub $ff5f = screen_set_mode(ubyte mode @A) clobbers(A, X, Y) -> ubyte @Pc
romsub $ff62 = screen_set_charset(ubyte charset @A, uword charsetptr @XY) clobbers(A,X,Y)      ; incompatible with C128  dlchr()
romsub $ff65 = pfkey()
romsub $ff6e = jsrfar()
romsub $ff74 = fetch()
romsub $ff77 = stash()
romsub $ff7a = cmpare()
romsub $ff7d = primm()

; X16 additions
romsub $ff44 = macptr()
romsub $ff47 = enter_basic(ubyte cold_or_warm @Pc)
romsub $ff68 = mouse_config(ubyte shape @A, ubyte scale @X) clobbers (A, X, Y)
romsub $ff6b = mouse_get(ubyte zpdataptr @X) clobbers(A)
romsub $ff71 = mouse_scan() clobbers(A, X, Y)
romsub $ff53 = joystick_scan() clobbers(A, X, Y)
romsub $ff56 = joystick_get(ubyte joynr @A) -> ubyte @A, ubyte @X, ubyte @Y
romsub $ff4d = clock_set_date_time() clobbers(A, X, Y)      ; args: r0, r1, r2, r3L
romsub $ff50 = clock_get_date_time() clobbers(A)            ; outout args: r0, r1, r2, r3L

; high level graphics & fonts
; TODO specify the correct clobbers for GRAPH and FB functions
romsub $ff20 = GRAPH_init()  clobbers(A,X,Y)           ; uses vectors=r0
romsub $ff23 = GRAPH_clear()  clobbers(A,X,Y)
romsub $ff26 = GRAPH_set_window()       ; uses x=r0, y=r1, width=r2, height=r3
romsub $ff29 = GRAPH_set_colors(ubyte stroke @A, ubyte fill @X, ubyte background @Y) clobbers (A,X,Y)
romsub $ff2c = GRAPH_draw_line()  clobbers(A,X,Y)       ; uses x1=r0, y1=r1, x2=r2, y2=r3
romsub $ff2f = GRAPH_draw_rect(ubyte fill @Pc) clobbers(A,X,Y)   ; uses x=r0, y=r1, width=r2, height=r3, cornerradius=r4
romsub $ff32 = GRAPH_move_rect()        ; uses sx=r0, sy=r1, tx=r2, ty=r3, width=r4, height=r5
romsub $ff35 = GRAPH_draw_oval(ubyte fill @Pc)        ; uses x=r0, y=r1, width=r2, height=r3
romsub $ff38 = GRAPH_draw_image()       ; uses x=r0, y=r1, ptr=r2, width=r3, height=r4
romsub $ff3b = GRAPH_set_font()         ; uses ptr=r0
romsub $ff3e = GRAPH_get_char_size(ubyte baseline @A, ubyte width @X, ubyte height_or_style @Y, ubyte is_control @Pc)
romsub $ff41 = GRAPH_put_char(ubyte char @A) clobbers(A,X,Y)   ; uses x=r0, y=r1

; framebuffer
romsub $fef6 = FB_init()
romsub $fef9 = FB_get_info() -> byte @A    ; also outputs width=r0, height=r1
romsub $fefc = FB_set_palette(ubyte index @A, ubyte bytecount @X)      ; also uses pointer=r0
romsub $feff = FB_cursor_position()    ; uses x=r0, y=r1
romsub $ff02 = FB_cursor_next_line()   ; uses x=r0
romsub $ff05 = FB_get_pixel() -> ubyte @A
romsub $ff08 = FB_get_pixels()         ; uses ptr=r0, count=r1
romsub $ff0b = FB_set_pixel(ubyte color @A)
romsub $ff0e = FB_set_pixels()         ; uses ptr=r0, count=r1
romsub $ff11 = FB_set_8_pixels(ubyte pattern @A, ubyte color @X)
romsub $ff14 = FB_set_8_pixels_opaque(ubyte pattern @A, ubyte color1 @X, ubyte color2 @Y)  ; also uses mask=r0L
romsub $ff17 = FB_fill_pixels(ubyte color @A)   ; also uses count=r0, step=r1
romsub $ff1a = FB_filter_pixels()      ; uses ptr=r0, count=r1
romsub $ff1d = FB_move_pixels()        ; uses sx=r0, sy=r1, tx=r2, ty=r3, count=r4

; misc
romsub $fef0 = sprite_set_image(ubyte number @A, ubyte width @X, ubyte height @Y, ubyte apply_mask @Pc) -> ubyte @Pc  ; also uses pixels=r0, mask=r1, bpp=r2L
romsub $fef3 = sprite_set_position(ubyte number @A)  ; also uses x=r0 and y=r1
romsub $fee4 = memory_fill(ubyte value @A)      ; uses address=r0, num_bytes=r1
romsub $fee7 = memory_copy()                    ; uses source=r0, target=r1, num_bytes=r2
romsub $feea = memory_crc()                     ; uses address=r0, num_bytes=r1     result->r2
romsub $feed = memory_decompress()              ; uses input=r0, output=r1     result->r1
romsub $fedb = console_init()           ; uses x=r0, y=r1, width=r2, height=r3
romsub $fede = console_put_char(ubyte char @A, ubyte wrapping @Pc)
romsub $fee1 = console_get_char() -> ubyte @A
romsub $fed8 = console_put_image()      ; uses ptr=r0, width=r1, height=r2
romsub $fed5 = console_set_paging_message()     ; uses messageptr=r0
romsub $fed2 = kbdbuf_put(ubyte key @A)
romsub $fecf = entropy_get() -> ubyte @A, ubyte @X, ubyte @Y
romsub $fecc = monitor()



; ---- end of kernal routines ----

asmsub init_system()  {
    ; Initializes the machine to a sane starting state.
    ; Called automatically by the loader program logic.
    %asm {{
        sei
        cld
        ;stz  $00
        ;stz  $01
        ;stz  d1prb      ; select rom bank 0
        lda  #$80
        sta  VERA_CTRL
        jsr  c64.IOINIT
        jsr  c64.RESTOR
        jsr  c64.CINT
        lda  #0
        tax
        tay
        clc
        clv
        cli
        rts
    }}
}

}
