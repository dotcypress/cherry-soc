package cherry.soc

import scala.collection.mutable.ArrayBuffer
import spinal.core._
import spinal.lib._
import spinal.lib.com.uart._
import vexriscv.{VexRiscv, plugin}
import vexriscv.plugin._
import spinal.lib.bus.simple._

case class CherryConfig(
    onChipRamHexFile: String,
    coreFrequency: HertzNumber,
    onChipRamSize: BigInt,
    gpioWidth: Int,
    enableDebug: Boolean,
    pipelinedMemoryBusConfig: PipelinedMemoryBusConfig,
    uartConfig: UartCtrlMemoryMappedConfig,
    plugins: ArrayBuffer[Plugin[VexRiscv]]
)

object CherryConfig {
  def withRamFile(onChipRamHexFile: String) =
    CherryConfig
      .default()
      .copy(
        onChipRamHexFile = onChipRamHexFile
      )

  def default() =
    CherryConfig(
      onChipRamHexFile = null,
      coreFrequency = 12 MHz,
      onChipRamSize = 8 kB,
      gpioWidth = 32,
      enableDebug = false,
      pipelinedMemoryBusConfig = PipelinedMemoryBusConfig(
        addressWidth = 32,
        dataWidth = 32
      ),
      plugins = ArrayBuffer(
        new IBusSimplePlugin(
          resetVector = 0x80000000L,
          cmdForkOnSecondStage = true,
          cmdForkPersistence = false
        ),
        new DBusSimplePlugin(),
        new CsrPlugin(
          CsrPluginConfig(
            mvendorid = null,
            marchid = null,
            mimpid = null,
            mhartid = 0,
            mtvecInit = 0x80000020L,
            misaExtensionsInit = 66,
            ecallGen = true,
            wfiGenAsWait = true,
            mscratchGen = false,
            catchIllegalAccess = true,
            misaAccess = CsrAccess.READ_WRITE,
            mtvecAccess = CsrAccess.READ_WRITE,
            mepcAccess = CsrAccess.READ_WRITE,
            minstretAccess = CsrAccess.READ_WRITE,
            mcycleAccess = CsrAccess.READ_ONLY,
            mcauseAccess = CsrAccess.READ_ONLY,
            mbadaddrAccess = CsrAccess.READ_ONLY,
            ucycleAccess = CsrAccess.READ_ONLY,
            uinstretAccess = CsrAccess.READ_ONLY
          )
        ),
        new DecoderSimplePlugin(),
        new RegFilePlugin(
          regFileReadyKind = plugin.SYNC
        ),
        new IntAluPlugin,
        new SrcPlugin(
          separatedAddSub = false,
          executeInsertion = true
        ),
        new LightShifterPlugin,
        new HazardSimplePlugin(
          bypassExecute = true,
          bypassMemory = true,
          bypassWriteBack = true,
          bypassWriteBackBuffer = true
        ),
        new BranchPlugin(earlyBranch = true),
        new YamlPlugin("cpu0.yaml")
      ),
      uartConfig = UartCtrlMemoryMappedConfig(
        uartCtrlConfig = UartCtrlGenerics(
          dataWidthMax = 8,
          clockDividerWidth = 20,
          preSamplingSize = 1,
          samplingSize = 3,
          postSamplingSize = 1
        ),
        initConfig = UartCtrlInitConfig(
          baudrate = 115200,
          dataLength = 7,
          parity = UartParityType.NONE,
          stop = UartStopType.ONE
        ),
        txFifoDepth = 16,
        rxFifoDepth = 16
      )
    )
}
