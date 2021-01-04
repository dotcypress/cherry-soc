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
    val jtag = slave(Jtag())

    val gpio = master(TriStateArray(config.gpioWidth bits))
    val uart = master(Uart())
    val panic = out(Bool())
  }

  val resetCtrlClockDomain = ClockDomain(
    clock = clockDomain.readClockWire,
    config = ClockDomainConfig(resetKind = BOOT)
  )

  val resetCtrl = new ClockingArea(resetCtrlClockDomain) {
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

  val systemClockDomain = ClockDomain(
    clock = clockDomain.readClockWire,
    reset = resetCtrl.systemReset,
    frequency = FixedFrequency(config.coreFrequency)
  )

  new ClockingArea(systemClockDomain) {
    val pipelinedMemoryBusConfig = PipelinedMemoryBusConfig(
      addressWidth = 32,
      dataWidth = 32
    )

    val plugins = ArrayBuffer(
      new IBusSimplePlugin(
        resetVector = 0x80000000L,
        cmdForkOnSecondStage = true,
        cmdForkPersistence = false
      ),
      new DBusSimplePlugin(),
      new CsrPlugin(CsrPluginConfig.smallest(mtvecInit = 0x80000020L)),
      new DecoderSimplePlugin(catchIllegalInstruction = false),
      new RegFilePlugin(regFileReadyKind = plugin.SYNC),
      new IntAluPlugin,
      new SrcPlugin(),
      new LightShifterPlugin,
      new HazardSimplePlugin(
        bypassExecute = true,
        bypassMemory = true,
        bypassWriteBack = true,
        bypassWriteBackBuffer = true
      ),
      new BranchPlugin(earlyBranch = true),
      new YamlPlugin("cpu0.yaml")
    )

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

      plugins += debugPlugin
    }

    val cpu = new VexRiscv(VexRiscvConfig(plugins))
    val mainBusArbiter = new MainBusArbiter(pipelinedMemoryBusConfig)

    val timerInterrupt = False
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
        plugin.timerInterrupt := timerInterrupt
      }
      case _ =>
    }

    val ram = new Ram(
      onChipRamSize = config.onChipRamSize,
      onChipRamHexFile = config.onChipRamHexFile,
      memoryBusConfig = pipelinedMemoryBusConfig
    )

    val apbBridge = new PipelinedMemoryBusToApbBridge(
      apb3Config = Apb3Config(addressWidth = 20, dataWidth = 32),
      pipelinedMemoryBusConfig = pipelinedMemoryBusConfig,
      pipelineBridge = true
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
    timerInterrupt setWhen (timerCtrl.io.interrupt)

    Apb3Decoder(
      apbBridge.io.apb,
      ArrayBuffer[(Apb3, SizeMapping)](
        sysCtrl.io.apb -> (0x00000, 4 kB),
        gpioCtrl.io.apb -> (0x10000, 4 kB),
        uartCtrl.io.apb -> (0x20000, 4 kB),
        timerCtrl.io.apb -> (0x30000, 4 kB)
      )
    )

    MemoryBusDecoder(
      pipelineMaster = true,
      master = mainBusArbiter.io.masterBus,
      specification = ArrayBuffer[(PipelinedMemoryBus, SizeMapping)](
        ram.io.bus -> (0x80000000L, config.onChipRamSize),
        apbBridge.io.pipelinedMemoryBus -> (0xf0000000L, 1 MB)
      )
    )
  }
}
