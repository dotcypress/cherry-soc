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

case class SnapOff() extends Bundle {
  val pin4 = in(Bool())
  val pin9 = in(Bool())
  val pin10 = in(Bool())
  val pin1 = out(Bool())
  val pin2 = out(Bool())
  val pin3 = out(Bool())
  val pin7 = out(Bool())
  val pin8 = out(Bool())
}

case class Bootstrap() extends Component {
  val io = new Bundle {
    val pmod2 = SnapOff()
    val uart = master(Uart())
    val reset = in(Bool())
    val ledRed = out(Bool())
  }

  var config = CherryConfig.withRamFile("src/main/resources/cherry-app.hex")
  val cherry = CherrySoC(config)

  cherry.io.asyncReset <> io.reset
  cherry.io.uart <> io.uart

  io.ledRed <> ~cherry.io.panic

  val gpio = cherry.io.gpio

  gpio.write(0) <> io.pmod2.pin7
  gpio.write(1) <> io.pmod2.pin1
  gpio.write(2) <> io.pmod2.pin2
  gpio.write(3) <> io.pmod2.pin8
  gpio.write(4) <> io.pmod2.pin3

  gpio.read := 0
  io.pmod2.pin9 <> gpio.read(31)
  io.pmod2.pin4 <> gpio.read(30)
  io.pmod2.pin10 <> gpio.read(29)
}
