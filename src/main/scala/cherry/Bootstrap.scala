package cherry

import java.nio.file._
import spinal.core._
import spinal.lib._
import spinal.lib.com.uart._
import cherry.soc._

object Bootstrap {
  def main(args: Array[String]) {
    val targetDirectory = Paths.get("target/bitstream")
    if (!Files.exists(targetDirectory)) {
      Files.createDirectory(targetDirectory)
    }
    new SpinalConfig(
      defaultClockDomainFrequency = FixedFrequency(12 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(
        resetKind = ASYNC,
        resetActiveLevel = LOW
      ),
      targetDirectory = targetDirectory.toString()
    ).generateVerilog(new Bootstrap)
  }
}

case class Bootstrap() extends Component {
  val io = new Bundle {
    val uart = master(Uart())
    val button = in(Bool())
    val ledGreen = out(Bool())
    val ledRed = out(Bool())
  }

  val cherry = CherryCore(
    CherryConfig.default("src/main/resources/cherry-app.hex")
  )

  cherry.io.jtag.tck := False
  cherry.io.jtag.tms := False
  cherry.io.jtag.tdi := False

  cherry.io.asyncReset := io.button
  cherry.io.uart <> io.uart

  val gpio = cherry.io.gpio.write
  io.ledRed := gpio(1)
  io.ledGreen := ~gpio(1)
  cherry.io.gpio.read := 0
}
