; CommanderX16 simple graphics example!
; make sure to compile with the cx16 compiler target.

%target cx16
%zeropage basicsafe

main {

    sub start() {

        void cx16.screen_set_mode($80)
        cx16.r0=0

        cx16.FB_init()
        cx16.r0 = 0
        cx16.r1 = 0
        cx16.FB_cursor_position()
        uword xx
        ubyte yy
        for yy in 199 downto 0 {
            for xx in 319 downto 0 {
                cx16.FB_set_pixel( yy+lsb(xx))
            }
        }

        ; cx16.GRAPH_init()
        for xx in 0 to 319 step 32 {
            ubyte q
            for q in 0 to 31 {
                cx16.GRAPH_set_colors(q, 2, 0)
                cx16.r0 = xx+q
                cx16.r1=0
                cx16.r2=rnd()
                cx16.r3=199
                cx16.GRAPH_draw_line()
            }
        }
    }
}

