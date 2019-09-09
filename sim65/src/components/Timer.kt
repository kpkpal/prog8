package sim65.components

/**
 * A programmable timer. Causes an IRQ or NMI at specified 24-bits intervals.
 * byte   value
 *  00    control register  bit 0=enable  bit 1=nmi (instead of irq)
 *  01    24 bits interval value, bits 0-7 (lo)
 *  02    24 bits interval value, bits 8-15 (mid)
 *  03    24 bits interval value, bits 16-23  (hi)
 */
class Timer(startAddress: Address, endAddress: Address) : MemMappedComponent(startAddress, endAddress) {
    private var counter: Int = 0
    private var interval: Int = 0
    private var enabled = false
    private var nmi = false

    init {
        require(endAddress - startAddress + 1 == 4) { "timer needs exactly 4 memory bytes" }
    }

    override fun clock() {
        if (enabled && interval > 0) {
            counter++
            if (counter == interval) {
                if (nmi)
                    println("TODO: timer causes CPU nmi $counter") // TODO
                else
                    println("TODO: timer causes CPU irq $counter")  // TODO
                counter = 0
            }
        }
    }

    override fun reset() {
        counter = 0
        interval = 0
        enabled = false
        nmi = false
    }

    override operator fun get(address: Address): UByte {
        when (address - startAddress) {
            0 -> {
                var data = 0
                if (enabled) data = data or 0b00000001
                if (nmi) data = data or 0b00000010
                return data.toShort()
            }
            1 -> {
                return (counter and 0xff).toShort()
            }
            2 -> {
                return ((counter ushr 8) and 0xff).toShort()
            }
            3 -> {
                return ((counter ushr 16) and 0xff).toShort()
            }
            else -> return 0
        }
    }

    override operator fun set(address: Address, data: UByte) {
        when (address - startAddress) {
            0 -> {
                val i = data.toInt()
                val newEnabled = (i and 0b00000001) != 0
                nmi = (i and 0b00000010) != 0
                if(newEnabled && !enabled) {
                    // timer is set to enabled (was disabled) - reset the counter
                    counter = 0
                }
                enabled = newEnabled
            }
            1 -> {
                interval = (interval and 0x7fffff00) or data.toInt()
            }
            2 -> {
                interval = (interval and 0x7fff00ff) or (data.toInt() shl 8)
            }
            3 -> {
                interval = (interval and 0x7f00ffff) or (data.toInt() shl 16)
            }
        }
    }
}