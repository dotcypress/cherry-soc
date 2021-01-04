package cherry.soc

import spinal.core._
import spinal.lib._
import spinal.lib.com.uart._

case class CherryConfig(
    onChipRamHexFile: String,
    coreFrequency: HertzNumber,
    onChipRamSize: BigInt,
    gpioWidth: Int,
    enableDebug: Boolean,
    uartConfig: UartCtrlMemoryMappedConfig
)

object CherryConfig {
  def default(onChipRamHexFile: String) =
    CherryConfig(
      onChipRamHexFile = onChipRamHexFile,
      coreFrequency = 12 MHz,
      onChipRamSize = 8 kB,
      gpioWidth = 32,
      enableDebug = false,
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