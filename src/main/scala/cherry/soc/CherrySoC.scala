package cherry.soc

import scala.collection.mutable.ArrayBuffer
import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb._
import spinal.lib.bus.misc.SizeMapping
import spinal.lib.bus.simple._
import spinal.lib.com.jtag.Jtag
import spinal.lib.com.uart._
import vexriscv.{VexRiscv, VexRiscvConfig, plugin}
import vexriscv.plugin._
import spinal.lib.io.TriStateArray

case class CherrySoC(config: CherryConfig) extends Component {
  val io = new Bundle {
    val asyncReset = in(Bool())
    val jtag = if (config.enableDebug) slave(Jtag()) else null

    val gpio = master(TriStateArray(config.gpioWidth bits))
    val uart = master(Uart())

    val panic = out(Bool())
  }

  val resetCtrl = new ClockingArea(
    ClockDomain(
      clock = clockDomain.readClockWire,
      config = ClockDomainConfig(resetKind = BOOT)
    )
  ) {
    val systemClkResetCounter = Reg(UInt(6 bits)) init (0)
    val mainClkResetUnbuffered = False

    when(systemClkResetCounter =/= U(systemClkResetCounter.range -> true)) {
      systemClkResetCounter := systemClkResetCounter + 1
      mainClkResetUnbuffered := True
    }

    when(BufferCC(io.asyncReset)) {
      systemClkResetCounter := 0
    }

    val mainClkReset = RegNext(mainClkResetUnbuffered)
    val systemReset = RegNext(mainClkResetUnbuffered)
  }

  val coreCtrl = new ClockingArea(
    ClockDomain(
      clock = clockDomain.readClockWire,
      reset = resetCtrl.systemReset,
      frequency = FixedFrequency(config.coreFrequency)
    )
  ) {
    if (config.enableDebug) {
      val debugClockDomain = ClockDomain(
        clock = clockDomain.readClockWire,
        reset = resetCtrl.mainClkReset,
        frequency = FixedFrequency(config.coreFrequency)
      )

      val debugPlugin = new DebugPlugin(debugClockDomain)
      debugPlugin.debugClockDomain {
        resetCtrl.systemReset setWhen (RegNext(debugPlugin.io.resetOut))
        io.jtag <> debugPlugin.io.bus.fromJtag()
      }

      config.plugins += debugPlugin
    }

    val cpu = new VexRiscv(VexRiscvConfig(config.plugins))
    val mainBusArbiter = new MainBusArbiter(config.pipelinedMemoryBusConfig)

    val externalInterrupt = False

    for (plugin <- cpu.plugins) plugin match {
      case plugin: IBusSimplePlugin =>
        mainBusArbiter.io.iBus.cmd <> plugin.iBus.cmd
        mainBusArbiter.io.iBus.rsp <> plugin.iBus.rsp
      case plugin: DBusSimplePlugin => {
        mainBusArbiter.io.dBus <> plugin.dBus
      }
      case plugin: CsrPlugin => {
        plugin.externalInterrupt := externalInterrupt
        plugin.timerInterrupt := False
      }
      case _ =>
    }

    val ram = new Ram(
      onChipRamSize = config.onChipRamSize,
      onChipRamHexFile = config.onChipRamHexFile,
      memoryBusConfig = config.pipelinedMemoryBusConfig
    )

    val apbBridge = new PipelinedMemoryBusToApbBridge(
      apb3Config = Apb3Config(addressWidth = 20, dataWidth = 32),
      pipelinedMemoryBusConfig = config.pipelinedMemoryBusConfig,
      pipelineBridge = true
    )

    MemoryBusDecoder(
      pipelineMaster = true,
      master = mainBusArbiter.io.masterBus,
      specification = ArrayBuffer[(PipelinedMemoryBus, SizeMapping)](
        ram.io.bus -> (0x80000000L, config.onChipRamSize),
        apbBridge.io.pipelinedMemoryBus -> (0xf0000000L, 1 MB)
      )
    )

    val sysCtrl = new SystemCtrl()
    io.panic := sysCtrl.io.panic

    val gpioCtrl = Apb3Gpio(
      gpioWidth = config.gpioWidth,
      withReadSync = true
    )
    io.gpio <> gpioCtrl.io.gpio

    val uartCtrl = Apb3UartCtrl(config.uartConfig)
    uartCtrl.io.uart <> io.uart
    externalInterrupt setWhen (uartCtrl.io.interrupt)

    val timerCtrl = new TimerCtrl()
    externalInterrupt setWhen (timerCtrl.io.interrupt)

    Apb3Decoder(
      apbBridge.io.apb,
      ArrayBuffer[(Apb3, SizeMapping)](
        sysCtrl.io.apb -> (0x00000, 4 kB),
        gpioCtrl.io.apb -> (0x10000, 4 kB),
        uartCtrl.io.apb -> (0x20000, 4 kB),
        timerCtrl.io.apb -> (0x30000, 4 kB)
      )
    )
  }
}
