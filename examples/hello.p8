%import c64lib
%import c64textio
%import c64flt
%zeropage basicsafe


main {

    sub start() {

        ; set text color and activate lowercase charset
        txt.color(13)
        c64.VMCSB |= 2

        ; use optimized routine to write text
        txt.print("Hello!\n")

        ; use iteration to write text
        str question = "How are you?\n"
        ubyte char
        for char in question
            c64.CHROUT(char)

        ; use indexed loop to write characters
        str bye = "Goodbye!\n"
        for char in 0 to len(bye)-1
            c64.CHROUT(bye[char])


        float clock_seconds = ((mkword(c64.TIME_MID, c64.TIME_LO) as float) + (c64.TIME_HI as float)*65536.0) / 60
        float hours = floor(clock_seconds / 3600)
        clock_seconds -= hours*3600
        float minutes = floor(clock_seconds / 60)
        clock_seconds = floor(clock_seconds - minutes * 60.0)

        txt.print("system time in ti$ is ")
        c64flt.print_f(hours)
        c64.CHROUT(':')
        c64flt.print_f(minutes)
        c64.CHROUT(':')
        c64flt.print_f(clock_seconds)
        c64.CHROUT('\n')

        txt.print("bye!\n")
    }
}
